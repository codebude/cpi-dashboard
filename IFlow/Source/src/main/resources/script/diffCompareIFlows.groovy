//internal
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.*
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import com.sap.it.api.ITApi;
import com.sap.it.api.ITApiFactory;
import com.sap.it.api.securestore.*;

//external
import com.github.difflib.patch.Patch
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

Message processData(Message message) {	
	def compObj = new JsonSlurper().parseText(message.getProperty("incomingPayload"))
	
	//Add credential names	
	def remotes = "${message.getProperty("remoteTenants")}".split(';')
	if (compObj.iFlow1.hostname == message.getProperty("ownHostname")){
		compObj.iFlow1.credentialName = message.getProperty("ownCredentials")
	} else {
		remotes.each{ remote ->
			def pts = remote.split('\\|')
			if (pts.size() == 2 && pts[0]?.trim() && pts[1]?.trim()){
				if (compObj.iFlow1.hostname == pts[0].trim()){
					compObj.iFlow1.credentialName = pts[1].trim()
				}
			}
		}
	}	
	if (compObj.iFlow2.hostname == message.getProperty("ownHostname")){
		compObj.iFlow2.credentialName = message.getProperty("ownCredentials")
	} else {
		remotes.each{ remote ->
			def pts = remote.split('\\|')
			if (pts.size() == 2 && pts[0]?.trim() && pts[1]?.trim()){
				if (compObj.iFlow2.hostname == pts[0].trim()){
					compObj.iFlow2.credentialName = pts[1].trim()
				}
			}
		}
	}	
	
	def result = buildIflowDiff(compObj.iFlow1, compObj.iFlow2)
	
    def body = JsonOutput.toJson([count:result.fileList.size(),result:result.fileList,totalUnifiedDiff:result.totalUnifiedDiff])
    message.setBody(body)
    message.setHeader('Content-Type', 'application/json')
    return message
}

private buildIflowDiff(def iFlow1, def iFlow2){
	//Setup credentials
	iFlow1.credential = getUserCreds(iFlow1.credentialName)
	iFlow2.credential = getUserCreds(iFlow2.credentialName)
	
	//Download IFlow objects
	iFlow1.bytes = getIFlowBytes(iFlow1)
	iFlow2.bytes = getIFlowBytes(iFlow2)
	
	def mergedFileList = buildFileIndex(iFlow1, iFlow2)
	
    return mergedFileList
}

private getIFlowBytes(def iFlow){
	
	//Check if latest or specific version is selected
	def iflVersion = iFlow.versionSemanticSelected != "Latest version" ? iFlow.versionSemanticSelected : "active"
	
	//In case the user selected not the newest version, we have to do a rollback
	def rollbackNeeded = iFlow.versionSemanticSelected != "Latest version" && (iFlow.versionSemanticSelected != iFlow.versionSemanticCurrent || iFlow.versionTechnicalSelected != iFlow.versionTechnicalCurrent)
	if (rollbackNeeded){
		rollbackIFlow(iFlow, false)
	}
	
	//Get metadata
	def pkgMetaUrl = "https://${iFlow.hostname}/api/v1/IntegrationDesigntimeArtifacts(Id='${iFlow.iFlowId}',Version='${iflVersion}')"
	def pkgMeta = pkgMetaUrl.toURL().getText([requestProperties:[Authorization:iFlow.credential.basicAuth,"Accept":"application/xml"]])
	
	//Retrieve package url
	def pkgMetaXml = new XmlSlurper().parseText(pkgMeta)
	def pkgDlUrl = "https://${iFlow.hostname}/api/v1/${pkgMetaXml.'**'.find{it.name() == 'link' && it.@rel.text() == 'edit-media'}.@href.text()}"
	
	//Download package
	def artifactBytes = pkgDlUrl.toURL().getBytes([requestProperties:[Authorization:iFlow.credential.basicAuth,"Accept":"application/xml"]])
	
	//If rollback was needed, turn back original state
	if (rollbackNeeded){
		rollbackIFlow(iFlow, true)
	}
	
	return artifactBytes
}

private rollbackIFlow(def iFlow, def toCurrentVersion){
	def rollbackUrlStr = "https://${iFlow.hostname}/itspaces/api/1.0/workspace/${iFlow.packageRegId}/artifacts/${iFlow.iFlowRegId}?webdav=UPDATE"
		
	//Get X-CSRF-Token
	def rollbackUrl = rollbackUrlStr.toURL().openConnection()
	rollbackUrl.setRequestMethod("GET")
	rollbackUrl.setRequestProperty("Authorization", iFlow.credential.basicAuth)
	rollbackUrl.setRequestProperty("X-CSRF-Token", "Fetch")
	def xsrfToken =  rollbackUrl.getHeaderField("X-CSRF-Token")
	def cookies = []
	for (int i = 0;; i++) {
		  if (rollbackUrl.getHeaderFieldKey(i) == null && rollbackUrl.getHeaderField(i) == null) {
			break;
		  }
		  if ("Set-Cookie".equalsIgnoreCase(rollbackUrl.getHeaderFieldKey(i))) {
			cookies << rollbackUrl.getHeaderField(i).split(";")[0]
		  }
	}
	
	//Do rollback to target version
	def versionSemantic = toCurrentVersion ? iFlow.versionSemanticCurrent : iFlow.versionSemanticSelected
	def versionTechnical = toCurrentVersion ? iFlow.versionTechnicalCurrent : iFlow.versionTechnicalSelected
	
	//Rewrite artifact history/set new active version
	rollbackUrl = rollbackUrlStr.toURL().openConnection()
	rollbackUrl.setRequestMethod("PUT")
	rollbackUrl.setRequestProperty("Authorization", iFlow.credential.basicAuth)
	rollbackUrl.setRequestProperty("X-CSRF-Token", xsrfToken)
	rollbackUrl.setRequestProperty("Cookie", cookies.join(';'))
	
	//Set change body/payload
	def payload = "{\"technicalVersion\":${versionTechnical},\"comment\":\"SAP CPI Dashboard automatic rollback via diffutils addon.\",\"semanticVersion\":\"${versionSemantic}\"}"
	rollbackUrl.setDoOutput(true)
	rollbackUrl.setRequestProperty("Content-Type", "application/json")
	rollbackUrl.getOutputStream().write(payload.getBytes("UTF-8"))
	def postRC = rollbackUrl.getResponseCode()
}

private getUserCreds(def credentialName){
	def service = ITApiFactory.getApi(SecureStoreService.class, null)
	def credential = service.getUserCredential(credentialName)
    return [
    	username:credential.getUsername(),
    	password:new String(credential.getPassword()),
    	basicAuth:"Basic ${"${credential.getUsername()}:${new String(credential.getPassword())}".bytes.encodeBase64().toString()}"
    ]
}

private buildFileIndex(def iFlow1, def iFlow2){
	def fileList = []
	def totalUnifiedDiff = ""
	def iFlow1Files = extractZipEntries(iFlow1.bytes)
	def iFlow2Files = extractZipEntries(iFlow2.bytes)
	
	iFlow1Files.each { file1 -> 
		sourceList = []
		sourceList << [id:"${iFlow1.iFlowId}@${iFlow1.hostname}",crc:file1.crc]
		identical = false
		def unifiedDiff = ""
		if (iFlow2Files.any{it.name==file1.name}){
			def file2 = iFlow2Files.find{it.name==file1.name}
			sourceList << [id:"${iFlow2.iFlowId}@${iFlow2.hostname}",crc:file2.crc]
			identical = file2.crc==file1.crc
			if (!identical && isTextBasedFile(file1.name)){
				unifiedDiff = calculateDiff(file1.name, iFlow1.bytes, iFlow2.bytes)
				totalUnifiedDiff += "${unifiedDiff}\n"
			}
		}
		fileList << [name:file1.name,identical:identical,isTextBased:isTextBasedFile(file1.name),unifiedDiff:unifiedDiff,sourceList:sourceList]
	}
	iFlow2Files.findAll{!iFlow1Files.any{ifl1 -> ifl1.name==it.name}}.each{ file2 -> 
		fileList << [name:file2.name,identical:false,isTextBased:isTextBasedFile(file2.name),unifiedDiff:"",sourceList:[[id:"${iFlow2.iFlowId}@${iFlow2.hostname}",crc:file2.crc]]]
	}
	fileList.sort{ a, b ->
	    a.name <=> b.name
	}
	return [fileList:fileList,totalUnifiedDiff:totalUnifiedDiff]
}

private calculateDiff(def fileName, def zipBytes1, def zipBytes2){
	List<String> text1=Arrays.asList(readEntryFromZipAsString(zipBytes1, fileName).replace("\r","").split("\n"));
	List<String> text2=Arrays.asList(readEntryFromZipAsString(zipBytes2, fileName).replace("\r","").split("\n"));

	Patch<String> diff = DiffUtils.diff(text1, text2);
	List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(fileName, fileName, text1, diff, 3);
		
    return unifiedDiff.join("\n")
}

private isTextBasedFile(def fName){
	return !fName.toLowerCase().endsWith(".zip") && !fName.toLowerCase().endsWith(".jar") 
}

private readEntryFromZipAsString(byte[] content, def fileName){
	StringBuilder s = new StringBuilder()
	byte[] buffer = new byte[1024]
	int read = 0
    ZipInputStream zi = null
    try {
        zi = new ZipInputStream(new ByteArrayInputStream(content))
        ZipEntry zipEntry = null
        while ((zipEntry = zi.getNextEntry()) != null) {
            if (zipEntry.name == fileName){
            	while ((read = zi.read(buffer, 0, 1024)) >= 0) {
		        	s.append(new String(buffer, 0, read))
		    	}
            }
        }
    } finally {
        if (zi != null) {
            zi.close()
        }
    }
    return s.toString()
}

private extractZipEntries(byte[] content) throws IOException {
    def entries = []
    ZipInputStream zi = null
    try {
        zi = new ZipInputStream(new ByteArrayInputStream(content))
        ZipEntry zipEntry = null
        while ((zipEntry = zi.getNextEntry()) != null) {
            entries.add(zipEntry)
        }
    } finally {
        if (zi != null) {
            zi.close()
        }
    }
    return entries
}

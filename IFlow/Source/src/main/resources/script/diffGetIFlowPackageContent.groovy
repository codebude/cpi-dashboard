//internal
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.sap.it.api.ITApi;
import com.sap.it.api.ITApiFactory;
import com.sap.it.api.securestore.*;


Message processData(Message message) {
	
	def systems = []

	//Add own system
	systems << [
		credentialName:message.getProperty("ownCredentials"),
		hostname:message.getProperty("ownHostname")
	]

	def remotes = "${message.getProperty("remoteTenants")}".split(';')
	remotes.each{ remote ->
		def pts = remote.split('\\|')
		if (pts.size() == 2 && pts[0]?.trim() && pts[1]?.trim()){
			systems << [
				credentialName:pts[1].trim(),
				hostname:pts[0].trim()
			]
		}
	}
	
	def result = []
	systems.each{ system -> 
		result << [system:system.hostname,content:getDesigntimeContent(system)]
	}
	
    def body = JsonOutput.toJson([count:result.size(),result:result])
    message.setBody(body)
    message.setHeader('Content-Type', 'application/json')
    return message
}

private getDesigntimeContent(def system){
	//Setup credentials
	system.credential = getUserCreds(system.credentialName)
	
	//Download packages
	def pkgUrl = "https://${system.hostname}/itspaces/odata/1.0/workspace.svc/ContentEntities.ContentPackages?\$format=json"
	def pkgList = pkgUrl.toURL().getText([requestProperties:[Authorization:system.credential.basicAuth,"Accept":"application/json"]])
	def pkgJson = new JsonSlurper().parseText(pkgList)
	def pkgs = []
	pkgJson.d.results.each{   
		def pkgContentUrl = "https://${system.hostname}/itspaces/odata/1.0/workspace.svc/ContentEntities.ContentPackages('${it.TechnicalName}')/Artifacts?\$format=json"
		def pkgContentList = pkgContentUrl.toURL().getText([requestProperties:[Authorization:system.credential.basicAuth,"Accept":"application/json"]])
		
		def iFlowList = []
		def json = new JsonSlurper().parseText(pkgContentList)
		json.d.results.each{ iflow -> 
			iFlowList << [id:iflow.Name,name:iflow.DisplayName]
		}
		pkgs << [id:it.TechnicalName,name:it.DisplayName,iFlows:iFlowList]
    }    
    
    //Sort by package name
	pkgJson.sort{ a, b ->
	    a.name <=> b.name
	}
    return pkgs
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
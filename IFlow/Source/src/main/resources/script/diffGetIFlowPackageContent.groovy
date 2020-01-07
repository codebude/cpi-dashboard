//internal
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.sap.it.api.ITApi;
import com.sap.it.api.ITApiFactory;
import com.sap.it.api.securestore.*;


Message processData(Message message) {
	
	//Get url parameters
	def params = [:]
    (message.getHeaders().CamelHttpQuery =~ /(\w+)=?([^&]+)?/)[0..-1].each{
        params[it[1]] = it[2]
    }
	
	//Init variables
	def result = []
	def systems = []
	
	//Prepare system list
	systems << [ //Add own system
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
		
	
	//Package mode
	if (params["mode"] == "packages"){		
		systems.each{ system -> 
			result << [system:system.hostname,content:getDesigntimeContent(system)]
		}
	} else if (params["mode"] == "versions"){		
	
		//Setup system and environment
		def system = systems.find{it -> it.hostname == params['hostname']}
		system.credential = getUserCreds(system.credentialName)
		def versionUrlStr = "https://${params['hostname']}/itspaces/api/1.0/workspace/${params['pkgId']}/artifacts/${params['iflowId']}?versionhistory=true&webdav=REPORT"
		
		//Get X-CSRF-Token
		def versionUrl = versionUrlStr.toURL().openConnection()
		versionUrl.setRequestMethod("GET")
		versionUrl.setRequestProperty("Authorization", system.credential.basicAuth.toString())
		versionUrl.setRequestProperty("X-CSRF-Token", "Fetch")
		def xsrfToken =  versionUrl.getHeaderField("X-CSRF-Token")
		def cookies = []
		for (int i = 0;; i++) {
		      if (versionUrl.getHeaderFieldKey(i) == null && versionUrl.getHeaderField(i) == null) {
		        break;
		      }
		      if ("Set-Cookie".equalsIgnoreCase(versionUrl.getHeaderFieldKey(i))) {
		      	cookies << versionUrl.getHeaderField(i).split(";")[0]
		      }
		}
		
		//Get artifact history
		versionUrl = versionUrlStr.toURL().openConnection()
		versionUrl.setRequestMethod("PUT")
		versionUrl.setRequestProperty("Authorization", system.credential.basicAuth.toString())
		versionUrl.setRequestProperty("X-CSRF-Token", xsrfToken)
		versionUrl.setRequestProperty("Cookie", cookies.join(';'))
		def br = new BufferedReader(new InputStreamReader(versionUrl.getInputStream()))
		StringBuilder sb = new StringBuilder()
        String line
        while ((line = br.readLine()) != null) { sb.append(line+"\n") }
        br.close()
		result = [versionList:new JsonSlurper().parseText(sb.toString())]
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
			iFlowList << [id:iflow.Name,name:iflow.DisplayName,reg_id:iflow.reg_id]
		}
		pkgs << [id:it.TechnicalName,name:it.DisplayName,reg_id:it.reg_id,iFlows:iFlowList]
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
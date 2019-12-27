/*
 Prepares a JSON on different design and runtime artifacts on multiple tenants.
 */
import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.HashMap;
import groovy.json.*;
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import com.sap.it.api.ITApi;
import com.sap.it.api.ITApiFactory;
import com.sap.it.api.securestore.*;

Message processData(Message message) {
	def systems = getSystems(message)
	def orderOfSystems = message.getProperty("remoteTenantsOrder")

  def i = 0
	def result = [systems:[:],artifacts:[]]
  def artifacts = [:]
	systems.each{ system ->
    result.systems << ["${i}":system.hostname]
    artifacts = addDesigntimeArtifacts(system,artifacts,i)
    artifacts = addRuntimeArtifacts(system,artifacts,i)
    i++
	}

  // Improve artifacts - set missing versions and evaluate issues
  artifacts.each{artifact ->
		def findings = ""
		artifacts[artifact.key].Severity = "OK"
    0.upto(i-1, {
      if(!artifacts[artifact.key].get("VersionDesigntime").containsKey("${it}")){
        artifacts[artifact.key].VersionDesigntime << ["${it}":'']
      }
      if(!artifacts[artifact.key].get("VersionRuntime").containsKey("${it}")){
        artifacts[artifact.key].VersionRuntime << ["${it}":'']
      }
      if(!artifacts[artifact.key].get("DesigntimePackage").containsKey("${it}")){
        artifacts[artifact.key].DesigntimePackage << ["${it}":'']
      }

			////// Evaluate issues
			//// Evaluate general issues on one tenant
			// Check if the artifact is deployed on the runtime but not available on the designtime of the same tenant
			if((artifacts[artifact.key].get('VersionDesigntime').get("${it}") == '') && !(artifacts[artifact.key].get('VersionRuntime').get("${it}") == '')) {
				 findings += "Available on Runtime [${it}] but not on Designtime [${it}]|"
				 artifacts[artifact.key].Severity = "CRITICAL"
			// Check if the versions on design and runtime are different on the same tenant (only if available on runtime)
			} else if((artifacts[artifact.key].get('VersionRuntime').get("${it}") != '') && ((artifacts[artifact.key].get('VersionDesigntime').get("${it}") != artifacts[artifact.key].get('VersionRuntime').get("${it}")))) {
				 findings += "Different Version on Runtime and Designtime [${it}]|"
				 if(artifacts[artifact.key].Severity == "OK") {
				 	artifacts[artifact.key].Severity = "WARNING"
				 }			// Check if the artifact is available on the Designtime of the tenant (in case it is not available on all tenants)
			} else if((artifacts[artifact.key].get('VersionDesigntime').get("${it}") == '') && (orderOfSystems != 'UP') && (orderOfSystems != 'DOWN')) {
				 findings += "Not available on Designtime [${it}]|"
				 if(artifacts[artifact.key].Severity == "OK") {
					 artifacts[artifact.key].Severity = "WARNING"
				 }
			}

			//// Evaluate issues based on multiple tenants (only applicable if external parameter 'remoteTenantsOrder' is set)
			if (orderOfSystems == "DOWN") {
				if((it > 0) && (it <= i)) {
					if (artifacts[artifact.key].get('VersionDesigntime').get("${it-1}")?.trim()) {
						//// Not necessary: Check if a newer version of an artifact is deployed on an upper tenant and if not, check if a newer version of an artifact is deployed on an upper tenant
						// if (!(artifacts[artifact.key].get('VersionRuntime').get("${it-1}") == '') && (artifacts[artifact.key].get('VersionRuntime').get("${it}") == '')) {
						// 	 findings += "Artifact is available on runtime [${it-1}] but not on [${it}]|"
						// 	 artifacts[artifact.key].Severity = "CRITICAL"
						// } else if ((artifacts[artifact.key].get('VersionRuntime').get("${it-1}") != '') && (artifacts[artifact.key].get('VersionRuntime').get("${it-1}") != artifacts[artifact.key].get('VersionRuntime').get("${it}")) && !(artifacts[artifact.key].get('VersionRuntime').get("${it-1}") == getLatestVersion(['' + artifacts[artifact.key].get('VersionRuntime').get("${it}"), '' + artifacts[artifact.key].get('VersionRuntime').get("${it-1}")]))) {
						// 	findings += "Newer version on runtime of [${it-1}] as on [${it}]|"
						// 	artifacts[artifact.key].Severity = "CRITICAL"
						// }

						// Check if the artifact is not available on a lower tenant and if not, check if a newer version of an artifact is available on an upper tenant
						if (!(artifacts[artifact.key].get('VersionDesigntime').get("${it-1}") == '') && (artifacts[artifact.key].get('VersionDesigntime').get("${it}") == '')) {
							 findings += "Artifact is available on Designtime [${it-1}] but not on [${it}]|"
							 artifacts[artifact.key].Severity = "CRITICAL"
						} else if ((artifacts[artifact.key].get('VersionDesigntime').get("${it-1}") != '') && (artifacts[artifact.key].get('VersionDesigntime').get("${it-1}") != artifacts[artifact.key].get('VersionDesigntime').get("${it}")) && !(artifacts[artifact.key].get('VersionDesigntime').get("${it-1}") == getLatestVersion(['' + artifacts[artifact.key].get('VersionDesigntime').get("${it}"), '' + artifacts[artifact.key].get('VersionDesigntime').get("${it-1}")]))) {
							 findings += "Newer version on Designtime of [${it-1}] as on [${it}]|"
							 artifacts[artifact.key].Severity = "CRITICAL"
						}
					}
				}
			} else if (orderOfSystems == "UP") {
				if (it < systems.size() - 1) {
					def j = (systems.size() - 1) - it
					if (artifacts[artifact.key].get('VersionDesigntime').get("${j}")?.trim()) {
						//// Not necessary: Check if a newer version of an artifact is deployed on an upper tenant and if not, check if a newer version of an artifact is deployed on an upper tenant
						// if (!(artifacts[artifact.key].get('VersionRuntime').get("${j}") == '') && (artifacts[artifact.key].get('VersionRuntime').get("${j-1}") == '')) {
						// 	 findings += "Artifact is available on runtime [${j}] but not on [${j-1}]|"
						// 	 artifacts[artifact.key].Severity = "CRITICAL"
						// } else if ((artifacts[artifact.key].get('VersionRuntime').get("${j}") != '') && (artifacts[artifact.key].get('VersionRuntime').get("${j}") != artifacts[artifact.key].get('VersionRuntime').get("${j-1}")) && !(artifacts[artifact.key].get('VersionRuntime').get("${j}") == getLatestVersion(['' + artifacts[artifact.key].get('VersionRuntime').get("${j}"), '' + artifacts[artifact.key].get('VersionRuntime').get("${j-1}")]))) {
						// 	findings += "Newer version on runtime of [${j}] as on [${j-1}]|"
						// 	artifacts[artifact.key].Severity = "CRITICAL"
						// }

						// Check if the artifact is not available on a lower tenant and if not, check if a newer version of an artifact is available on an upper tenant
						if (!(artifacts[artifact.key].get('VersionDesigntime').get("${j}") == '') && (artifacts[artifact.key].get('VersionDesigntime').get("${j-1}") == '')) {
							 findings += "Artifact is available on Designtime [${j}] but not on [${j-1}]|"
							 artifacts[artifact.key].Severity = "CRITICAL"
						} else if ((artifacts[artifact.key].get('VersionDesigntime').get("${j}") != '') && (artifacts[artifact.key].get('VersionDesigntime').get("${j}") != artifacts[artifact.key].get('VersionDesigntime').get("${j-1}")) && !(artifacts[artifact.key].get('VersionDesigntime').get("${j}") == getLatestVersion(['' + artifacts[artifact.key].get('VersionDesigntime').get("${j}"), '' + artifacts[artifact.key].get('VersionDesigntime').get("${j-1}")]))) {
							 findings += "Newer version on Designtime of [${j}] as on [${j-1}]|"
							 artifacts[artifact.key].Severity = "CRITICAL"
						}
					}
				}
			}
    })
		if(findings.length() > 0) {
			artifacts[artifact.key].Remarks = findings.substring(0, findings.length() - 1)
		}
  }

  result.artifacts = artifacts

  def body = JsonOutput.toJson(result)
  message.setBody(body)
  message.setHeader('Content-Type', 'application/json')
  return message
}

private addDesigntimeArtifacts(def system, def artifacts, def i){
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
    def packageDisplayname = "${it.TechnicalName}"

		def iFlowList = []
		def json = new JsonSlurper().parseText(pkgContentList)
		def artifactsOut = artifacts
    json.d.results.each{
        if (artifactsOut.containsKey(it.Name)){
            artifactsOut[it.Name].VersionDesigntime << ["${i}":it.Version]
            artifactsOut[it.Name].VersionRuntime << ["${i}":'']
            artifactsOut[it.Name].DesigntimePackage << ["${i}":packageDisplayname]
        } else {
            artifactsOut[it.Name]= [
                Id:it.Name,
                Name:it.DisplayName,
                DesigntimePackage:["${i}":packageDisplayname],
                VersionRuntime:["${i}":''],
                VersionDesigntime: ["${i}":it.Version],
                Type:(it.Type=="IFlow"?"INTEGRATION_FLOW":(it.Type=="ValueMapping"?"VALUE_MAPPING":it.Type)),
								Remarks:''
            ]
        }
      }
    }
    return artifacts
}

private addRuntimeArtifacts(def system, def artifacts, def i){
	//Setup credentials
	system.credential = getUserCreds(system.credentialName)

	//Download runtime Artifacts
	def runtimeArtifactsUrl = "https://${system.hostname}/api/v1/IntegrationRuntimeArtifacts"
	def runtimeArtifactsList = runtimeArtifactsUrl.toURL().getText([requestProperties:[Authorization:system.credential.basicAuth,"Accept":"application/json"]])
	def runtimeArtifactsJson = new JsonSlurper().parseText(runtimeArtifactsList)
	runtimeArtifactsJson.d.results.each{
    if (artifacts.containsKey(it.Name)){
        artifacts[it.Id].VersionRuntime << ["${i}":it.Version]
    } else {
        artifacts[it.Id]= [
            Id:it.Id,
            Name:it.Name,
            DesigntimePackage:["${i}":''],
            VersionRuntime:["${i}":it.Version],
            VersionDesigntime: ["${i}":''],
            Type:it.Type,
						Remarks:''
        ]
    }
  }
  return artifacts
}

/* Returns the different systems as an array */
private getSystems(def message){
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
    return systems
}

/* Load user credentials */
private getUserCreds(def credentialName){
	def service = ITApiFactory.getApi(SecureStoreService.class, null)
	def credential = service.getUserCredential(credentialName)
    return [
    	username:credential.getUsername(),
    	password:new String(credential.getPassword()),
    	basicAuth:"Basic ${"${credential.getUsername()}:${new String(credential.getPassword())}".bytes.encodeBase64().toString()}"
    ]
}

/* Find the latest version */
private getLatestVersion(List versions) {
	def versionComparator = { a, b ->
	  def VALID_TOKENS = /._/
	  a = a.tokenize(VALID_TOKENS)
	  b = b.tokenize(VALID_TOKENS)

	  for (i in 0..<Math.min(a.size(), b.size())) {
	    if (i == a.size()) {
	      return b[i].isInteger() ? -1 : 1
	    } else if (i == b.size()) {
	      return a[i].isInteger() ? 1 : -1
	    }

	    if (a[i].isInteger() && b[i].isInteger()) {
	      int c = (a[i] as int) <=> (b[i] as int)
	      if (c != 0) {
	        return c
	      }
	    } else if (a[i].isInteger()) {
	      return 1
	    } else if (b[i].isInteger()) {
	      return -1
	    } else {
	      int c = a[i] <=> b[i]
	      if (c != 0) {
	        return c
	      }
	    }
	  }

	  return 0
	}

	versions.sort(versionComparator)

	return versions[-1]
}

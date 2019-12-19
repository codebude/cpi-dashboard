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

  def i = 0
	def result = [systems:[],artifacts:[]]
  def artifacts = [:]
	systems.each{ system ->
    result.systems << [id:i,hostname:system.hostname]
    //TODO: artifacts = addRuntimeArtifacts(system,artifacts,i)
    artifacts = addDesigntimeArtifacts(system,artifacts,i)
    i++
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
            artifactsOut[it.Name].DesigntimePackage << ["${i}":packageDisplayname]
        } else {
            artifactsOut[it.Name]= [
                Id:it.Name,
                Name:it.DisplayName,
                DesigntimePackage:["${i}":packageDisplayname],
                VersionRuntime:"---",
                VersionDesigntime: ["${i}":it.Version],
                Type:(it.Type=="IFlow"?"INTEGRATION_FLOW":(it.Type=="ValueMapping"?"VALUE_MAPPING":it.Type))
            ]
            //TODO: if i>0 than set old versions to ----
        }
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

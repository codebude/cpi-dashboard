//internal
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.sap.it.api.ITApi;
import com.sap.it.api.ITApiFactory;
import com.sap.it.api.securestore.*;

Message processData(Message message) {
		
	//Init variables
	def result = []
	def systems = []
	def hostname = message.getProperty("envHostname")
	def tenantId = message.getProperty("envTenantId")
	def username = message.getProperty("envUser")
	
	//Prepare system list
	systems << [
		credentialName:username,
		hostname:hostname, 
		credential: getUserCreds(username)
	]
	
	systems.each{ system -> 
		result << [system:system.hostname,endpoints:getEndpoints(system, tenantId)]
	}
	
    def body = JsonOutput.toJson([count:result.size(),result:result])
    message.setBody(body)
    message.setHeader('Content-Type', 'application/json')
    return message
}

private getEndpoints(def system, def tenantId){
		//Basic setup
		system.credential = getUserCreds(system.credentialName)
		def result = [:]
		
		//Get list of all deployed IFlows incl. internal ids
		def participantListCommandUrlStr = "https://${system.hostname}/itspaces/Operations/com.sap.it.op.srv.commands.dashboard.ParticipantListCommand?tenantId=${tenantId}"
		def artListStr = participantListCommandUrlStr.toURL().getText([requestProperties:[Authorization:system.credential.basicAuth,"Accept":"application/json"]])
		def artList = new JsonSlurper().parseText(artListStr)
		
		artList.participantInformation.nodes.node.deployedArtifacts[0].each { node ->
			node.each { iFlow -> 
				if (!result.any{ it.value.id == iFlow.id } && iFlow.linkedComponentType == "INTEGRATION_FLOW"){
					
					//load iflows endpoints
					
					def integrationComponentDetailCommandUrlStr = "https://${system.hostname}/itspaces/Operations/com.sap.it.op.tmn.commands.dashboard.webui.IntegrationComponentDetailCommand?artifactId=${iFlow.id}"
					def artDetailsStr = integrationComponentDetailCommandUrlStr.toURL().getText([requestProperties:[Authorization:system.credential.basicAuth,"Accept":"application/json"]])
					def artDetails = new JsonSlurper().parseText(artDetailsStr)
					
					def endpointsDetailed = []
					artDetails.endpointInformation.each { ei -> 
						def singleEndpointDetailsList = []
						ei.endpointInstances.each{ einst ->
							def singleEndpointDetails = [:]
							singleEndpointDetails << [url:einst.endpointUrl]
							singleEndpointDetails << [type:einst.endpointCategory]
							singleEndpointDetailsList.push(singleEndpointDetails)
						}
						endpointsDetailed.push(singleEndpointDetailsList)
					}
					
					result << ["${iFlow.symbolicName}":[
							id: iFlow.id,
							symbolicName: iFlow.symbolicName,
							name: iFlow.name,
							endpointsFlat: artDetails.endpoints,
							endpointsDetailed:endpointsDetailed
						]
					]
				}
			}
		}
		return result
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
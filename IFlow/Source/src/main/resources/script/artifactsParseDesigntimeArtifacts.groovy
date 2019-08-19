/*
 The integration developer needs to create the method processData 
 This method takes Message object of package com.sap.gateway.ip.core.customdev.util 
which includes helper methods useful for the content developer:
The methods available are:
    public java.lang.Object getBody()
	public void setBody(java.lang.Object exchangeBody)
    public java.util.Map<java.lang.String,java.lang.Object> getHeaders()
    public void setHeaders(java.util.Map<java.lang.String,java.lang.Object> exchangeHeaders)
    public void setHeader(java.lang.String name, java.lang.Object value)
    public java.util.Map<java.lang.String,java.lang.Object> getProperties()
    public void setProperties(java.util.Map<java.lang.String,java.lang.Object> exchangeProperties) 
    public void setProperty(java.lang.String name, java.lang.Object value)
    public java.util.List<com.sap.gateway.ip.core.customdev.util.SoapHeader> getSoapHeaders()
    public void setSoapHeaders(java.util.List<com.sap.gateway.ip.core.customdev.util.SoapHeader> soapHeaders) 
       public void clearSoapHeaders()
 */
import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.HashMap;
import groovy.json.*;

def Message processData(Message message) {
  
    def body = message.getBody(java.lang.String) as String
    def json = new JsonSlurper().parseText(body)
    def artifacts = message.getProperties().get("artifactList")
    json.d.results.each{    
        if (artifacts.containsKey(it.Name)){
            artifacts[it.Name].ExistsDesigntime = true
            artifacts[it.Name].VersionDesigntime = it.Version
            artifacts[it.Name].DesigntimePackage = message.getProperties().get("packageDisplaynameList")[0]
        } else {
            artifacts[it.Name]= [
                Id:it.Name,
                Name:it.DisplayName,
                DesigntimePackage:message.getProperties().get("packageDisplaynameList")[0],
                VersionRuntime:"",
                VersionDesigntime:it.Version,
                Type:(it.Type=="IFlow"?"INTEGRATION_FLOW":(it.Type=="ValueMapping"?"VALUE_MAPPING":it.Type)),
                ExistsRuntime:false,
                ExistsDesigntime:true
            ]
        }      
    }    
    message.setProperty("artifactList", artifacts)

    //Remove package from lookup list
    def packages = message.getProperties().get("packageNameList").reverse() as Stack
    packages.pop()
    packages = packages.reverse() as Stack
    message.setProperty("packageNameList", packages)
    def packagesDisplay = message.getProperties().get("packageDisplaynameList").reverse() as Stack
    packagesDisplay.pop()
    packagesDisplay = packagesDisplay.reverse() as Stack
    message.setProperty("packageDisplaynameList", packagesDisplay)

    return message;
}
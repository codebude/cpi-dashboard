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
   
    //Handle roles
    def body = message.getBody(java.lang.String) as String;
    def jsonSlurper = new JsonSlurper()
    def roleJson = jsonSlurper.parseText(body)
    def roleList = []
    if (message.getProperties().get("userRoles") != null){
        roleList = message.getProperties().get("userRoles");
    }
    roleJson.roles.each{
        roleList.push(it.name)
    }
    message.setProperty("userRoles", roleList);

    //Handle groups (if in role by group loop)
    if (message.getProperties().get("userGroups") != null){
        def groupList = message.getProperties().get("userGroups");
        def group = ""
        if ((groupList.size() > 0)){
            group = groupList.pop()
        }
        message.setHeader("nextGroup", group)
        message.setProperty("userGroups", groupList)
    }
    
    return message;
}
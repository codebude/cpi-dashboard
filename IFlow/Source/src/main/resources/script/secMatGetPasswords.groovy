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
import com.sap.it.api.ITApi;
import com.sap.it.api.ITApiFactory;
import com.sap.it.api.securestore.*;

def Message processData(Message message) {
    
    def service = ITApiFactory.getApi(SecureStoreService.class, null)
    
    def body = message.getBody(java.lang.String) as String
    def json = new JsonSlurper().parseText(body)
    def keyList = []
    json.d.results.each{
        
        def credential = service.getUserCredential(it.Name)
        def pw = ""
        if (credential != null){
            pw = new String(credential.getPassword())            
        }
        
    	keyList.push([
    		id:it.Name,
    		user:it.User,
    		password:pw,
    		type:it.Kind
    	])
    }
    message.getHeaders().put("Content-Type", "application/json")
    def outJson = JsonOutput.toJson(keyList)
    message.setBody(outJson)

    return message;
}
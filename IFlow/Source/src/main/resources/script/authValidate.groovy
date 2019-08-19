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
def Message processData(Message message) {
  
    def callingUser = message.getHeaders().get('SapAuthenticatedUserName')
    def contextPath = message.getHeaders().get('CamelServletContextPath')
    def dashboardUrlBase = message.getProperties().get('dashboardUrlBase')
    def generalAccessRole = message.getProperties().get('roleNameGeneralAccess')
    def logAndFileAccessRole = message.getProperties().get('roleNameLogAndFileAccess')
    
    //Check for general access right
    if(!message.getProperties().get("userRoles").contains(generalAccessRole)){
        throw new Exception("User ${callingUser} not authorized. Missing role: '${generalAccessRole}'.")    
    }
    
    //If the request came via /download-path, check additional role
    if (contextPath.startsWith("${dashboardUrlBase}/view") && !message.getProperties().get("userRoles").contains(logAndFileAccessRole)){
        throw new Exception("User ${callingUser} not authorized to download files. Missing role: '${logAndFileAccessRole}'.")    
    }
    
    return message;
}
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
  
    //Setup query
    def queries = message.getProperties().get("cacheMplQueries")
    if (queries != null && queries.size() > 0){
        def nextQuery = queries.pop()
        message.setProperty("cacheMplQueries",queries)
        message.setProperty("mplQuery",nextQuery)
        message.setProperty("mplCacheId",nextQuery.hash)
    }
    
    //Set OAuth bearer header for MPL request
    message.setHeader("Authorization", "Bearer ${message.getProperties().get('accessToken')}");
    message.setHeader("Accept", "application/json");
    
    return message;
}
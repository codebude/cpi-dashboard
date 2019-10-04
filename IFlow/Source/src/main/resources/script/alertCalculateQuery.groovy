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
import groovy.time.TimeCategory;
import java.security.MessageDigest;

def Message processData(Message message) {
  
    //Calculate query
    def body = message.getBody(java.lang.String) as String
    
    def endDate = new Date()
    def fEndDate = endDate.format("yyyy-MM-dd'T'HH:mm:ss")
    message.setProperty("alertLastRunDateNew", fEndDate)
    
    def queryStr = ""
    if (body.length() > 0){
        queryStr = "\$filter=LogStart ge datetime'${body}' and LogStart le datetime'${fEndDate}'"
    } else {
        def startDate = new Date()
        use (TimeCategory){
            startDate = startDate - 1.hours
        }
        queryStr = "\$filter=LogStart ge datetime'${fStartDate}' and LogStart le datetime'${fEndDate}'"
    }
    message.setProperty("alertQueryMpl", queryStr)
   
    return message;
}
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
import java.io.StringWriter;
import java.io.PrintWriter;

def Message processData(Message message) {
    
    def mapHdr = message.getHeaders()
    def mapProp = message.getProperties()
    
    def httpStatusCode = mapHdr.get("CamelHttpResponseCode");
    if (httpStatusCode == null || httpStatusCode == 200){
        httpStatusCode = 500;
        message.setHeader("CamelHttpResponseCode", httpStatusCode)
    }
    def ex = mapProp.get("CamelExceptionCaught")
    def exCaughtText = ex.getMessage()
    
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    ex.printStackTrace(pw);
    def exCaughtStack = sw.toString(); 
    
    def exception = []
    exception.push(error:[
        code:httpStatusCode,
        message:"There was an error. Please contact the dev team.",
        dogception:"https://httpstatusdogs.com/img/${httpStatusCode}.jpg",
        exception:[
            text:exCaughtText,
            stacktrace:exCaughtStack
        ]
    ])
    
    //Set response
    message.getHeaders().put("Content-Type", "application/json")
    def outJson = JsonOutput.toJson(exception)
    message.setBody(outJson);
     
    return message;
}

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
  
    //Calculate queries
    def queries = []
    0.upto(47, { i->
    
        def startDate = new Date()
        use (TimeCategory){
            startDate = startDate - i.hours
        }
        def fStartDate = "${startDate.format("yyyy-MM-dd'T'HH")}:00:00.000";
        
        def endDate = new Date()
        def fEndDate = endDate.format("yyyy-MM-dd'T'HH:mm:ss.SSS")
        if (i != 0){
            use (TimeCategory){
    		    endDate = endDate - i.hours
       	    }
    	    fEndDate = "${endDate.format("yyyy-MM-dd'T'HH")}:59:59.999";
        }
        def queryStr = "\$filter=LogStart ge datetime'${fStartDate}' and LogStart le datetime'${fEndDate}'"
        
        queries.push([
        	startDate:fStartDate,
        	endDate:fEndDate,
            query:queryStr,
            hash:calculateMd5(queryStr),
        	type:"HOURLY"
        ])
    })

    
    0.upto(29, { i->
    
        def startDate = new Date()
        use (TimeCategory){
            startDate = startDate - i.days
        }
        def fStartDate = "${startDate.format("yyyy-MM-dd")}T00:00:00.000";
        
        def endDate = new Date()
        def fEndDate = endDate.format("yyyy-MM-dd'T'HH:mm:ss.SSS")
        if (i != 0){
            use (TimeCategory){
        	    endDate = endDate - i.days
            }
            fEndDate = "${endDate.format("yyyy-MM-dd")}T23:59:59.999";
        }
        def queryStr = "\$filter=LogStart ge datetime'${fStartDate}' and LogStart le datetime'${fEndDate}'"
        
        queries.push([
            startDate:fStartDate,
            endDate:fEndDate,
            query:queryStr,
            hash:calculateMd5(queryStr),
            type:"DAILY"
        ])
    })
   
    message.setProperty("cacheMplQueries", queries)
   
    return message;
}

public String calculateMd5(String s){
    return MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString();
}

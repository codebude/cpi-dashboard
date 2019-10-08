/*
This script was written and debugged in the SAP_CPI_Groovy_Emulator IntelliJ project developed by Jernej Strazisar
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
 */

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.*
import groovy.time.TimeCategory;

def Message processData(Message message) {

    //Container for alerting mails
    def alertMails = []

    new JsonSlurper().parseText(message.getProperty("alertConfig")).rules.each { rule ->
        //only process active rules
        if (rule.active){
            if (rule.ruletype.equals("message")){
                //Check all messages for matches
                def matchingMsgs = []
                new JsonSlurper().parseText(message.getProperty("cacheAlertMplResponse")).d.results.each { msg ->
                    if (rule.state != "ALL" && msg.Status.toLowerCase() != rule.state.toLowerCase()){ return }
                    if (!isWildcardMatch(rule.artifact_id,  msg.IntegrationFlowName)){ return }
                    if (!isWildcardMatch(rule.sender,  msg.Sender)){ return }
                    if (!isWildcardMatch(rule.receiver,  msg.Receiver)){ return }
                    matchingMsgs.push([id:msg.MessageGuid,name:msg.IntegrationFlowName])
                }
                if (matchingMsgs.size() > 0) {
                    def mailText = "Dear operator(s),<br/><br/>during the alert check for message between ${message.getProperty("alertLastRunBeginDate")}(UTC) and ${message.getProperty("alertLastRunDateNew")}(UTC) there was a problem found in the interface \"${rule.artifact_id}\".<br/><br/><u>The matching alert rule is:</u> ${rule.name}<br/><u>The user alert text is:</u> ${rule.alert_receiver_body}<br/><br/><u>The following ${matchingMsgs.size()} messages are affected:</u><br/><ul>"
                    matchingMsgs.each { msg ->
                        mailText += "<li><a href=\"https://p0401-tmn.hci.eu1.hana.ondemand.com/itspaces/shell/monitoring/Messages/%7B%22artifact%22:%22${rule.artifact_id}%22%7D\">${msg.id}</a> (Iflow-ID: ${msg.name})</li>"
                    }
                    mailText += "</ul>"
                    rule.put("mailtext",mailText)
                    alertMails.add(rule)
                }
            } else if (rule.ruletype.equals("certificate")){
                def matchingCerts = []
                new JsonSlurper().parseText(message.getProperty("cacheAlertCertResponse")).d.results.each { cert ->
                    if (rule.certificate_id != null && !rule.certificate_id.equals("")){
                        //Cert id is given, so check name first
                        def regPattern = rule.certificate_id.replace("*", ".*?")
                        if (!cert.Alias.matches(regPattern)){
                            return
                        }
                    }
                    //Calculate valid until date
                    def m = cert.ValidNotAfter =~ /\/Date\((\d+)\)\//;
                    def ts = Long.valueOf(m[0][1])
                    def validUntil = new Date(ts)

                    //Calculate alert date
                    def alertDate = new Date()
                    use (TimeCategory){
                        alertDate = alertDate + rule.warn_days_before.hours
                    }
                    if (alertDate < validUntil){
                        return
                    }
                    matchingCerts.push([id:cert.Alias,valid:validUntil.toString()])
                }
                if (matchingCerts.size() > 0) {
                    def mailText = "Dear operator(s),<br/><br/>during the alert checks at ${message.getProperty("alertLastRunDateNew")}(UTC) there was a problem found with the certificate cache.<br/><br/><u>The matching alert rule is:</u> ${rule.name}<br/><u>The user alert text is:</u> ${rule.alert_receiver_body}<br/><br/><u>The following ${matchingCerts.size()} certificates are running out of validity:</u><br/><ul>"
                    matchingCerts.each { cert ->
                        mailText += "<li>${cert.id} (Valid until: ${cert.valid})</li>"
                    }
                    mailText += "</ul>"
                    rule.put("mailtext",mailText)
                    alertMails.add(rule)
                }
            }
        }
    }
    message.setProperty("numAlertMails", alertMails.size())
    def alertMailsJson = JsonOutput.toJson(alertMails)
    message.setBody("{ \"mails\":"+alertMailsJson+" }")
    return message
}

def boolean isWildcardMatch(def rule, def msg){
    if (rule == null || rule.equals("") || rule.equals("*")) //if rule is null/empty it isn't handled as filter and accepts every msg state
        return true
    if (msg == null || msg.equals("null")) //if msg value is null/empty, but rule is is filled, it can't match
        return false
    if (!rule.contains("*")){ //if rule as no wildcard we can match directly
        return rule == msg
    } else { //if rule contains wildcars we have to evaluate them
        def regPattern = rule.replace("*", ".*?")
        return msg.matches(regPattern)
    }
}

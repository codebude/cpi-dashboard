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
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import org.osgi.framework.FrameworkUtil
import java.time.ZonedDateTime
import org.quartz.CronScheduleBuilder
import org.quartz.CronTrigger
import org.quartz.TriggerBuilder
import org.joda.time.DateTime

import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.definition.CronDefinition
import com.cronutils.parser.CronParser
import com.cronutils.descriptor.CronDescriptor
import com.cronutils.model.CronType
import com.cronutils.model.time.ExecutionTime

Message processData(Message message) {

    def bundleContext = FrameworkUtil.getBundle(Class.forName("com.sap.gateway.ip.core.customdev.util.Message")).getBundleContext()
   
    def listIFlows = []
    bundleContext.getBundles().each {
    	def crons = getcronEntriesByBeansXml(it)
	    if (crons.size() > 0){	
	    	listIFlows.push([
	            name:it.getSymbolicName(),
	            crons:crons
	        ])
        }
    }
   
	def runs = []
	listIFlows.each{ iflow -> 
		iflow.crons.each{ cronItem -> 
			def dtStart = DateTime.now().toDate()
			def dtEnd = DateTime.now().plusHours(24).toDate()
			CronTrigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(cronItem.cronInfo.cron)).build()
		    def nextFire = trigger.getFireTimeAfter(dtStart)
		    if (runs.any{ it.datetime == nextFire }){
		    	runs.find{ it.datetime == nextFire }.interfaces << iflow.name
		    } else {
		    	runs << [datetime:nextFire,interfaces:[iflow.name]]
		    }
		    while(nextFire && nextFire <= dtEnd){
		        nextFire = trigger.getFireTimeAfter(nextFire)
		        if (runs.any{ it.datetime == nextFire }){
			    	runs.find{ it.datetime == nextFire }.interfaces << iflow.name
			    	runs.find{ it.datetime == nextFire }.interfaces.sort()
			    } else {
			    	runs << [datetime:nextFire,interfaces:[iflow.name]]
			    }
		    }
		}
	}
	runs.sort{ a, b ->
	    a.datetime <=> b.datetime
	}
   
    def body = JsonOutput.toJson(byInterface:[count:listIFlows.size(),list:listIFlows],byRunDate:[count:runs.size(),runs:runs])
    message.setBody(body)
    message.setHeader('Content-Type', 'application/json')
    
    return message
}

def getcronEntriesByBeansXml(bundle){
    def cronEntries = []
    def cronEntry = [:]

    bundle.findEntries("/OSGI-INF/blueprint/", "*.xml", false).each{ filePathBlueprint->
    
    	//Read beans.xml
    	def beansXml = readTextFileToString(filePathBlueprint)
        
        //Parse beans.xml
        //blueprint/camel:camelContext/camel:route/camel:from
        if (beansXml.find("(?<=cron=)(.+?(?=&))") != null){
        	def beansXmlDoc = new XmlSlurper().parseText(beansXml)
        	beansXmlDoc.'**'.findAll{ node -> node.name() == "from" && node.@uri.text() ==~ /.*?(?<=cron=)(.+?(?=&)).*?/ }.each{ fromRoute ->
        		//Get cron statement
        		def cron = fromRoute.@uri.text().find("(?<=cron=)(.+?(?=&))").replace("+"," ")
        		
        		//Get id and match it against external properties
        		def id = fromRoute.@id.text().substring(0, fromRoute.@id.text().lastIndexOf("_"))
        		def patternName = null
        		def isExternalized = false
        		bundle.findEntries("/src/main/resources/scenarioflows/integrationflow", "*.iflw", false).each{ filePathIflw->
        			if (!isExternalized){
	        			def iflwXmlDoc = new XmlSlurper().parseText(readTextFileToString(filePathIflw))
	        			def startEvent = iflwXmlDoc.'**'.find{ it.name() == "startEvent" && it.@id.text() == id }
	        			if (startEvent != null && startEvent.'**'.findAll{ it.name() == "property" && it.key.text() == "scheduleKey" }.size() > 0){
	        				def timerConfig = startEvent.'**'.find{ it.name() == "property" && it.key.text() == "scheduleKey" }.value.text()
	        				isExternalized = timerConfig ==~ /\{\{(.+)\}\}/
	        				if (isExternalized){
	        					def matcher = timerConfig =~ /\{\{(.+)\}\}/
	        					patternName = matcher[0][1]
	        				}
	        			}
        			}
        		}
        		
        		//Create description
        		def cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
				def parser = new CronParser(cronDefinition)
				def cronObj = parser.parse(cron)
				def descriptor = CronDescriptor.instance(Locale.UK)
				def description = descriptor.describe(cronObj)
				if (description.length() > 1){
					description = "${description.substring(0, 1).toUpperCase() + description.substring(1)}."
				}
        		
        		//Create next three runs/today's runs
        		def fireNextThree = []
        		def dtStart = DateTime.now().toDate()
        		CronTrigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(cron)).build()
			    def nextFire = trigger.getFireTimeAfter(dtStart)
			    fireNextThree << nextFire
			    while(nextFire && fireNextThree.size() < 3){
			        nextFire = trigger.getFireTimeAfter(nextFire)
			        fireNextThree << nextFire
			    }
        		
        		cronEntries << [cronInfo: [id:id, cron:cron, cronHumanReadable:description, isExternalized:isExternalized, patternName:patternName, nextThreeFires:fireNextThree]]
        	}
        }
    }
    return cronEntries
}

def readTextFileToString(URL file){
	BufferedReader br = new BufferedReader(new InputStreamReader(file.openConnection().getInputStream()))
	def fileContent = ""
    while(br.ready()){
        fileContent += br.readLine()
    }
    br.close()
    return fileContent
}

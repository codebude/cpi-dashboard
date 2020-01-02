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
import org.osgi.framework.FrameworkUtil;
import java.util.HashMap;
import groovy.json.*;
import java.util.concurrent.TimeUnit;

def Message processData(Message message) {
     
    def body = message.getBody(java.lang.String) as String;
    def systemStatus = []
    
    //Get calling interface user
    
    def userName = message.getHeaders().get("SapAuthenticatedUserName")
    def rolePasswordAccess = message.getProperties().get("rolePasswordAccess")
	def roleShellAccess = message.getProperties().get("roleNameShellAccess")
    def roleGeneralAccess = message.getProperties().get("roleNameGeneralAccess")
    def roleLogAndFileAccess = message.getProperties().get("roleNameLogAndFileAccess")
    def scopePasswordAccess = message.getProperties().get("userRoles").contains(rolePasswordAccess)
	def scopeShellAccess = message.getProperties().get("userRoles").contains(roleShellAccess)
    def scopeGeneralAccess = message.getProperties().get("userRoles").contains(roleGeneralAccess)
    def scopeLogAndFileAccess = message.getProperties().get("userRoles").contains(roleLogAndFileAccess)
       
    /***************************/
    //      osInfo - Node 
    /***************************/
    
    //Read and calculate uptime 
    File fUptime = new File("/proc/uptime")
    def uptime = fUptime.getText("UTF-8")
    uptime = uptime.substring(0,uptime.indexOf(" "))
    
    //Read operating system version
    File fRelease = new File("/proc/version")
    def sysVersion = fRelease.getText("UTF-8")
    sysVersion = sysVersion.substring(0,sysVersion.indexOf("#1"))
    
    //Write osInfo node
    systemStatus.push(osInfo:[
        release:sysVersion.trim(),
        uptime:[
            seconds:(int)Double.parseDouble(uptime),
            text:millisecondsToLongString((long)(Double.parseDouble(uptime)*1000))
        ]
    ])
    
    
    /***************************/
    //    hardwareInfo - Node 
    /***************************/
    
    //Get system memory
    File fMeminfo = new File("/proc/meminfo")
    def memInfo = fMeminfo.getText("UTF-8")
    def matcher = memInfo =~ /(?s)MemTotal: +?(?<memtotal>\d+) kB\s+MemFree: +?(?<memfree>\d+) kB\s+MemAvailable: +?(?<memavailable>\d+) kB\s+.+SwapTotal: +?(?<swaptotal>\d+) kB\s+SwapFree: +?(?<swapfree>\d+) kB\s+/
    matcher.find()
    def memTotalKb = Long.parseLong(matcher.group('memtotal'))
    def memTotalHu = humanReadableByteCount(memTotalKb*1024)
    def memfreeKb = Long.parseLong(matcher.group('memfree'))
    def memfreeHu = humanReadableByteCount(memfreeKb*1024)
    def memavailableKb = Long.parseLong(matcher.group('memavailable'))
    def memavailableHu = humanReadableByteCount(memavailableKb*1024)
    def memUsedKb = memTotalKb - memavailableKb
    def memUsedHu = humanReadableByteCount(memUsedKb*1024)
    def swapTotalKb = Long.parseLong(matcher.group('swaptotal'))
    def swapTotalHu = humanReadableByteCount(swapTotalKb*1024)
    def swapFreeKb = Long.parseLong(matcher.group('swapfree'))
    def swapFreeHu = humanReadableByteCount(swapFreeKb*1024)
    def swapUsedKb = swapTotalKb - swapFreeKb
    def swapUsedHu = humanReadableByteCount(swapUsedKb*1024)
   
    
    //Get processor-model
    File fCpuInfo = new File("/proc/cpuinfo")
    cpuInfo = fCpuInfo.getText("UTF-8")
    matcher = cpuInfo =~ /(?s)vendor_id\s+?:\s?(?<vendorid>[^\n]+)\n.*?cpu family\s+?:\s?(?<cpufamilyid>[^\n]+)\n.*?model\s+?:\s?(?<modelid>[^\n]+)\n.*?model name\s+?:\s?(?<modelname>[^\n]+)\n.*?cpu MHz\s+?:\s?(?<mhz>[^\n]+)\n.*?cache size\s+?:\s?(?<cacheKb>[^\n]+)\n.*?cpu cores\s+?:\s?(?<cores>[^\n]+)\n/
    def cpuDetails = []
    while (matcher.find()){
	    cpuDetails.push([
	        modelName:matcher.group('modelname'),    
	        vendorId:matcher.group('vendorid'),
	        cpuFamilyId:matcher.group('cpufamilyid'),
	        cpuModelId:matcher.group('modelid'),
	        frequencyMhz:matcher.group('mhz'),
	        cacheSizeKb:matcher.group('cacheKb'),
	        cores:matcher.group('cores'),
	    ])
    }
    def totalCores = 0
    cpuDetails.each { totalCores += Integer.parseInt(it.cores) }
    
    //Get system load
    File fLoad = new File("/proc/loadavg")
    def loadAvg = fLoad.getText("UTF-8").trim().split()
    
    //Get current load in percentage (over last second)
    //See: https://github.com/Leo-G/DevopsWiki/wiki/How-Linux-CPU-Usage-Time-and-Percentage-is-calculated
    File fStat1 = new File("/proc/stat")
    def stat1 = fStat1.getText("UTF-8")
    sleep(message.getProperties().get("cpuMeasureTimeMs").toInteger())
    File fStat2 = new File("/proc/stat")
    def stat2 = fStat2.getText("UTF-8")
    matcher = stat1 =~ /(?s)cpu\s+(?<user>\d+)\s+(?<nice>\d+)\s+(?<system>\d+)\s+(?<idle>\d+)\s+(?<iowait>\d+)\s+(?<irq>\d+)\s+(?<softirq>\d+)\s+(?<steal>\d+)/
    matcher.find()
    def matcher2 = stat2 =~ /(?s)cpu\s+(?<user>\d+)\s+(?<nice>\d+)\s+(?<system>\d+)\s+(?<idle>\d+)\s+(?<iowait>\d+)\s+(?<irq>\d+)\s+(?<softirq>\d+)\s+(?<steal>\d+)/
    matcher2.find()
    
    def totalCpu = matcher2.group('user').toDouble()+matcher2.group('nice').toDouble()+matcher2.group('system').toDouble()+matcher2.group('idle').toDouble()+matcher2.group('iowait').toDouble()+matcher2.group('irq').toDouble()+matcher2.group('softirq').toDouble()+matcher2.group('steal').toDouble()-matcher.group('user').toDouble()-matcher.group('nice').toDouble()-matcher.group('system').toDouble()-matcher.group('idle').toDouble()-matcher.group('iowait').toDouble()-matcher.group('irq').toDouble()-matcher.group('softirq').toDouble()-matcher.group('steal').toDouble()
    def totalIdle = matcher2.group('idle').toDouble()+matcher2.group('iowait').toDouble()-matcher.group('idle').toDouble()-matcher.group('iowait').toDouble()
    def totalUsage = totalCpu-totalIdle
    def totalPercentage = totalUsage/totalCpu*100
    def totalPercentageRounded = Math.round(totalPercentage)
    def totalPercentageIowait = (matcher2.group('iowait').toDouble()-matcher.group('iowait').toDouble())/totalCpu*100
    def totalPercentageIowaitRounded = Math.round(totalPercentageIowait)
    
    //Get diskspace information
    //Drive: /
    def totalSpaceBytes1 = new File("/").getTotalSpace()
    def useableSpaceBytes1 = new File("/").getUsableSpace()
    def usedSpaceBytes1 = totalSpaceBytes1-useableSpaceBytes1
    def totalSpaceHu1 = humanReadableByteCount(totalSpaceBytes1)
    def useableSpaceHu1 = humanReadableByteCount(useableSpaceBytes1)
    def usedSpaceHu1 = humanReadableByteCount(usedSpaceBytes1)
    
    //Drive: /usr/sap
    def totalSpaceBytes2 = new File("/usr/sap").getTotalSpace()
    def useableSpaceBytes2 = new File("/usr/sap").getUsableSpace()
    def usedSpaceBytes2 = totalSpaceBytes2-useableSpaceBytes2
    def totalSpaceHu2 = humanReadableByteCount(totalSpaceBytes2)
    def useableSpaceHu2 = humanReadableByteCount(useableSpaceBytes2)
    def usedSpaceHu2 = humanReadableByteCount(usedSpaceBytes2)
    
    //Drive: /tmp
    def totalSpaceBytes3 = new File("/tmp").getTotalSpace()
    def useableSpaceBytes3 = new File("/tmp").getUsableSpace()
    def usedSpaceBytes3 = totalSpaceBytes3-useableSpaceBytes3
    def totalSpaceHu3 = humanReadableByteCount(totalSpaceBytes3)
    def useableSpaceHu3 = humanReadableByteCount(useableSpaceBytes3)
    def usedSpaceHu3 = humanReadableByteCount(usedSpaceBytes3)
    
    //Drive: /var
    def totalSpaceBytes4 = new File("/var").getTotalSpace()
    def useableSpaceBytes4 = new File("/var").getUsableSpace()
    def usedSpaceBytes4 = totalSpaceBytes4-useableSpaceBytes4
    def totalSpaceHu4 = humanReadableByteCount(totalSpaceBytes4)
    def useableSpaceHu4 = humanReadableByteCount(useableSpaceBytes4)
    def usedSpaceHu4 = humanReadableByteCount(usedSpaceBytes4)
    
    def diskSpace = []
    diskSpace.push([
                path:"/",
                totalSpaceBytes:totalSpaceBytes1,
                totalSpaceHu:totalSpaceHu1,
                useableSpaceBytes:useableSpaceBytes1,
                useableSpaceHu:useableSpaceHu1,
                usedSpaceBytes:usedSpaceBytes1,
                usedSpaceHu:usedSpaceHu1
    ])
    diskSpace.push([
                path:"/usr/sap",
                totalSpaceBytes:totalSpaceBytes2,
                totalSpaceHu:totalSpaceHu2,
                useableSpaceBytes:useableSpaceBytes2,
                useableSpaceHu:useableSpaceHu2,
                usedSpaceBytes:usedSpaceBytes2,
                usedSpaceHu:usedSpaceHu2
    ])
    diskSpace.push([
                path:"/tmp",
                totalSpaceBytes:totalSpaceBytes3,
                totalSpaceHu:totalSpaceHu3,
                useableSpaceBytes:useableSpaceBytes3,
                useableSpaceHu:useableSpaceHu3,
                usedSpaceBytes:usedSpaceBytes3,
                usedSpaceHu:usedSpaceHu3
    ])
    diskSpace.push([
                path:"/var",
                totalSpaceBytes:totalSpaceBytes4,
                totalSpaceHu:totalSpaceHu4,
                useableSpaceBytes:useableSpaceBytes4,
                useableSpaceHu:useableSpaceHu4,
                usedSpaceBytes:usedSpaceBytes4,
                usedSpaceHu:usedSpaceHu4
    ])
    
    //Write hardwareInfo node
    systemStatus.push(hardwareInfo:[
        systemDisk:diskSpace,
        systemMemory:[
            memTotalKb:memTotalKb,
            memTotalHu:memTotalHu,
            memFreeKb:memfreeKb,
            memFreeHu:memfreeHu,
            memAvailableKb:memavailableKb,
            memAvailableHu:memavailableHu,
            memUsedKb:memUsedKb,
            memUsedHu:memUsedHu,
            swapTotalKb:swapTotalKb,
            swapTotalHu:swapTotalHu,
            swapFreeKb:swapFreeKb,
            swapFreeHu:swapFreeHu,
            swapUsedKb:swapUsedKb,
            swapUsedHu:swapUsedHu
        ],
        cpuLoad:[
            loadAvg:[
                avg1Minute:loadAvg[0],
                avg5Minute:loadAvg[1],
                avg15Minute:loadAvg[2],
            ],
            loadCurrentPerc:totalPercentage,
            loadCurrentPercRounded:totalPercentageRounded,
            iowaitPercentage:totalPercentageIowait,
            iowaitPercentageRounded:totalPercentageIowaitRounded
        ],
        cpuInfo:[
            totalCpus:cpuDetails.size(),
            totalCores:totalCores,
            cpuDetails:cpuDetails
        ]
    ])
    
    
    
    /***************************/
    //     jvmInfo - Node 
    /***************************/
    
    //Get Java VM general information
    def jvmVersion = System.getProperty("java.version")
    def jvmVendor = System.getProperty("java.vendor")
    def jvmVendorUrl = System.getProperty("java.vendor.url")
    def jvmUser = System.getProperty("user.name")
    def jvmUserHomeDir = System.getProperty("user.home")
    def jvmUserWorkingDir = System.getProperty("user.dir")
    //Get Java VM memory
    def runtime = Runtime.getRuntime()
	long jvmMemUsedKb = (runtime.totalMemory()-runtime.freeMemory())/1024L
	def jvmMemUsedHu = humanReadableByteCount(jvmMemUsedKb*1024L)
    long jvmMemFreeKb = runtime.freeMemory()/1024L
    def jvmMemFreeHu = humanReadableByteCount(jvmMemFreeKb*1024L)
    long jvmMemTotalKb = runtime.totalMemory() / 1024L
    def jvmMemTotalHu = humanReadableByteCount(jvmMemTotalKb*1024L)
    long jvmMemMaxKb = runtime.maxMemory()/1024L
	def jvmMemMaxHu = humanReadableByteCount(jvmMemMaxKb*1024L)
	
	//Write jvmInfo node
	systemStatus.push(jvmInfo:[
	    jvmSystemInfo:[
	        jvmVersion:jvmVersion,
	        jvmVendor:jvmVendor,
	        jvmVendorUrl:jvmVendorUrl,
	        userInfo:[
	            jvmUser:jvmUser,
	            jvmUserHomeDir:jvmUserHomeDir,
	            jvmUserWorkingDir:jvmUserWorkingDir
	        ]
	    ],
	    jvmMemory:[
            jvmMemUsedKb:jvmMemUsedKb,
            jvmMemUsedHu:jvmMemUsedHu,
            jvmMemFreeKb:jvmMemFreeKb,
            jvmMemFreeHu:jvmMemFreeHu,
            jvmMemTotalKb:jvmMemTotalKb,
            jvmMemTotalHu:jvmMemTotalHu,
            jvmMemMaxKb:jvmMemMaxKb,
            jvmMemMaxHu:jvmMemMaxHu
        ]
    ])
    
    
    /***************************/
    //     cpiInfo - Node 
    /***************************/
    
    //Get message statistics (summarize amount of messages per hour over 48h & 30d)
    def msgCountList = message.getProperties().get('msgCountList')

    //Read SAP CPI versionString cpiVersion = ""
    def bundleContext = FrameworkUtil.getBundle(Class.forName("com.sap.gateway.ip.core.customdev.util.Message")).getBundleContext()
    def profileBundle = bundleContext.getBundles().find(){ it.getSymbolicName() == "com.sap.it.node.stack.profile" }
    def cpiVersion = profileBundle.getVersion().toString()
    def cpiModules = []
    bundleContext.getBundles().each {
        cpiModules.push([
            name:it.getSymbolicName().toString(),
            version:it.getVersion().toString()
        ])
    }
    
    //Get logfiles
    def logFileList = []
    if (scopeLogAndFileAccess){
        File dir = new File("/usr/sap/ljs/log")
        dir.listFiles().sort{ it.name }.each {
            if (it.isFile()) {
                logFileList.push([
                    name:it.name,
                    localPath:"/usr/sap/ljs/log/${it.name}",
                    externalPath:"${message.getHeaders().get('CamelHttpUrl').replace('/status','')}/view/usr/sap/ljs/log/${it.name}"
                ])
            }
        }
    }
    
    //Get XSLT engine values
    def xsltValueList = new XmlSlurper().parseText(message.getProperties().get('cacheXSLT'))
    
    
    //Write cpiInfo node
	systemStatus.push(cpiInfo:[
	    currentCaller:[
	        userName:userName,
	        cloudPlatformRoles:message.getProperties().get('userRoles'),
	        dashboardScopes:[
	            generalAccess:scopeGeneralAccess,
	            logAndFileAccess:scopeLogAndFileAccess,
	            passwordAccess:scopePasswordAccess,
				shellAccess:scopeShellAccess
	        ]
	    ],
	    messageCountInfo:msgCountList,
	    versionInfo:[
	        cpiVersion:cpiVersion,
	        groovyVersion:GroovySystem.version.toString(),
	        moduleVersions:cpiModules
	    ],
	    logfiles:logFileList,
	    xsltEngineInfo:[
	        version:xsltValueList.version.text(),
	        productName:xsltValueList.'product-name'.text(),
	        productVersion:xsltValueList.'product-version'.text(),
	        vendor:xsltValueList.vendor.text(),
	        vendorUrl:xsltValueList.'vendor-url'.text()
	    ]
    ])
    
    
    //Performance log
    def start = (long)message.getProperties().get('perfLogStart');
    def elapsed = System.currentTimeMillis()-start;
    systemStatus.push(queryRuntime:"${elapsed.toString()} ms")
    
    //Set response
    message.getHeaders().put("Content-Type", "application/json")
    def outJson = JsonOutput.toJson(systemStatus)
    message.setBody(outJson)
    
    return message;
}

public static String humanReadableByteCount(long v) {
    if (v < 1024) return v + " B";
    int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
    return String.format("%.1f %sB", (double)v / (1L << (z*10)), " KMGTPE".charAt(z));
}


def String millisecondsToLongString(long milliseconds){
	long dy = TimeUnit.MILLISECONDS.toDays(milliseconds);
	long hr = TimeUnit.MILLISECONDS.toHours(milliseconds) - TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds));
	long min = TimeUnit.MILLISECONDS.toMinutes(milliseconds) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds));
	long sec = TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds));
	long ms = TimeUnit.MILLISECONDS.toMillis(milliseconds) - TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(milliseconds));
	return String.format("%d Days %d Hours %d Minutes %d Seconds %d Milliseconds", dy, hr, min, sec, ms);
}
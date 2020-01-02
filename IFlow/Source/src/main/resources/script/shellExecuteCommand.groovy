import com.sap.gateway.ip.core.customdev.util.Message
import groovy.io.GroovyPrintStream
import org.apache.karaf.shell.api.console.Session
import org.apache.karaf.shell.api.console.SessionFactory
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import groovy.json.*
import org.fusesource.jansi.*

Message processData(Message message) {
  
    //Get command and build SessionFactory/Shell
    String command = message.getProperty("shellCommand")
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    BundleContext context = FrameworkUtil.getBundle(Message.class).bundleContext
    SessionFactory sessionFactory = (SessionFactory) context.getService(context.getServiceReference(SessionFactory.class))

	def hOut = new HtmlAnsiOutputStream(out)
	def hErr = new HtmlAnsiOutputStream(err)
	Session session = sessionFactory.create(new ByteArrayInputStream(), new GroovyPrintStream(hOut, true, "UTF-8"), new GroovyPrintStream(hErr, true, "UTF-8"))
    
    //Execute command and parse response
    session.execute(command)
    def cmdResult = out.toString()
    def errResult = err.toString()

    //Remove ANSI escapes codes which may be uncovered by Jansi from responses
	cmdResult = decolorizeString(cmdResult)
	errResult = decolorizeString(errResult)
        
    //Format the output
	cmdResult = cmdResult.replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").replace("\r\n", "\n").replace("\n", "<br/>").replace(" ", "&nbsp;")
    errResult = "<span style='color:red;'>"+errResult.replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").replace("\r\n", "\n").replace("\n", "<br/>").replace(" ", "&nbsp;")+"</span>"
	
	//Build datatype and set JSON header
	def result = [
		outStream: cmdResult,
		errStream: errResult
	]
	def response = JsonOutput.toJson(result)
	message.setBody(response)
	message.setHeader('Content-Type', 'application/json')
   
    session.close()
    return message
}

private decolorizeString(String inStr){
    return inStr.replaceAll("\\u001b\\[[^m]+?m",'')
}
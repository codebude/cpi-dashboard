import com.sap.gateway.ip.core.customdev.util.Message
import java.util.HashMap
import java.text.SimpleDateFormat

def Message processData(Message message) {
    
    def fPath = message.getProperties().get('filePath') as String
    if (!fPath.startsWith('/')){
        fPath = "/${fPath}"
    }
    
    StringBuilder strBuilder = new StringBuilder()
    File file = new File(fPath)
    message.setBody(file.bytes)
    
    if (message.getHeaders().get('CamelHttpQuery') == "download"){
        message.setHeader('Content-Disposition',"attachment; filename=${file.getName()}")
    }
    
    return message
}


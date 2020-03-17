package net.unicon.iam.motd

import groovyx.net.http.ContentTypes
import groovyx.net.http.HttpBuilder
import groovyx.net.http.optional.Download
import org.apache.velocity.Template
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import javax.mail.Address
import javax.mail.Message
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sns.model.PublishResult
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.InstanceProfileCredentialsProvider


/**
 *  MOTD Message Properties Updater by Unicon IAM
 */
public class MOTDUpdater implements Runnable {

    private final LogN log = new LogN()
    private final Properties config

    private String existingMOTDLocation
    private Properties existingMOTD
    private DateTimeFormatter dateTimeFormatter
    private Session mailSession
    private AmazonSNS snsClient
    private Template mailTemplate
    private String newError
    private String oldError


    MOTDUpdater(final URL configProps) {
        if (configProps) {
            log.info("MOTD: Retrieving MOTD Config...")
            config = retrieveAndParsePropertiesFile("Config Properties", configProps.getPath())
            //Retrieve existing motd.properties configuration
        } else {
            log.warn("MOTD: No Configuration found!")
        }
    }

    void run() {
        log.info("MOTD: Processing started.")
        if (!config?.isEmpty()) {
            newError = ""
            dateTimeFormatter = DateTimeFormatter.ofPattern(
                    config.getProperty("motd.date.format").toString().trim()).withZone(
                    ZoneId.of(config.getProperty("motd.date.timezone").toString().trim()))

            existingMOTDLocation = config.getProperty("motd.existing.motdPropsLocation").toString().trim()

            log.info("MOTD: Retrieving Existing MOTD from file location...")
            existingMOTD = retrieveAndParsePropertiesFile("Existing MOTD Message", existingMOTDLocation)

            log.info("MOTD: Retrieving MOTD from Remote URL...")
            def remoteMOTDMessage = retrieveAndParseRemoteMOTD() //Retrieve remote MOTD from endpoint

            processMOTD(existingMOTD, remoteMOTDMessage) //Main Logic of MOTD Feature

            saveErrorState() //saves error only if error occurred
            log.info("MOTD: Processing complete.")

        } else {
            log.error("MOTD: ERROR couldn't retrieve or process MOTD Configuration!")
        }

        log.info("MOTD: Processing complete see above for success or errors.")
    }

    private def processMOTD(def existingMsg, def newMsg) {
        log.debug("MOTD: Processing MOTD...")

        if (newMsg && !newMsg.isEmpty()) {
            if (isMOTDFormattedCorrectly(newMsg)) {
                def valid = isMOTDValid(newMsg)

                if (valid >= 0) {
                    if (existingMsg && !existingMsg.isEmpty()) {
                        if (compareMOTD(existingMsg, newMsg.get(valid))) {
                            log.info("MOTD: New MOTD is the same as the existing. No change needed, doing nothing.")

                        } else {
                            log.info("MOTD: New MOTD found is different and valid. Publishing new MOTD.")
                            publishMOTD(newMsg.get(valid))
                        }

                    } else {
                        log.info("MOTD: New MOTD message found was valid and there is no existing MOTD. Publishing new MOTD.")
                        publishMOTD(newMsg.get(valid))
                    }

                } else {
                    log.info("MOTD: New MOTD messages found aren't valid and based on configuration removing existing")
                    removeMOTD(existingMsg)
                }
            } else {
                log.info("MOTD: Invalid format encountered! Sending email as required!")
                sendErrorEmail("Invalid MOTD format encountered. No new MOTD can be published until it's fixed!", false)
            }
        } else {
            log.info("MOTD: Removing existing MOTD if exists otherwise doing nothing because remote MOTD wasn't found or empty...")
            removeMOTD(existingMsg)
        }
    }

    private def retrieveAndParsePropertiesFile (final String name, final String resource) {
        try {
            log.trace("MOTD: Looking for " + name + " file... ")
            def returnProps = new Properties()
            def propsFile = new File(resource)

            if (!propsFile.exists()) {
                log.trace("MOTD: " + name + " file doesn't exist!")

            } else if (propsFile.getText('UTF-8').isEmpty()) {
                log.trace("MOTD: " + name + " file empty!")

            } else {
                log.trace("MOTD: " + name + " found!")
                propsFile.withInputStream {
                    returnProps.load(it)
                }
            }

            log.trace("MOTD: " + name + " contents [" + returnProps.toString() + "].")

            if (returnProps.containsKey("motd.error")) {
                log.trace("MOTD: Found existing error state.")
                oldError = returnProps.getProperty("motd.error").toString().trim() //save error state
                returnProps.remove("motd.error")
            } else {
                oldError = ""
            }

            return returnProps

        } catch (Exception e) {
            log.warn("MOTD: Error couldn't find/parse " + name + " file! Found error: ", e)
            return null
        }
    }

    private def retrieveAndParseRemoteMOTD() {
        try {
            log.trace("MOTD: Retrieving MOTD from url [" + config.getProperty("motd.remote.url") +
                    "] with content type [" + config.getProperty("motd.remote.contenttype") + "] and auth required: [" +
                    config.getProperty("motd.remote.authRequired") + "]... ")

            def motd
            final String uri = config.getProperty("motd.remote.url").toString().trim()
            def type =  config.getProperty("motd.remote.contenttype").toString().trim()
            def url = HttpBuilder.configure { request.uri = uri }

            if ( config.getProperty("motd.remote.authRequired") == true) {
                log.debug("MTOD: Setting Basic Auth for Remote Url.")
                url.configure {
                    request.auth.basic(config.getPropertyProperty("motd.remote.username").toString().trim(),
                            config.getProperty("motd.remote.password").toString().trim())
                }
            }

            if (type.equalsIgnoreCase("json")) {
                log.debug("MOTD: Attempting to retrieve MOTD as JSON...")
                motd = url.get()

            } else if (type.equalsIgnoreCase("html") || type.equalsIgnoreCase("text")) {
                log.debug("MOTD: Attempting to retrieve MOTD as html/text...")
                motd = parseTextMOTD(url.get())

            } else if (type.equalsIgnoreCase("textfile")) {
                log.debug("MOTD: Attempting to retrieve MOTD as text file")
                def stream = url.get {
                    Download.toStream(delegate, ContentTypes.TEXT.toString(), new ByteArrayOutputStream())
                }

                if (stream) {
                    log.debug("MOTD: Remote MOTD file found attempting to parse...")
                    motd = parseTextMOTD(new ByteArrayInputStream(stream).getText(StandardCharsets.UTF_8.name()).trim())
                } else {
                    log.debug("MOTD: Remote MOTD file doesn't exist.")
                    motd = null
                }

            } else {
                log.error("MOTD: Error can't process content of type [" + type + "]!")
            }

            log.debug("MOTD: Remote MOTD found and completed parsing.")
            log.trace("MOTD: The Remote MOTD found was [" + motd.toString() + "].")
            return motd

        } catch (Exception e) {
            log.error("MOTD: Error processing remote MOTD endpoint! Found error: ", e)
            sendErrorEmail("Error while retrieving MOTD from remote url", false)
            return null
        }
    }

    private def parseTextMOTD(final String text) {
        def messagesFormatted = [[:]]
        def messageCount = [:]

        (config.getProperty("motd.keys").toString().tokenize(",")).each {value ->
            messageCount.put(value.trim().toLowerCase(), 0)
        }

        log.debug("MOTD: Converting remote MOTD text into comparison format...")
        log.trace("MOTD: Remote MOTD raw=[" + text + "].")
        def previousHeader;
        text.tokenize(config.getProperty("motd.line.separator")).each { line->
            try {
                if (line.indexOf("=") > 0) {
                    def header = line.substring(0, line.indexOf("=", 0)).trim().toLowerCase()

                    if (messageCount.containsKey(header)) {
                        def count = messageCount[header]
                        if (messagesFormatted.size() <= count) {
                            messagesFormatted.add([:])
                        }

                        def value = line.substring(line.indexOf("=", 0), line.length());
                        log.trace("MOTD: Found header [" + header + "] with value [" + value + "] for " + messageCount[header] + " message.")
                        messagesFormatted.get(messageCount[header]).put(header, removeFormatting(value))
                        previousHeader = header
                        messageCount[header] = messageCount[header] += 1

                    } else {
                        log.trace("MOTD: Unrecognized header [" + header + "] found! This will not be included in the MOTD!")
                    }
                } else if (previousHeader && config.getProperty("motd.allowMultilineText").trim().equalsIgnoreCase("true")) {
                    def count = messageCount[previousHeader] - 1
                    messagesFormatted.get(count).put(previousHeader, messagesFormatted.get(count).get(previousHeader) + removeFormatting(line))
                    log.trace("MOTD: Adding to previous header " + previousHeader + "with value [" + line + "] to messaage + " + count + ".")
                }

            } catch (Exception e) {
                log.debug("MOTD: Error parsing remote MOTD. Error found ", e)
                sendErrorEmail("Error parsing remote MOTD, format is likely invalid.", false)
            }
        }

        return messagesFormatted
    }

    private def removeFormatting(final String text) {
        def formattedText = text.trim()

        if (config.getProperty("motd.removeFormatting").trim().toBoolean()) {
            if (formattedText.startsWith("=\"") && formattedText.endsWith("\"")) {
                formattedText = formattedText.substring(2, formattedText.size() - 1).replaceAll("//R", " ")
            } else if (text.startsWith("=")) {
                formattedText = formattedText.substring(1, formattedText.size()).replaceAll("//R", "")
            }
        }
        return formattedText
    }

    private def isMOTDFormattedCorrectly(def motd) {
        def result
        log.trace("MOTD: Checking MOTD for validity...")

        try {
            if (motd) {
                motd.eachWithIndex { message, index ->
                    result = true
                    (config.getProperty("motd.keys").toString().tokenize(",")).each { key ->
                        if( !message.containsKey(key.trim().toLowerCase()) ) {
                            log.trace("MOTD: MOTD doesn't contain expected header [" + key + "].")
                            result = false
                            return
                        }
                    }

                    if (result) {
                        try {
                            ZonedDateTime.parse(message.get(config.getProperty("motd.keys.startDate").toString().trim().toLowerCase()), dateTimeFormatter)
                            ZonedDateTime.parse(message.get(config.getProperty("motd.keys.endDate").toString().trim().toLowerCase()), dateTimeFormatter)
                        } catch (ParseException pe) {
                            log.debug("MOTD: Incorrect MOTD format: Couldn't parse date for message " + index + " with format [" + config.getProperty("motd.date.timezone") + "]!")
                            result = false
                        }
                    } else {
                        log.debug("MOTD: Incorrect MOTD format: Not all of the required keys are present for message " + index + "!")
                        result = false
                    }
                }
            }
        } catch (Exception e) {
            log.debug("MOTD: Error occurred while checking for incorrect MOTD format! Found error: " + e)
            return false
        }

        log.debug("MOTD: Validity check returning [" + result + "]")
        return result
    }

    private def isMOTDValid(def motd) {
        log.trace("MOTD: Checking for MOTD date window...")
        def result = -1

        try {
            def now = ZonedDateTime.now(ZoneId.of(config.getProperty("motd.date.timezone")))
            motd.eachWithIndex { message, index ->
                def start =  ZonedDateTime.parse(message.get(config.getProperty("motd.keys.startDate").trim().toLowerCase()), dateTimeFormatter)
                def end = ZonedDateTime.parse(message.get(config.getProperty("motd.keys.endDate").trim().toLowerCase()), dateTimeFormatter)

                if ( start.isBefore(now) && end.isAfter(now) ) {
                    log.trace("MOTD: The times for message " + index + " [" + start.format(dateTimeFormatter) + "] and [" + end.format(dateTimeFormatter) + "] fall between now [" + now.format(dateTimeFormatter) + "].")
                    result = index
                    return

                } else {
                    log.trace("MOTD: The date for message " + index + " is *not* within the window and is *not* valid.")
                }
            }

        } catch (Exception e) {
            log.error("MOTD: Error while checking MOTD date window. Found error: ", e)
            sendErrorEmail("Error while checking the MOTD date window. Date is either improper or doesn't match format.", false)
        }

        return result
    }

    private def compareMOTD(def oldMsg, def possiblyNewMsg) {
        log.trace("MOTD: Comparing New MOTD to existing...")
        def result = true //safe value, we want to do nothing if error

        try {
            possiblyNewMsg.each { key, value ->
                if (!oldMsg.containsKey(("motd." + key))) {
                    result = false
                } else if (!oldMsg.getProperty(("motd." + key)).trim().equalsIgnoreCase(value.toString())) {
                    result = false
                }
            }
        } catch (Exception e) {
            log.error("MOTD: Error while comparing MOTDs. Error found: " + e)
            sendErrorEmail("Error while comparing MOTDs", true)
        }

        log.debug("MOTD: Comparision is " + result)
        return result
    }

    private void removeMOTD(def existingMessage) {
        if (existingMessage && !existingMessage.isEmpty()) {

            if ( config.getProperty("motd.existing.removeWhen").trim().equalsIgnoreCase("existingIsExpired") ) {
                def end = ZonedDateTime.parse(existingMessage.get(config.getProperty("motd.keys.endDate").trim().toLowerCase()), dateTimeFormatter)

                if ( !end.isAfter(ZonedDateTime.now(ZoneId.of(config.getProperty("motd.date.timezone")))) ) {
                    //do nothing as existing message hasn't expired yet
                    return
                }
            }

            log.debug('MOTD: Removing existing MOTD!')
            publishMOTD(null)
        }
        //nothing to do if existing is empty/doesn't exist
    }

    private void publishMOTD(def newMessage) {
        try {
            log.trace("MOTD: Printing [" + newMessage + "] to existing MOTD properties file.")

            if (newMessage && !newMessage.isEmpty()) {
                log.debug("MOTD: Publishing populated MOTD!")
                new File(existingMOTDLocation).withWriter(StandardCharsets.UTF_8.name()) { writer ->
                    //writer.writeLine ''
                    newMessage.each { key, value ->
                        if (!key.equalsIgnoreCase(config.getProperty("motd.keys.startDate")) && !key.equalsIgnoreCase( config.getProperty("motd.keys.endDate"))) {
                            writer.writeLine "motd.$key=$value"    //note not trusting normal properties writer here since it could escape characters we need. TODO look into compatiblity issues
                        } else {
                            writer.writeLine "motd.$key=$value"
                        }
                    }
                }
            } else {
                log.debug("MOTD: Publishing Empty MOTD!")
                new File(existingMOTDLocation).withWriter(StandardCharsets.UTF_8.name()) { writer ->
                    writer.write("")
                }
            }

        } catch (Exception e) {
            log.error("MOTD: Error saving/writing to MOTD Message Properties file! Found error: " + e)
            sendErrorEmail("Error writing to MOTD properties file!", true)
        }
    }

    private void sendErrorEmail(def message, def adminOnly) {
        def topic = config.containsKey("motd.mail.sns.topic") ? config.getProperty("motd.mail.sns.topic").toString().trim() : false
        if (shouldSendEmail()) {
            if (topic) {
                sendEmailUsingSNSTopic(message, adminOnly)
            } else {
                sendEmailUsingMailServer(message, adminOnly)
            }
        } else {
            log.debug("MOTD: Not sending error email due to error email throttling requirement.")
        }
    }

    private boolean shouldSendEmail() {
        log.trace("MOTD: Checking if error email should be sent per throttling requirements.")
        def zoneId = ZoneId.of(config.getProperty("motd.date.timezone").toString().trim());

        try {
            if (oldError && oldError.contains(",")) {
                log.trace("MOTD: Found existing error state [" + oldError + "].")
                def errorCountAndTime = oldError.toString().tokenize(",")
                def thresholdCount =  Integer.parseInt(config.getProperty("motd.error.throttle.threshold").toString().trim())
                def errorCount = Integer.parseInt(errorCountAndTime[0].replace("error=", ""))
                def errorTime = ZonedDateTime.parse(errorCountAndTime[1], dateTimeFormatter)
                def hours = Long.parseLong(config.getProperty("motd.error.throttle.hours").toString().trim())

                log.trace("MOTD: Comparing threshold count [" + thresholdCount + "] with saved value [" + errorCount + "]...")
                if (errorCount > thresholdCount) {
                    def nowMinusHours = ZonedDateTime.now(zoneId).minusHours(hours)
                    log.trace("MOTD: Comparing error time [" + errorTime.format(dateTimeFormatter) + "] to [" + nowMinusHours + "]...")

                    if (errorTime.isAfter(nowMinusHours)) {
                        log.trace("MOTD: Following threshold and only incrementing error count, no email will be sent.")
                        newError = "motd.error=" + (errorCount+1) + "," + errorTime.format(dateTimeFormatter)
                        return false
                    } else {
                        log.debug("MOTD: Time reached, starting over error threshold and sending emails again!")
                        newError = "motd.error=0," + errorTime.format(dateTimeFormatter)
                    }

                } else {
                    log.trace("MOTD: Threshold is below set value sending email and incrementing error count.")
                    newError = "motd.error=" + (errorCount+1) + "," + errorTime.format(dateTimeFormatter)
                    return true
                }
            } else {
                newError = "motd.error=0," + ZonedDateTime.now().format(dateTimeFormatter)
                return true
            }
        } catch (Exception e) {
            log.debug("MOTD: Error checking error throttling state. Error found: ", e)
            return true
        }
    }

    private void saveErrorState() {
        try {
            if (!oldError?.isEmpty() && !newError?.isEmpty()) {
                log.trace("MOTD: Existing error [" + oldError + "] and new error [" + newError + "]. Updating error state/count.")
                def msgFile = new File(existingMOTDLocation)
                def text = msgFile.text
                msgFile.write(text.replaceAll(("motd.error=" + oldError),newError))  //update error message

            } else if (!newError?.isEmpty()) {
                log.trace("MOTD: Saving new error state/count.")
                new File(existingMOTDLocation).withWriterAppend(StandardCharsets.UTF_8.name()) { out ->
                    //out.write(System.lineSeparator() + newError)
                    out.write(newError)  //write a new error message
                }
            } else if (!oldError?.isEmpty()) {
                log.trace("MOTD: Removing error state/count.")
                def msgFile = new File(existingMOTDLocation)
                def toKeep = []
                def shouldWrite = false

                msgFile.eachLine { line ->
                    if (!line.contains("motd.error")) {
                        toKeep.add(line)
                    } else {
                        shouldWrite = true
                    }
                }

                if (shouldWrite) {
                    msgFile.withWriter { out ->
                        toKeep.each { out.println(it) }  //remove error(s) from file
                    }
                }

            } else{
                //no errors do nothing
            }

        } catch (Exception e) {
            log.trace("MOTD: Error saving error state. Error found: ", e)
        }
    }

    private void sendEmailUsingMailServer(def message, def adminOnly) {
        log.trace("MOTD: Sending email using mail server...")
        setupMailServerSession()

        try {
            final Message emailToSend = new MimeMessage(mailSession)
            emailToSend.setFrom(new InternetAddress(config.getProperty("motd.mail.fromAddress").toString().trim()))
            emailToSend.setSubject(config.getProperty("motd.mail.subject").toString().trim())

            if (adminOnly == false) {
                def toAddresses = config.getProperty("motd.mail.toAddresses").toString().tokenize(",")
                final Address[] toInetAddresses = new InetAddress[toAddresses.size()]
                        .eachWithIndex { val, idx ->
                            (toInetAddresses[idx] = new InternetAddress(val))
                        }
                emailToSend.addRecipients(Message.RecipientType.TO, toInetAddresses)
                emailToSend.addRecipients(Message.RecipientType.BCC, new InternetAddress(config.getProperty("motd.mail.adminAddress").toString().trim()))
            } else {
                emailToSend.addRecipients(Message.RecipientType.TO, new InternetAddress(config.getProperty("motd.mail.adminAddress").toString().trim()))
            }

            if (mailTemplate) {
                def vc = new VelocityContext()
                def writer = new StringWriter()
                vc.put("issue", message)
                mailTemplate.merge(vc, writer)
                emailToSend.setContent(writer.toString(), "text/html;charset=utf-8")

            } else {
                emailToSend.setContent("Error occurred during MOTD processing! Error found [" + message + "].", "text/html;charset=utf-8")
            }

            Transport.send(emailToSend, config.getProperty("motd.mail.server.user").toString().trim(), config.getProperty("motd.mail.server.password").toString().trim())

        } catch (Exception e) {
            log.error("MOTD: Error creating or sending email to mail server! Error found: ", e)
        }
    }

    private void setupMailServerSession() {
        if (!mailSession) {
            try {
                log.trace("MOTD: Setting up mail server")

                def mailProperties = new Properties()
                mailProperties.put("mail.smtp.host", config.getProperty("motd.mail.server.host").toString().trim())
                mailProperties.put("mail.smtp.port", config.getProperty("motd.mail.server.port").toString().trim())
                mailProperties.put("mail.smtp.socketFactory.port", config.getProperty("motd.mail.server.port").toString().trim())
                mailProperties.put("mail.smtp.socketFactory.fallback", "false")

                def ssl = config.getProperty("motd.mail.server.ssl").toString().trim()
                if (!ssl || ssl.isEmpty()) {
                    mailProperties.put("mail.smtp.starttls.enable", "false")
                } else if (ssl.equalsIgnoreCase("ssl")) {
                    mailProperties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    mailProperties.put("mail.smtp.starttls.enable", "true")
                } else if (ssl.equalsIgnoreCase("tls")) {
                    mailProperties.put("mail.smtp.starttls.enable", "true")
                }

                if (config.containsKey("motd.mail.server.user")) {
                    mailProperties.put("mail.smtp.user", config.getProperty("motd.mail.server.user").toString().trim())
                    mailProperties.put("mail.smtp.auth", "true")
                }

                mailSession = Session.getDefaultInstance(mailProperties)

                if (!mailTemplate) {
                    try {
                        def mt = new Properties();
                        mt.setProperty("resource.loader", "file")
                        mt.setProperty("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader")
                        mt.setProperty("file.resource.loader.path", config.getProperty("motd.mail.template").toString().trim())
                        mt.setProperty("file.resource.loader.cache", "false")
                        mt.setProperty("file.resource.loader.modificationCheckInterval", "86400")
                        Velocity.init(mt)
                        mailTemplate = Velocity.getTemplate(config.getProperty("motd.mail.template").toString().trim())

                    } catch (Exception e) {
                        log.debug("MOTD: Error setting up velocity template. Error found: ", e)
                    }
                }

            } catch (Exception e) {
                log.error("MOTD: Error setting up Email Server! Error found: ", e)
            }
        }
    }
    private void sendEmailUsingSNSTopic(def message, def adminOnly) {
        if (!adminOnly) {
            log.trace("MOTD: Sending email using SNS Topic")
            setupSNSClient()
            try {
                final PublishRequest request = new PublishRequest()
                        .withTopicArn(config.getProperty("motd.mail.sns.topic").toString().trim())
                        .withMessage(config.getProperty("motd.mail.subject").toString().trim() + "Error occurred during MOTD processing! Error found [" + message + "].")

                final PublishResult snsResult = snsClient.publish(request)
                log.debug("MOTD: Published SNS Message with id: " + snsResult.getMessageId())

            } catch (Exception e) {
                log.error("MOTD: Error creating or sending email to AWS SNS Topic! Error found: ", e)
            }
        }
    }

    private void setupSNSClient() {
        if (!snsClient) {
            log.trace("MOTD: Setting up SNS Client")
            BasicAWSCredentials creds
            AmazonSNS client

            try {
                if (config.containsKey("motd.mail.sns.account") && config.containsKey("motd.mail.sns.account")) {
                    creds = new BasicAWSCredentials(config.getProperty("motd.mail.sns.account").toString().trim(), config.getProperty("motd.mail.sns.secret").toString().trim())
                }

                if (creds) {
                    client = AmazonSNSClient
                            .builder()
                            .withRegion(config.getProperty("motd.mail.sns.region").toString().trim())
                            .withCredentials(new AWSStaticCredentialsProvider(creds))
                            .build()

                } else {
                    client = AmazonSNSClientBuilder
                            .standard()
                            .withRegion(config.getProperty("motd.mail.sns.region").toString().trim())
                            .withCredentials(new InstanceProfileCredentialsProvider(true))
                            .build()
                }

                snsClient = client

            } catch (Exception e) {
                log.error("MOTD: Error setting up SNS Client! Error found: ", e)
            }
        }
    }
}

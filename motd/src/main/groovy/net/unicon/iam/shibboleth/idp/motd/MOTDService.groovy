package net.unicon.iam.shibboleth.idp.motd

import groovyx.net.http.ContentTypes
import groovyx.net.http.HttpBuilder
import groovyx.net.http.optional.Download
import org.apache.velocity.Template
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity

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


/**
 *  Shibboleth IdP MOTD Script by Unicon IAM
 */
public class MOTDService implements Runnable {

    private final String idpMotdRemoveExisting="any"
    private final String[] idpMotdExpectedKeys=["Header","Body","Effective","expiration", "BOGUS"]
    private final String idpMotdStartDateKey="Effective"
    private final String idpMotdEndDateKey="Expiration"
    private final String idpMotdDateFormat="yyyy-MM-dd HH:mm"
    private final String idpMotdDateTimeZone="UTC"
    private final String idpMotdSeparator="\n"
    private final String idpMotdAcceptMultiSeparateValue="true"
    private final String idpMotdExistingLocation="/home/paul/Wrkspce/iam/motd/motd/motd-message.properties"
    private final String idpMotdRemoteUrl="http://localhost:8084/motd/motdtxtfile.php"
    private final String idpMotdRemoteContentType="textfile"
    private final String idpMotdRemoteAuthnRequired="false"
    private final String idpMotdRemoteUsername="joe"
    private final String idpMotdRemotePassword="smith"
    private final String idpMotdMailServerUrl="https://localhost"
    private final String idpMotdMailServerPort="25"
    private final String idpMotdMailServerUser="jake"
    private final String idpMotdMailServerPassword="statefarm"
    private final String idpMotdMailAdminAddress="iamcloud@example.org"
    private final String[] idpMotdMailToAddresses=["joeblow@example.org", "someoneelse@example.org"]
    private final String idpMotdMailFromAddress="iamcloudfrom@example.org"
    private final String idpMotdMailSubject="MOTD Issue Encountered"
    private final String idpMotdMailTemplate="templates/motd-email.vm"
    private final String idpMotdErrorThrottleHours="4"
    private final String idpMotdErrorThrottleThreshold="2"

    private Session mailSession;
    private Template mailTemplate;
    private DateTimeFormatter dateTimeFormatter;
    private static volatile String savedError;


    private final LogN log = new LogN()

    void run() {
        log.info("MOTD: Starting...")
        if (!dateTimeFormatter) {
            dateTimeFormatter = DateTimeFormatter.ofPattern(idpMotdDateFormat).withZone(ZoneId.of(idpMotdDateTimeZone))
        }

        if (idpMotdRemoteUrl && idpMotdExpectedKeys && idpMotdExistingLocation && idpMotdMailTemplate) {
            log.info("MOTD: Retrieving MOTD from Remote URL...")
            def remoteMOTDMessage = retrieveAndParseRemoteMOTD() //Retrieve remote MOTD from endpoint

            log.info("MOTD: Retrieving existing MOTD Message from [" + idpMotdExistingLocation + "]...")
            def existingMOTDMessage = retrieveAndParsePropertiesFile("Message Properties") //Retrieve existing MOTD file

            processMOTD(existingMOTDMessage, remoteMOTDMessage) //Main Logic of MOTD Feature

        } else {
            log.error("MOTD: ERROR couldn't retrieve or process MOTD Configuration!")
            sendErrorEmail("Couldn't retrieve or process MOTD Configuration!", true)
        }

        log.info("MOTD: Processing complete see above for success or errors.")
    }

    private def processMOTD(def existingMsg, def newMsg) {
        log.debug("MOTD: Processing MOTD...")

        if (newMsg && !newMsg.isEmpty()) {
            if (isMOTDFormattedCorrectly(newMsg)) {
                def valid = isMOTDValid(newMsg)

                if (valid > 0) {
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

    private def retrieveAndParseRemoteMOTD() {
        try {
            log.trace("MOTD: Retrieving MOTD from url [" + idpMotdRemoteUrl +
                    "] with content type [" + idpMotdRemoteContentType + "] and auth required: [" +
                    idpMotdRemoteAuthnRequired + "]... ")

            def motd
            def url = HttpBuilder.configure {request.uri = idpMotdRemoteUrl.toString().trim()}
            def type = idpMotdRemoteContentType.toString().trim()

            if (idpMotdRemoteAuthnRequired == true) {
                log.debug("MTOD: Setting Basic Auth for Remote Url.")
                url.configure {
                    request.auth.basic(idpMotdRemoteUsername.toString().trim(), idpMotdRemotePassword.toString().trim())
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
            sendErrorEmail("Error while retrieving MOTD from remote url", true)
            return null
        }
    }

    private def parseTextMOTD(final String text) {
        def messagesFormatted = [[:]]
        def messageCount = [:]

        idpMotdExpectedKeys.each {value ->
            messageCount.put(value.trim().toLowerCase(), 0)
        }

        log.debug("MOTD: Converting remote MOTD text into comparison format...")
        log.trace("MOTD: Remote MOTD raw=[" + text + "].")
        def previousHeader;
        text.tokenize(idpMotdSeparator).each { line->
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
                } else if (previousHeader && idpMotdAcceptMultiSeparateValue.trim().equalsIgnoreCase("true")) {
                    def count = messageCount[previousHeader] - 1
                    messagesFormatted.get(count).put(previousHeader, messagesFormatted.get(count).get(previousHeader) + removeFormatting(line))
                    log.trace("MOTD: Adding to previous header " + previousHeader + "with value [" + line + "] to messaage + " + count + ".")
                }

            } catch (Exception e) {
                log.debug("MOTD: Error parsing remote MOTD. Error found ", e)
                sendErrorEmail("Error parsing remote MOTD, format is likely invalid.", true)
            }
        }

        return messagesFormatted
    }

    private def removeFormatting(final String text) {
        if (text.startsWith("=\"")) {
            return text.substring(2, text.size())
        } else if (text.startsWith("=")) {
            return text.substring(1, text.size())
        } else if (text.endsWith("\'")) {
            return text.substring(1, text.size()-1)
        }
        return text
    }

    private def retrieveAndParsePropertiesFile (def name) {
        try {
            log.trace("MOTD: Looking for " + name + " file at " + idpMotdExistingLocation + "... ")
            def properties = new Properties()
            def file = new File(idpMotdExistingLocation)

            if (!file.exists()) {
                log.trace("MOTD: " + name + " file doesn't exist!")

            } else if (file.getText('UTF-8').isEmpty()) {
                log.trace("MOTD: " + name + " file empty!")

            } else {
                log.trace("MOTD: " + name + " found!")
                file.withInputStream {
                    properties.load(it)
                }
            }

            if (!name.contains("Config")) { //avoid printing config/password (if loaded that way)
                log.trace("MOTD: " + name + " contents [" + properties.toString() + "].")

                if (properties.containsKey("error")) {
                    log.trace("MOTD: Found existing error state.")
                    savedError = properties.get("error") //save error state
                    properties.remove("error")
                } else {
                    savedError = savedError
                }
            }

            return properties

        } catch (Exception e) {
            log.warn("MOTD: Error couldn't find/parse " + name + " file! Found error: ", e)
            return null
        }
    }

    private def isMOTDFormattedCorrectly(def motd) {
        def result
        log.trace("MOTD: Checking MOTD for validity...")

        try {
            if (motd) {
                motd.eachWithIndex { message, index ->
                    result = true
                    idpMotdExpectedKeys.each { key ->
                        if( !message.containsKey(key.trim().toLowerCase()) ) {
                            log.trace("MOTD: MOTD doesn't contain expected header [" + key + "].")
                            result = false
                            return
                        }
                    }

                    if (result) {
                        try {
                            ZonedDateTime.parse(message.get(idpMotdStartDateKey.trim().toLowerCase()), dateTimeFormatter)
                            ZonedDateTime.parse(message.get(idpMotdEndDateKey.trim().toLowerCase()), dateTimeFormatter)
                        } catch (ParseException pe) {
                            log.debug("MOTD: Incorrect MOTD format: Couldn't parse date for message " + index + " with format [" + idpMotdDateTimeZone + "]!")
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
            def now = ZonedDateTime.now(ZoneId.of(idpMotdDateTimeZone))
            motd.eachWithIndex { message, index ->
                def start =  ZonedDateTime.parse(message.get(idpMotdStartDateKey.trim().toLowerCase()), dateTimeFormatter)
                def end = ZonedDateTime.parse(message.get(idpMotdEndDateKey.trim().toLowerCase()), dateTimeFormatter)

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
                if (!oldMsg.containsKey(key)) {
                    result = false
                } else if (!oldMsg.getProperty(key).trim().equalsIgnoreCase(value.toString())) {
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

            if (idpMotdRemoveExisting.trim().equalsIgnoreCase("existingIsExpired") ) {
                def end = ZonedDateTime.parse(existingMessage.get(idpMotdEndDateKey.trim().toLowerCase()), dateTimeFormatter)

                if ( !end.isAfter(ZonedDateTime.now(ZoneId.of(idpMotdDateTimeZone))) ) {
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
            log.trace("MOTD: Printing [" + newMessage + "] to [" + idpMotdExistingLocation + "].")

            if (newMessage && !newMessage.isEmpty()) {
                log.debug("MOTD: Publishing populated MOTD!")
                new File(idpMotdExistingLocation).withWriter(StandardCharsets.UTF_8.name()) { writer ->
                    //writer.writeLine ''
                    newMessage.each { key, value ->
                        if (!key.equalsIgnoreCase(idpMotdStartDateKey) && !key.equalsIgnoreCase(idpMotdEndDateKey)) {
                            writer.writeLine "$key=\"$value\""    //note not trusting normal properties writer here since it could escape characters we need. TODO look into compatiblity issues
                        } else {
                            writer.writeLine "$key=$value"
                        }
                    }
                }
            } else {
                log.debug("MOTD: Publishing Empty MOTD!")
                new File(idpMotdExistingLocation).withWriter(StandardCharsets.UTF_8.name()) { writer ->
                    writer.write("")
                }
            }

        } catch (Exception e) {
            log.error("MOTD: Error saving/writing to MOTD Message Properties file! Found error: " + e)
            sendErrorEmail("Error writing to MOTD properties file!", true)
        }
    }

    private void sendErrorEmail(def message, def adminOnly) {
        if (shouldSendEmail()) {
            log.trace("MOTD: Sending email")
            setupMailSession()

            try {
                def emailToSend = new MimeMessage(mailSession)
                emailToSend.setFrom(new InternetAddress(idpMotdMailFromAddress))
                emailToSend.setSubject(idpMotdMailSubject)

                if (!adminOnly) {
                    def toAddresses = new InternetAddress[idpMotdMailToAddresses.length]
                    idpMotdMailToAddresses.eachWithIndex { val, idx ->
                        (toAddresses[idx] = new InternetAddress(val))
                    }
                    emailToSend.addRecipients(Message.RecipientType.TO, toAddresses)
                    emailToSend.addRecipients(Message.RecipientType.CC, new InternetAddress(idpMotdMailAdminAddress))
                } else {
                    emailToSend.addRecipients(Message.RecipientType.TO, new InternetAddress(idpMotdMailAdminAddress))
                }

                def vc = new VelocityContext()
                def writer = new StringWriter()
                vc.put("issue", message)
                mailTemplate.merge(vc, writer)
                emailToSend.setContent(writer.toString(), "text/html;charset=utf-8")

                Transport.send(emailToSend, idpMotdMailServerUser, idpMotdMailServerPassword)

            } catch (Exception e) {
                log.error("MOTD: Error creating or sending email! Error found: ", e)
            }

        } else {
            log.debug("MOTD: Not sending error email due to error email throttling requirement.")
        }
    }

    private boolean shouldSendEmail() {
        log.trace("MOTD: Checking if error email should be sent per throttling requirements.")
        def zoneId = ZoneId.of(idpMotdDateTimeZone);

        try {
            if (savedError && savedError.contains(",")) {
                log.trace("MOTD: Found existing error state [" + savedError + "].")
                def errorCountAndTime = savedError.toString().tokenize(",")
                def thresholdCount =  Integer.parseInt(idpMotdErrorThrottleThreshold)
                def errorCount = Integer.parseInt(errorCountAndTime[0].replace("error=", ""))
                def errorTime = ZonedDateTime.parse(errorCountAndTime[1], dateTimeFormatter)
                def hours = Long.parseLong(idpMotdErrorThrottleHours)

                log.trace("MOTD: Comparing threshold count [" + thresholdCount + "] with saved value [" + errorCount + "]...")
                if (errorCount > thresholdCount) {
                    def nowMinusHours = ZonedDateTime.now(zoneId).minusHours(hours)
                    log.trace("MOTD: Comparing error time [" + errorTime.format(dateTimeFormatter) + "] to [" + nowMinusHours + "]...")

                    if (errorTime.isAfter(nowMinusHours)) {
                        log.trace("MOTD: Following threshold and only incrementing error count, no email will be sent.")
                        saveErrorState(false, errorCount, errorTime.format(dateTimeFormatter))
                        return false
                    } else {
                        log.debug("MOTD: Time reached, starting over error threshold and sending emails again!")
                        saveErrorState(false, 0, ZonedDateTime.now().format(dateTimeFormatter))
                    }

                } else {
                    log.trace("MOTD: Threshold is below set value sending email and incrementing error count.")
                    saveErrorState(false, errorCount, errorTime.format(dateTimeFormatter))
                    return true
                }
            }
        } catch (Exception e) {
            log.debug("MOTD: Error checking error throttling state. Error found: ", e)
        }

        saveErrorState(true, 0, ZonedDateTime.now().format(dateTimeFormatter))
        return true
    }

    private void saveErrorState(def newError, def count, def time) {
        log.trace("MOTD: Saving Error State")
        try {
            def newCount = count+1
            def newErrorMsg = ("error=" + newCount + "," + time)

            if (newError) {
                log.info("MOTD: Saving new error record.")
                //new File(idpMotdExistingLocation).withWriterAppend(StandardCharsets.UTF_8.name()) { out ->
                //    out.write(System.lineSeparator() + newErrorMsg)
                //}
                savedError = newErrorMsg
            } else {
                log.info("MOTD: Existing error [" + savedError + "] and new error [" + newErrorMsg + "].")
                //def msgFile = new File(idpMotdExistingLocation)
                //def text = msgFile.text
                //msgFile.write(text.replaceAll(savedError,newErrorMsg))
                savedError = newErrorMsg
            }

        } catch (Exception e) {
            log.trace("MOTD: Error saving error state. Error found: ", e)
        }
    }

    private void setupMailSession() {
        if (!mailSession) {
            try {
                log.trace("MOTD: Setting up mail server")

                def mailProperties = new Properties()
                mailProperties.put("mail.smtp.host", idpMotdMailServerUrl)
                mailProperties.put("mail.smtp.port", idpMotdMailServerPort)
                mailProperties.put("mail.smtp.starttls.enable", "true")
                mailProperties.put("mail.smtp.socketFactory.port", idpMotdMailServerPort)
                mailProperties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                mailProperties.put("mail.smtp.socketFactory.fallback", "false")
                mailProperties.put("mail.smtp.user", idpMotdMailServerUser)
                mailProperties.put("mail.smtp.auth", "true")

                mailSession = Session.getDefaultInstance(mailProperties)

                if (!mailTemplate) {
                    def mt = new Properties();
                    mt.setProperty("resource.loader", "class");
                    mt.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
                    Velocity.init(mt)
                    mailTemplate = Velocity.getTemplate(idpMotdMailTemplate)
                }

            } catch (Exception e) {
                log.error("MOTD: Error setting up Email Server! Error found: ", e)
            }
        }
    }
}

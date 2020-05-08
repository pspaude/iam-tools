package net.unicon.iam.saml2.service.converter

import net.unicon.iam.saml2.service.converter.converters.ShibbolethIdPXmlConverter
import net.unicon.iam.saml2.service.converter.result.CASJSONResultProcessor
import net.unicon.iam.saml2.service.converter.result.ShibbolethIdpResultProcessor
import net.unicon.iam.saml2.service.converter.util.ResultFormats
import net.unicon.iam.saml2.service.converter.converters.CASJSONConverter
import net.unicon.iam.saml2.service.converter.util.OriginalFormats


/**
 * SAML 2 Configuration Service Converter
 *
 * Converts SAML2 service configuration from
 *  Shibboleth 3x or CAS 5x+ JSON formats
 * to one of the other formats listed.
 *
 * By: Paul Spaude
 * Date: 2019
 */
class SAML2ServiceConverter implements Runnable {

    def currentFormat
    def currentLocation
    def resultFormat
    def resultLocation
    def startingId
    def metadataLocation


    SAML2ServiceConverter(final URL configProps) {
        if (configProps) {
            println "SAML2 Service Converter: Retrieving converter config..."
            def config = retrieveAndParsePropertiesFile(configProps.getPath()) //Retrieve existing converter.properties configuration
            currentFormat = config.getProperty("converter.currentformat").toString().trim()
            resultFormat = config.getProperty("converter.resultformat").toString().trim()
            def origPath = config.getProperty("converter.currentdirorfile").toString().trim()
            def resultPath = config.getProperty("converter.resultlocation").toString().trim()

            if (!config.getProperty("converter.startingid").isBlank()) {
                startingId = config.getProperty("converter.startingid").trim().toBigInteger()
            }

            if (!config.getProperty("converter.metadatalocation").isBlank()) {
                metadataLocation = new File(config.getProperty("converter.metadatalocation").trim())
            }

            if (!origPath?.isEmpty() && !resultPath?.isEmpty()) {
                currentLocation = new File(origPath)
                resultLocation = new File(resultPath)
            }

        } else {
            println "SAML2 Service Converter:  No Configuration found!"
        }
    }

    @Override
    void run() {
        try {
            println "Starting Conversion Process..."

            if (!OriginalFormats.valueOf(currentFormat)) {
                println "\nPlease enter a valid origformat. It must be one of: " + OriginalFormats.toString()
                throw new Exception("Please enter a valid origformat")
            }

            if (!ResultFormats.valueOf(resultFormat)) {
                println "\nPlease enter a valid resultformat. It must be one of: " + ResultFormats.toString()
                throw new Exception("Please enter a valid resultformat")
            }

            if (!currentLocation.exists()) {
                println("\nPlease enter a valid file or directory path for currentdir")
                throw new Exception("Please enter a valid file or directory path for currentdir")
            }

            if (!resultLocation.exists()) {
                println("\nPlease enter a valid directory path for resultlocation")
                throw new Exception("Please enter a valid directory path for resultlocation")
            }

            println " \nStarting conversion process of [${currentLocation.getAbsolutePath()} ] and [${currentFormat}] to [${resultFormat}] to be placed in [${resultLocation.getAbsolutePath()}]... "

            def resultProcessor = ((resultFormat?.equalsIgnoreCase(ResultFormats.cas5json.toString())) ? new CASJSONResultProcessor(resultLocation, ResultFormats.cas5json) : new ShibbolethIdpResultProcessor(resultLocation, resultFormat))

            if (currentFormat.equalsIgnoreCase(OriginalFormats.casjson.toString())) {
                println "\nProcessing CAS 5+ JSON file directory..."
                CASJSONConverter.convertCASJSON(currentLocation.isDirectory(), currentLocation, resultProcessor)

            } else if (currentFormat.equalsIgnoreCase(OriginalFormats.shib3x.toString())) {
                println "\nProcessing Shibboleth IdP CAS Xml bean definition file..."
                ShibbolethIdPXmlConverter.convertShibbolethIdPXml(currentLocation, resultProcessor, metadataLocation, startingId)

            } else {
                println "\nYou've provided an invalid currentFormat or scenario! Make sure you've configured a file or directory as appropriate for currentdir! No conversion can be performed, exiting..."
            }

            println "Creating Results..."
            resultProcessor.processResults()
            println "Results complete!\n"
            println "\nSAML2 Service Converter is Finished"

        } catch (Exception e) {
            println "Error initializing SAML2 Service Converter! Ensure config properties file exists and has all 4 required properties! " + e
        }

        return
    }

    private def retrieveAndParsePropertiesFile (final String resource) {
        try {
            def returnProps = new Properties()
            def propsFile = new File(resource)

            propsFile.withInputStream {
                returnProps.load(it)
            }

            return returnProps

        } catch (Exception e) {
            println "SAML2 Service Converter: Error couldn't find/parse " + resource + " file! Found error: " + e
            return null
        }
    }
}

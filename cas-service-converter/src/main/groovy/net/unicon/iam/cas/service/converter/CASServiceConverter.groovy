package net.unicon.iam.cas.service.converter

import net.unicon.iam.cas.service.converter.converters.CAS3JSONConverter
import net.unicon.iam.cas.service.converter.converters.CASJSONConverter
import net.unicon.iam.cas.service.converter.result.CASJSONResultProcessor
import net.unicon.iam.cas.service.converter.result.ShibbolethIdpResultProcessor
import net.unicon.iam.cas.service.converter.util.OriginalFormats
import net.unicon.iam.cas.service.converter.util.ResultFormats
import net.unicon.iam.cas.service.converter.converters.ShibbolethCASMetadataConverter
import net.unicon.iam.cas.service.converter.converters.ShibbolethCASXMLConverter


/**
 * CAS Service Converter
 *
 * Converts CAS services from
 *  CAS 3.x JSON, CAS 4.x/5.x/6.x JSON, Shibboleth IdP xml bean, or Shibboleth IdP metadata
 * to one of the other formats listed. Note you can't convert to CAS 3.x JSON.
 *
 * By: Paul Spaude
 * Date: 2019
 */
class CASServiceConverter implements Runnable {

    def currentFormat
    def currentLocation
    def resultFormat
    def resultLocation


    CASServiceConverter(final URL configProps) {
        if (configProps) {
            println "CAS Service Converter: Retrieving converter config..."
            def config = retrieveAndParsePropertiesFile(configProps.getPath()) //Retrieve existing converter.properties configuration
            currentFormat = config.getProperty("converter.currentformat").toString().trim()
            resultFormat = config.getProperty("converter.resultformat").toString().trim()
            def origPath = config.getProperty("converter.currentdirorfile").toString().trim()
            def resultPath = config.getProperty("converter.resultlocation").toString().trim()

            if (!origPath?.isEmpty() && !resultPath?.isEmpty()) {
                currentLocation = new File(origPath)
                resultLocation = new File(resultPath)
            }

        } else {
            println "CAS Service Converter:  No Configuration found!"
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

            def isDirectory = currentLocation.isDirectory()
            def resultProcessor = ((resultFormat?.equalsIgnoreCase(ResultFormats.cas5json.toString())) ? new CASJSONResultProcessor(resultLocation, ResultFormats.cas5json) : new ShibbolethIdpResultProcessor(resultLocation, resultFormat))

            if (!isDirectory && currentFormat.equalsIgnoreCase(OriginalFormats.cas3json.toString())) {
                println "\nProcessing CAS 3 JSON file..."
                CAS3JSONConverter.convertCAS3JSON(currentLocation, resultProcessor)

            } else if (currentFormat.equalsIgnoreCase(OriginalFormats.casjson.toString())) {
                println "\nProcessing CAS 5+ JSON file directory..."
                CASJSONConverter.convertCASJSON(isDirectory, currentLocation, resultProcessor)

            } else if (!isDirectory && currentFormat.equalsIgnoreCase(OriginalFormats.shibxml.toString())) {
                println "\nProcessing Shibboleth IdP CAS Xml bean definition file..."
                ShibbolethCASXMLConverter.convertCASXML(resultProcessor)

            } else if (currentFormat.equalsIgnoreCase(OriginalFormats.shibmetadata.toString())) {
                println "\nProcessing Shibboleth IdP CAS Xml metadata file(s)..."
                ShibbolethCASMetadataConverter.convertCASMetadata(resultProcessor)

            } else {
                println "\nYou've provided an invalid currentFormat or scenario! Make sure you've configured a file or directory as appropriate for currentdir! No conversion can be performed, exiting..."
            }

            println "Creating Results..."
            resultProcessor.processResults()
            println "Results complete!\n"
            println "\nCAS Service Converter is Finished"

        } catch (Exception e) {
            println "Error initializing CAS Service Converter! Ensure config properties file exists and has all 4 required properties! " + e
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
            println "CAS Service Converter: Error couldn't find/parse " + resource + " file! Found error: " + e
            return null
        }
    }
}

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

    def origFormat
    def origLocation
    def resultFormat
    def resultLocation

    CASServiceConverter(def opt) {
        origFormat = opt.currentformat
        origLocation = new File(opt.currentdir)
        resultFormat = opt.resultformat
        resultLocation = new File(opt.resultlocation)
    }

    @Override
    void run() {
        println "Starting CAS Service Converter"

        if ( !OriginalFormats.valueOf(origFormat) ) {
            println "\nPlease enter a valid origformat. It must be one of: " + OriginalFormats.toString()
            return
        }

        if ( !ResultFormats.valueOf(resultFormat) ) {
            println "\nPlease enter a valid resultformat. It must be one of: " + ResultFormats.toString()
            return
        }

        if ( !origLocation.exists ( ) ) {
            println("\nPlease enter a valid file or directory path for currentdir")
            return
        }

        if ( !resultLocation.exists ( ) ) {
            println("\nPlease enter a valid directory path for resultlocation")
            return
        }

        println " \nStarting conversion process of [${origLocation.getAbsolutePath()} ] and [${origFormat}] to [${resultFormat}] to be placed in [${resultLocation.getAbsolutePath()}]... "

        def isDirectory = origLocation.isDirectory()
        def resultProcessor = ((resultFormat?.equalsIgnoreCase(ResultFormats.cas5json)) ? new CASJSONResultProcessor(resultLocation, ResultFormats.cas5json) : new ShibbolethIdpResultProcessor(resultLocation, resultFormat))

        if ( !isDirectory && origFormat.equalsIgnoreCase(OriginalFormats.cas3json) ) {
            println "\nProcessing CAS 3 JSON file..."
            CAS3JSONConverter.convertCAS3JSON(origLocation, resultProcessor)

        } else if ( origFormat.equalsIgnoreCase(OriginalFormats.casjson) ) {
            println "\nProcessing CAS 5+ JSON file directory..."
            CASJSONConverter.convertCASJSON(isDirectory, origLocation, resultProcessor)

        } else if ( !isDirectory && origFormat.equalsIgnoreCase(OriginalFormats.shibxml) ) {
            println "\nProcessing Shibboleth IdP CAS Xml bean definition file..."
            ShibbolethCASXMLConverter.convertCASXML(resultProcessor)

        } else if ( origFormat.equalsIgnoreCase(OriginalFormats.shibmetadata) ) {
            println "\nProcessing Shibboleth IdP CAS Xml metadata file(s)..."
            ShibbolethCASMetadataConverter.convertCASMetadata(resultProcessor)

        } else {
            println "\nYou've provided an invalid origformat or scenario! No conversion can be performed, exiting..."
        }

        println "Creating Results..."
        resultProcessor.processResults()
        println "Results complete!\n"
        println "\nCAS Service Converter is Finished"

        return
    }
}

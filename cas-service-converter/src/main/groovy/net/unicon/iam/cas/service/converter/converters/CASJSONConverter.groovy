package net.unicon.iam.cas.service.converter.converters

import groovy.io.FileType
import org.hjson.JsonValue;
import net.unicon.iam.cas.service.converter.result.ResultProcessor
import net.unicon.iam.cas.service.converter.util.CasService


class CASJSONConverter {

    private static int skipCount

    static void convertCASJSON(final boolean isDirectory, final File origLocation, final ResultProcessor resultProcessor) {
        if (isDirectory) {
            skipCount = 0
            println "\n\nProcessing ${origLocation.listFiles().length} possible CAS 5.x+ JSON Services..."
            def serviceCount = 0

            origLocation.eachFileRecurse(FileType.FILES) { file ->
                try {
                    if (file.name.endsWith(".json")) {
                        resultProcessor.storeResult(consumeJSONCAS(file))
                        serviceCount++

                    } else {
                        println "\n\nSkipping ${file.name} because it doesn't have JSON extension!"
                        skipCount++
                    }
                } catch (Exception e) {
                    println "Error processing 5x+ JSON file with name ${file.name} with exception " + e
                    skipCount++
                }
            }
            println "\n\nProcessed ${serviceCount-skipCount} out of ${serviceCount} CAS 5.x+ JSON Files!"

        } else {
            println "\n\nProcessing a single CAS 5.x+ JSON Service"
            if (origLocation.name.endsWith(".json")) {
                try {
                    resultProcessor.storeResult(consumeJSONCAS(origLocation))

                } catch (Exception e) {
                    println "Error processing single 5x JSOn file with exception " + e
                }
            } else {
                println "\nCan't process file because it doesn't have valid JSON extension!"
            }
        }
    }

    private static CasService consumeJSONCAS(final File file) throws Exception {
        def json = JsonValue.readHjson(file.text) //throws parse exception if invalid hjson

        if (json?.isObject() && json?.getAt("@class")?.toString()?.contains("RegexRegisteredService")) {
            def releaseAttributes = "default"
            if (json?.attributeReleasePolicy?.get("@class")?.toString()?.contains("ReturnAllAttributeReleasePolicy")) {
                releaseAttributes = "all"
            } else if (json?.attributeReleasePolicy?.get("@class")?.toString()?.contains("ReturnAllowedAttributeReleasePolicy")) {
                if (json?.attributeReleasePolicy?.allowedAttributes?.size() > 0) {
                    releaseAttributes = json?.attributeReleasePolicy?.allowedAttributes?.get(1)?.values()?.join(",")?.replaceAll("\"", "")
                } else {
                    releaseAttributes = "default"
                }
            } else {
                //TODO DenyAllAttributeReleasePolicy,ReturnMappedAttributeReleasePolicy and warn user to manually convert groovy,python, rest, regex etc.
            }

            return new CasService(
                    serviceId: json?.serviceId?.asString(),
                    name: json?.name?.asString(),
                    id: json?.id?.asInt(),
                    description: json?.description?.asString(),
                    evaluationOrder: json?.evaluationOrder?.asInt(),
                    usernameAttribute: json?.usernameAttributeProvider?.usernameAttribute?.asString(),
                    logoutType: json?.logoutType?.asString(),
                    mfaProviders: json?.multifactorPolicy?.multifactorAuthenticationProviders?.get(1)?.values()?.join(",")?.replaceAll("\"", ""), //TODO likely need to fix
                    mfaFailureMode: json?.multifactorPolicy?.failureMode?.asString(),  //TODO likely need to fix
                    releaseAttributes: releaseAttributes,
                    authorizedToReleaseCredentialPassword: json?.attributeReleasePolicy?.authorizedToReleaseCredentialPassword?.asString(),
                    authorizedToReleaseProxyGrantingTicket: json?.attributeReleasePolicy?.authorizedToReleaseProxyGrantingTicket?.asString(),
                    publicKeyLocation: json?.publicKey?.location?.asString(),
                    publicKeyAlgorithm: json?.publicKey?.algorithm?.asString()
            )

        } else {
            println "Skipping ${file.name} because it's not of the type RegexRegisteredService!"
            skipCount++
            return null
        }
    }
}

package net.unicon.iam.cas.service.converter.converters

import groovy.io.FileType
import groovy.json.JsonSlurper
import net.unicon.iam.cas.service.converter.result.ResultProcessor
import net.unicon.iam.cas.service.converter.util.CasService


class CASJSONConverter {

    static void convertCASJSON(isDirectory, origLocation, final ResultProcessor resultProcessor) {
        if (isDirectory) {
            println "\nProcessing CAS 5.x+ JSON Services..."
            def serviceCount = 0

            origLocation.eachFileRecurse(FileType.FILES) { file ->
                try {
                    if (file.name.endsWith(".json")) {
                        resultProcessor.storeResult(consumeJSONCAS(file))
                        serviceCount++

                    } else {
                        println "\nSkipping ${file.name} because it doesn't have JSON extension!"
                    }
                } catch (Exception e) {
                    println "Error processing 5x+ JSON file with name ${file.name} with exception " + e
                }
            }
            println "Processed ${serviceCount} CAS 5.x+ JSON Files!"

        } else {

            println "\nProcessing a single CAS 5.x+ JSON Service"
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
        def json = new JsonSlurper().parseText(file.text)
        //TODO check @class here ensure is RegexRegisteredService

        if ((!json.serviceId || json.serviceId.allWhitespace) || (!json.name || json.name.allWhitespace) || (!json.id)) {
            println "\nWarning: JSON for file [${file.name}] is not complete/valid. Missing or empty serviceId, name or id!"
        }

        def releaseAttributes = "default"
        if (json?.attributeReleasePolicy?.get("@class").contains("ReturnAllAttributeReleasePolicy")) {
            releaseAttributes = "all"
        } else if (json?.attributeReleasePolicy?.get("@class").contains("ReturnAllowedAttributeReleasePolicy")) {
            if (json?.attributeReleasePolicy?.allowedAttributes) {
                releaseAttributes = json?.attributeReleasePolicy?.allowedAttributes.get(1).join(",")
            } else {
                releaseAttributes = "default"
            }
        } else {
            //TODO DenyAllAttributeReleasePolicy,ReturnMappedAttributeReleasePolicy and warn user to manually convert groovy,python, rest, regex etc.
        }

        return new CasService(
                serviceId: json?.serviceId,
                name: json?.name,
                id: json?.id,
                description: json?.description,
                evaluationOrder: json?.evaluationOrder,
                usernameAttribute: json?.usernameAttributeProvider?.usernameAttribute,
                logoutType: json?.logoutType,
                mfaProviders: json?.multifactorPolicy?.multifactorAuthenticationProviders,
                mfaFailureMode: json?.multifactorPolicy?.failureMode,
                releaseAttributes: releaseAttributes,
                authorizedToReleaseCredentialPassword: json?.attributeReleasePolicy?.authorizedToReleaseCredentialPassword,
                authorizedToReleaseProxyGrantingTicket: json?.attributeReleasePolicy?.authorizedToReleaseProxyGrantingTicket,
                publicKeyLocation: json?.publicKey?.location,
                publicKeyAlgorithm: json?.publicKey?.algorithm
        )
    }
}

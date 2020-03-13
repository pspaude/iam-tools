package net.unicon.iam.cas.service.converter.converters

import groovy.json.JsonSlurper
import net.unicon.iam.cas.service.converter.result.ResultProcessor
import net.unicon.iam.cas.service.converter.util.CasService


class CAS3JSONConverter {

    static void convertCAS3JSON(final File jsonFile, final ResultProcessor resultProcessor) {
        println "\nProcessing a single CAS 3.x JSON Service file"
        try {
            def casServices = []
            def json = new JsonSlurper().parseText(jsonFile.text)

            if (!json?.services) {
                println "\n Warning: JSON for file [${jsonFile.name}] is not complete/valid. Missing or empty services!"
            }

            json.services.each { service ->
                try {
                    if ((!service.serviceId || service.serviceId.allWhitespace) || (!service.name || service.name.allWhitespace) || (!service.id)) {
                        println "\nWarning: JSON for service [${service.toString()}] is not complete/valid. Missing or empty serviceId, name or id!"
                        //TODO is this right?
                    }

                    //TODO any other properties to capture?

                    resultProcessor.storeResult(new CasService(
                            serviceId: convertAntToJavaRegex(json?.serviceId),
                            name: json?.name,
                            id: json?.id,
                            description: json?.description,
                            evaluationOrder: json?.evaluationOrder,
                            usernameAttribute: json?.usernameAttributeProvider?.usernameAttribute,
                            logoutType: json?.logoutType,
                            releaseAttributes: json?.allowedAttributes?.join(","),
                            authorizedToReleaseCredentialPassword: json?.attributeReleasePolicy?.authorizedToReleaseCredentialPassword,
                            authorizedToReleaseProxyGrantingTicket: json?.attributeReleasePolicy?.authorizedToReleaseProxyGrantingTicket,
                            publicKeyLocation: json?.publicKey?.location,
                            publicKeyAlgorithm: json?.publicKey?.algorithm,
                            ssoEnabled: json?.ssoEnabled,
                            enabled: json?.enabled,
                            allowedToProxy: json?.allowedToProxy,
                            anonymousAccess: json?.anonymousAccess,
                            theme: json?.theme,
                            staticAttributes: convertExtraAttributes(json?.extraAttributes)
                    ))
                } catch (Exception e) {
                    println "Error processing single JSON 3x Service with id ${json?.id} with exception " + e
                }
            }
        } catch (Exception e) {
            println "Error processing CAS 3x JSON File " + e
        }
    }

    /**
     *  Converts as best a possible the former ant regular expression into new CAS Java regex
     * @param serviceId
     */
    def convertAntToJavaRegex(final String serviceId) {
        if (!serviceId?.isEmpty()) {
            def toReturn = serviceId.trim().replace("**", "*")

            if (serviceId.endsWith("(http|https)")) {
                toReturn = "https?://" + (serviceId.substring(0, serviceId.indexOf("(http|https)")))
            }

            if (serviceId.startsWith("http*:")) {
                toReturn = serviceId.replace("http*", "https?")
            }

            //TODO any other conditions in ant to capture?

            return toReturn
        }

        return serviceId.trim()
    }

    /**
     * Converts as best possible the custom extra attributes notation
     * @param extraAttrs
     */
    def convertExtraAttributes(def extraAttrs) {
        if (extraAttrs) {
            def toReturn = [:]
            extraAttrs.each {k,v ->
                toReturn.put(k.trim(),v.trim())
            }

            return toReturn
        }
        return null
    }
}

package net.unicon.iam.cas.service.converter.converters

import groovy.json.JsonSlurper
import net.unicon.iam.cas.service.converter.result.ResultProcessor
import net.unicon.iam.cas.service.converter.util.CasService


class CAS3JSONConverter {

    static void convertCAS3JSON(final File jsonFile, final ResultProcessor resultProcessor) {
        println "\nProcessing a single CAS 3.x JSON Service file"
        try {
            def json = new JsonSlurper().parseText(jsonFile.text)

            if (!json?.services) {
                println "\n Warning: JSON for file [${jsonFile.name}] is not complete/valid. Missing or empty services!"
            }

            json.services.each { service ->
                try {
                    if ((!service.serviceId || service.serviceId.allWhitespace) || (!service.name || service.name.allWhitespace) || (!service.id)) {
                        println "\nWarning: JSON for service [${service.toString()}] is not complete/valid. Missing or empty serviceId, name or id!"
                    }

                    //TODO any other properties to capture?
                    resultProcessor.storeResult(new CasService(
                            serviceId: convertAntToJavaRegex(service?.serviceId),
                            name: service?.name,
                            id: service?.id,
                            description: service?.description,
                            evaluationOrder: service?.evaluationOrder,
                            usernameAttribute: service?.usernameAttributeProvider?.usernameAttribute,
                            logoutType: service?.logoutType,
                            releaseAttributes: service?.allowedAttributes?.join(","),
                            authorizedToReleaseCredentialPassword: service?.attributeReleasePolicy?.authorizedToReleaseCredentialPassword,
                            authorizedToReleaseProxyGrantingTicket: service?.attributeReleasePolicy?.authorizedToReleaseProxyGrantingTicket,
                            publicKeyLocation: service?.publicKey?.location,
                            publicKeyAlgorithm: service?.publicKey?.algorithm,
                            ssoEnabled: service?.ssoEnabled,
                            enabled: service?.enabled,
                            allowedToProxy: service?.allowedToProxy,
                            anonymousAccess: service?.anonymousAccess,
                            theme: service?.theme,
                            staticAttributes: convertExtraAttributes(service?.extraAttributes)
                    ))
                } catch (Exception e) {
                    println "Error processing single JSON 3x Service with id ${service?.id} with exception " + e
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
    private static String convertAntToJavaRegex(final String serviceId) {
        if (serviceId && !serviceId.isEmpty()) {
            def toReturn = serviceId?.trim()?.replace("**", "*")

            if (serviceId.endsWith("(http|https)")) {
                toReturn = "https?://" + (toReturn.substring(0, toReturn.indexOf("(http|https)")))
            }

            if (serviceId.startsWith("http*:")) {
                toReturn = toReturn.replace("http*", "https?")
            }

            //TODO any other conditions in ant to capture?

            return toReturn
        }

        return serviceId
    }

    /**
     * Converts as best possible the custom extra attributes notation
     * @param extraAttrs
     */
    private static Map<String,String> convertExtraAttributes(def extraAttrs) {
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

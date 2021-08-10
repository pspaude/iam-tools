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

                    def parsedExtraAttributes = convertExtraAttributes(service?.extraAttributes)

                    //TODO any other properties to capture?
                    resultProcessor.storeResult(new CasService(
                            serviceId: convertAntToJavaRegex(service?.serviceId),
                            name: service?.name,
                            id: service?.id,
                            description: service?.description,
                            evaluationOrder: service?.evaluationOrder,
                            usernameAttribute: handleUsernameAttribute(service),
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
                            mfaProviders: parsedExtraAttributes?.get("mfaProviders")?.keySet()?.join(","),
                            mfaPrincipalAttributeTriggers: parsedExtraAttributes?.get("mfaPrincipalAttributeTriggers"),
                            staticAttributes: parsedExtraAttributes?.get("staticAttributes")
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
            def toReturn = serviceId?.trim()?.replace("http*", "https?")?.replaceAll("\\.", "\\\\\\\\.")?.replaceAll("(\\*\\*|\\*)", ".*")

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
    private static Map<String, Map<String, String>> convertExtraAttributes(def extraAttrs) {
        if (extraAttrs) {
            Map<String, Map<String, String>> toReturn = [:]
            def mfaProviders = [:]
            def mfaPrincipalAttributes = [:]
            def staticAttributes = [:]

            extraAttrs.each {k,v ->
                if ("authn_method".equalsIgnoreCase(k.toString())) {
                    def provider
                    if ("duo-two-factor".equalsIgnoreCase(v.toString())) {
                        provider = "mfa-duo"
                    } else {
                        provider = v.toString().trim()
                    }
                    mfaProviders.put(provider, v)

                } else if ("mfa_role".equalsIgnoreCase(k.toString())) {
                    def attrName = ""
                    def attrPattern = ""

                    v.each {key,value ->
                        if ("mfa_attribute_name".equalsIgnoreCase(key.toString())) {
                            attrName = value.toString().trim()
                        }
                        if ("mfa_attribute_pattern".equalsIgnoreCase(key.toString())) {
                            attrPattern = value.toString().trim()
                        }
                        if (!attrName.isBlank() && !attrPattern.isBlank()) {
                            mfaPrincipalAttributes.put(attrName, attrPattern)
                            attrName = ""
                            attrPattern = ""
                        }
                    }

                } else {
                    staticAttributes.put(k.toString().trim(), v.toString().trim())
                }
            }

            toReturn.put("mfaProviders", mfaProviders)
            toReturn.put("mfaPrincipalAttributeTriggers", mfaPrincipalAttributes)
            toReturn.put("staticAttributes", staticAttributes)

            return toReturn
        }

        return null
    }

    private static String handleUsernameAttribute(def service) {
        if (service) {
            if (service.usernameAttributeProvider) {
                return service.usernameAttributeProvider.usernameAttribute
            } else if (service.usernameAttribute) {
                return service.usernameAttribute
            }
        }
        return null
    }
}

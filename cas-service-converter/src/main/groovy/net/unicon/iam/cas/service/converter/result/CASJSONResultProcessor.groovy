package net.unicon.iam.cas.service.converter.result

import net.unicon.iam.cas.service.converter.util.CasService
import net.unicon.iam.cas.service.converter.util.ResultFormats
import java.nio.charset.StandardCharsets


class CASJSONResultProcessor extends ResultProcessor {

    CASJSONResultProcessor(resultLocation, resultFormat) {
        super(resultLocation, resultFormat)
    }

    /**
     * Processes single CAS Service directly to CAS 5x JSON file
     * @param casService
     * @param remainFileCount
     * @return
     */
    @Override
    void processResults() {
        if (resultFormat?.equalsIgnoreCase(ResultFormats.cas5json.toString())) {
            def fileCount = 0
            servicesStorage.keySet().sort().each { id ->
                servicesStorage[id].each { cs ->
                    try {
                        fileCount++
                        def fileName = createCASServiceFileName(cs.name, fileCount, cs.id)
                        def cas5xJSON = casServiceTo5xJSON(cs, fileName)

                        if (cas5xJSON && !cas5xJSON.isEmpty()) {
                            new File(resultLocation.getAbsolutePath() + File.separator + fileName)
                                    .withWriterAppend(StandardCharsets.UTF_8.name()) { out ->
                                        out.write(cas5xJSON)
                                    }
                        } else {
                            println "Error creating CAS 5x JSON for ${fileName}!"
                        }
                    } catch (Exception e) {
                        println "Error creating CAS 5x+ JSON File" + e
                    }
                }
            }
            println "Created ${fileCount} CAS 5.x+ JSON Service Files"
            final Set<String> attributesToRelease = []
            attributeStorage.keySet().each { attrList ->
                attrList.each {
                    attributesToRelease.add(it)
                }
            }
            println "List of Attributes to be Released: [" + attributesToRelease.toString() + "]."

        } else {
            //Do nothing not the right format
        }
    }

    def createCASServiceFileName(final String serviceName, int fileCount, final String id) {
        def name = "CAS_Service"
        def end = "-"
        end = ((id) ? (end + id + ".json") : (end + fileCount + ".json"))

        if (!serviceName.isEmpty()) {
            name = serviceName.trim().replaceAll("[^a-zA-Z0-9\\-]", "_")
        }

        name = name.replaceFirst("^_+", "") //remove any preceding _ chars

        if (name.contains("_-_")) {
            name = name.replace("_-_", "_")
        }

        if (name.contains("-")) {
            name = name.replace("-", "_")
        }

        if (name.contains("__")) {
            name = name.substring(0, name.indexOf("__"))  //presumably a long file name so we'll cut it short
        }

        return name + end
    }

    def casServiceTo5xJSON(final CasService cs, final String fileName) {
        def beginFile = "{" + System.lineSeparator()
        def endFile = "}" + System.lineSeparator()
        def singleLineEnd = "\", " + System.lineSeparator()
        def blockEnd = "\" " + System.lineSeparator() + "  }, " + System.lineSeparator()

        def builder = new StringBuilder()
        builder.append(beginFile)
        builder.append("  \"@class\" : \"org.apereo.cas.services.RegexRegisteredService" + singleLineEnd)
        builder.append("  \"serviceId\" : \"" + cs.serviceId + singleLineEnd)
        builder.append("  \"id\" : \"" + cs.id + singleLineEnd)
        builder.append("  \"name\" : \"" + fileName.substring(0, fileName.indexOf("-")) + singleLineEnd)

        if (!cs?.description) {
            builder.append("  \"description\" : \"" + cs.name + singleLineEnd)
        } else {
            builder.append("  \"description\" : \"" + formatDescription(cs.description) + singleLineEnd)
        }

        builder.append("  \"evaluationOrder\" : \"" + cs.evaluationOrder + singleLineEnd)

        if (cs?.theme) {
            builder.append("  \"theme\" : \"" + cs.theme + singleLineEnd)
        }

        if (cs?.logoutType) {
            builder.append("  \"logoutType\" : \"" + cs.logoutType + singleLineEnd)  //TODO need any validation correct logout type here?
        }

        if (cs?.usernameAttribute || cs?.anonymousAccess?.equalsIgnoreCase("true")) {
            builder.append("  \"usernameAttributeProvider\" : { " + System.lineSeparator())

            if (cs?.anonymousAccess) {
                builder.append("    \"@class\" : \"org.apereo.cas.services.AnonymousRegisteredServiceUsernameAttributeProvider" + blockEnd)
            } else {
                builder.append("    \"@class\" : \"org.apereo.cas.services.PrincipalAttributeRegisteredServiceUsernameProvider" + singleLineEnd)
                builder.append("    \"usernameAttribute\" : \"" + cs.usernameAttribute + blockEnd)
            }
        }

        if (cs?.ssoEnabled || cs?.enabled) {
            builder.append("  \"accessStrategy\" : { " + System.lineSeparator())
            builder.append("    \"@class\" : \"org.apereo.cas.services.DefaultRegisteredServiceAccessStrategy" + singleLineEnd)

            if (cs?.ssoEnabled && cs?.enabled) {
                builder.append("    \"ssoEnabled\" : \"" + cs.ssoEnabled + singleLineEnd)
                builder.append("    \"enabled\" : \"" + cs.enabled + blockEnd)
            } else if (cs?.ssoEnabled) {
                builder.append("    \"ssoEnabled\" : \"" + cs.ssoEnabled + blockEnd)
            } else {
                builder.append("    \"enabled\" : \"" + cs.enabled + blockEnd)
            }
        }

        if (cs?.allowedToProxy && cs?.allowedToProxy?.equalsIgnoreCase("true")) {
            builder.append("  \"proxyPolicy\" : { " + System.lineSeparator())
            builder.append("    \"@class\" : \"org.apereo.cas.services.RegexMatchingRegisteredServiceProxyPolicy" + singleLineEnd)
            builder.append("    \"pattern\" : \"^https?://.*" + blockEnd)
        }

        if (cs?.mfaProviders) {
            builder.append("  \"multifactorPolicy\" : { " + System.lineSeparator())
            builder.append("    \"@class\" : \"org.apereo.cas.services.DefaultRegisteredServiceMultifactorPolicy" + singleLineEnd)
            builder.append("    \"multifactorAuthenticationProviders\" : [ \"java.util.LinkedHashSet\", [ ")

            def mfaList = cs.mfaProviders.split(",")
            mfaList.each { val ->
                if (val == mfaList.last()) {
                    builder.append("\"" + val + "\"")
                } else {
                    builder.append("\"" + val + "\", ")
                }
            }
            builder.append(" ] ]")

            if (cs?.mfaFailureMode) {
                builder.append(", " + System.lineSeparator() + "    \"failureMode\" : \"" + cs.mfaFailureMode + blockEnd)
            } else {
                builder.append(System.lineSeparator() + "  }, " + System.lineSeparator())
            }
        }

        if (cs?.staticAttributes) {
            builder.append("  \"properties\" : { " + System.lineSeparator())
            builder.append("    \"@class\" : \"java.util.HashMap\"," + System.lineSeparator())
            cs.staticAttributes.eachWithIndex { kv, idx ->
                //builder.append("//\"" + k + "\" : \"" + v + System.lineSeparator())  //add as comment
                if (idx == 0) {
                    builder.append("    \"" + kv.getKey() + "\" : { " + System.lineSeparator())
                } else {
                    builder.append("," + System.lineSeparator() + "    \"" + kv.getKey() + "\" : { " + System.lineSeparator())
                }
                builder.append("        \"@class\" : \"org.apereo.cas.services.DefaultRegisteredServiceProperty" + singleLineEnd)
                builder.append("        \"values\" : [ \"java.util.HashSet\", [ \"" + kv.getValue() + "\" ] ]" + System.lineSeparator())
                builder.append("    }")
            }
            builder.append(System.lineSeparator() + "  }, " + System.lineSeparator())
        }

        if (cs?.releaseAttributes && !cs?.releaseAttributes?.equalsIgnoreCase("default")) {
            builder.append("  \"attributeReleasePolicy\" : { " + System.lineSeparator())

            if (cs?.releaseAttributes?.equalsIgnoreCase("all")) {
                builder.append("    \"@class\" : \"org.apereo.cas.services.ReturnAllAttributeReleasePolicy")

            } else {
                builder.append("    \"@class\" : \"org.apereo.cas.services.ReturnAllowedAttributeReleasePolicy" + singleLineEnd)
                builder.append("    \"allowedAttributes\" : [ \"java.util.ArrayList\", [ ")

                def allowedList = cs.releaseAttributes.split(",")
                allowedList.each { val ->
                    if (val == allowedList.last()) {
                        builder.append("\"" + val + "\"")
                    } else {
                        builder.append("\"" + val + "\", ")
                    }
                }
                builder.append(" ] ]")
            }

            if (cs?.authorizedToReleaseProxyGrantingTicket && cs?.authorizedToReleaseCredentialPassword) {
                builder.append(", " + System.lineSeparator() + "    \"authorizedToReleaseProxyGrantingTicket\" : \"" + cs.authorizedToReleaseProxyGrantingTicket + singleLineEnd)
                builder.append("    \"authorizedToReleaseCredentialPassword\" : \"" + cs.authorizedToReleaseCredentialPassword + blockEnd)
            } else if (cs?.authorizedToReleaseProxyGrantingTicket) {
                builder.append(", " + System.lineSeparator() + "    \"authorizedToReleaseProxyGrantingTicket\" : \"" + cs.authorizedToReleaseProxyGrantingTicket + blockEnd)
            } else if (cs?.authorizedToReleaseCredentialPassword) {
                builder.append(", " + System.lineSeparator() + "    \"authorizedToReleaseCredentialPassword\" : \"" + cs.authorizedToReleaseCredentialPassword + blockEnd)
            } else {
                builder.append(System.lineSeparator() + "  }, " + System.lineSeparator())
            }
        }

        if (cs?.publicKeyLocation && cs?.publicKeyAlgorithm) {
            builder.append("  \"publicKey\" : { " + System.lineSeparator())
            builder.append("    \"@class\" : \"org.apereo.cas.services.RegisteredServicePublicKeyImpl" + singleLineEnd)
            builder.append("    \"location\" : \"" + cs.publicKeyLocation + singleLineEnd)
            builder.append("    \"algorithm\" : \"" + cs.publicKeyAlgorithm + blockEnd)
        }

        def toReturn = builder.toString()
        def length = System.lineSeparator().length() + 2
        if (toReturn.substring(toReturn.length()-length, toReturn.length()).contains(",")) {
            return toReturn.substring(0, toReturn.length()-length) + System.lineSeparator() + endFile
        } else {
            return toReturn + endFile
        }
    }

    def formatDescription (final String description) {
        return description
                .replaceAll("\b", "\\\\b")
                .replaceAll("\f", "\\\\f")
                .replaceAll("\n", "\\\\n")
                .replaceAll("\r", "\\\\r")
                .replaceAll("\t", "\\\\t")
    }
}

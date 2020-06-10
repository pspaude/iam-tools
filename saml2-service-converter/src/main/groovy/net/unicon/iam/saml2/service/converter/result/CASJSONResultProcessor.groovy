package net.unicon.iam.saml2.service.converter.result

import net.unicon.iam.saml2.service.converter.util.AttributeDefinition
import net.unicon.iam.saml2.service.converter.util.SAML2Service
import net.unicon.iam.saml2.service.converter.util.ResultFormats
import java.nio.charset.StandardCharsets


class CASJSONResultProcessor extends ResultProcessor {

    CASJSONResultProcessor(resultLocation, resultFormat) {
        super(resultLocation, resultFormat)
    }

    /**
     * Processes single SAML2 Service directly to CAS 5x JSON file
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
                        def fileName = createCASServiceFileName(cs.name, fileCount, cs.id)
                        def cas5xJSON = saml2ServiceTo5xJSON(cs, fileName)

                        if (cas5xJSON && !cas5xJSON.isEmpty()) {
                            if (fileNamesUsed.contains(fileName)) {
                                println "Warning duplicate file!"
                                throw new Exception ()
                            }

                            fileNamesUsed.add(fileName)
                            new File(resultLocation.getAbsolutePath() + File.separator + fileName)
                                    .withWriterAppend(StandardCharsets.UTF_8.name()) { out ->
                                        out.write(cas5xJSON)
                                    }
                            fileCount++
                        } else {
                            println "Error creating CAS 5x SAML2 JSON for ${fileName}!"
                            throw new Exception ()
                        }
                    } catch (Exception e) {
                        println "Error creating CAS 5x+ SAML2 JSON File" + e
                    }
                }
            }
            println "Created ${fileCount} CAS 5.x+ SAML2 JSON Service Files"
            final Set<String> attributesToRelease = []
            attributeStorage.keySet().each { attrList ->
                attrList.each {
                    attributesToRelease.add(it)
                }
            }

            //Output notes and messages for manual review
            new File(resultLocation.getAbsolutePath() + File.separator + "NOTES.txt")
                    .withWriterAppend(StandardCharsets.UTF_8.name()) { out ->
                        out.println "List of Attributes to be Released: [" + attributesToRelease.toString() + "]."

                        out.println "\n***BEGIN NOTES***"
                        messageList.each { i ->
                            out.println i
                        }
                        out.println "\n***END NOTES****"
                    }

//            def fileNamesStripped = [:]
//            fileNamesUsed.each { it ->
//                fileNamesStripped.put(it.substring(0,it.indexOf("-")), it)
//            }
//            def old = new File("/home/paul/Desktop/saml2")
//            if (old) {
//                old.eachFileRecurse { file ->
//                    try {
//                        def fileName = file.name
//                        if (fileName.contains("-")) {
//                            def strp = fileName.substring(0, fileName.indexOf("-"))
//                            if (fileNamesStripped.containsKey(strp)) {
//                                def filePath = file.getParent() + File.separator + fileNamesStripped.get(strp)
//                                file.renameTo(filePath)
//                            }
//                        }
//                    }catch (Exception e) {
//                        println "BLAH"
//                    }
//                }
//            }

        } else {
            //Do nothing not the right format
        }
    }

    def createCASServiceFileName(final String serviceName, int fileCount, final String id) {
        def name = "SAML2_Service"
        def end = "_SP-"
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

    def saml2ServiceTo5xJSON(final SAML2Service cs, final String fileName) {
        def beginFile = "{" + System.lineSeparator()
        def endFile = "}" + System.lineSeparator()
        def singleLineEnd = "\", " + System.lineSeparator()
        def blockEnd = "\" " + System.lineSeparator() + "  }, " + System.lineSeparator()

        def builder = new StringBuilder()
        builder.append(beginFile)
        builder.append("  \"@class\" : \"org.apereo.cas.support.saml.services.SamlRegisteredService" + singleLineEnd)
        builder.append("  \"serviceId\" : \"" + cs.serviceId + singleLineEnd)
        builder.append("  \"id\" : \"" + cs.id + singleLineEnd)
        builder.append("  \"name\" : \"" + fileName.substring(0, fileName.indexOf("-")) + singleLineEnd)

        if (!cs?.description) {
            builder.append("  \"description\" : \"" + cs.name + singleLineEnd)
        } else {
            builder.append("  \"description\" : \"" + formatDescription(cs.description) + singleLineEnd)
        }

        builder.append("  \"evaluationOrder\" : \"" + cs.evaluationOrder + singleLineEnd)

        builder.append("  \"metadataLocation\" : \"" + cs.metadataLocation + singleLineEnd)

        if (cs?.metadataSignatureLocation) {
            builder.append("  \"metadataSignatureLocation\" : \"" + cs.metadataSignatureLocation + singleLineEnd)
        }

        if (cs?.signAssertions) {
            builder.append("  \"signAssertions\" : \"" + cs.signAssertions + singleLineEnd)
        } else {
            builder.append("  \"signAssertions\" : \"" + "false" + singleLineEnd)
        }

        if (cs?.signResponses) {
            builder.append("  \"signResponses\" : \"" + cs.signResponses + singleLineEnd)
        } else {
            builder.append("  \"signResponses\" : \"" + "true" + singleLineEnd)
        }

        if (cs?.encryptAssertions) {
            builder.append("  \"encryptAssertions\" : \"" + cs.encryptAssertions + singleLineEnd)
        } else {
            builder.append("  \"encryptAssertions\" : \"" + "false" + singleLineEnd)
        }

        if (cs?.requiredNameIdFormat) {
            builder.append("  \"requiredNameIdFormat\" : \"" + cs.requiredNameIdFormat + singleLineEnd)
        }

        if (cs?.theme) {
            builder.append("  \"theme\" : \"" + cs.theme + singleLineEnd)
        }

//        if (cs?.staticAttributes) {
//            cs.staticAttributes.each { k, v ->
//                builder.append("//\"" + k + "\" : \"" + v + System.lineSeparator())  //add as comment
//            }
//        }

        if (cs?.logoutType) {
            builder.append("  \"logoutType\" : \"" + cs.logoutType + singleLineEnd)  //TODO need any validation correct logout type here?
        }

        if (false && cs?.usernameAttribute) { //TODO FIX THIS!
            builder.append("  \"usernameAttributeProvider\" : { " + System.lineSeparator())
            builder.append("    \"@class\" : \"org.apereo.cas.services.PrincipalAttributeRegisteredServiceUsernameProvider" + singleLineEnd)
            builder.append("    \"usernameAttribute\" : \"" + cs.usernameAttribute + blockEnd)
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

//        if (cs?.allowedToProxy && cs?.allowedToProxy?.equalsIgnoreCase("true")) {
//            builder.append("  \"proxyPolicy\" : { " + System.lineSeparator())
//            builder.append("    \"@class\" : \"org.apereo.cas.services.RegexMatchingRegisteredServiceProxyPolicy" + singleLineEnd)
//            builder.append("    \"pattern\" : \"^https?://.*" + blockEnd)
//        }
//
//        if (cs?.mfaProviders) {
//            builder.append("  \"multifactorPolicy\" : { " + System.lineSeparator())
//            builder.append("    \"@class\" : \"org.apereo.cas.services.DefaultRegisteredServiceMultifactorPolicy" + singleLineEnd)
//            builder.append("    \"multifactorAuthenticationProviders\" : [ \"java.util.LinkedHashSet\", [ ")
//
//            def mfaList = cs.mfaProviders.split(",")
//            mfaList.each { val ->
//                if (val == mfaList.last()) {
//                    builder.append("\"" + val + "\"")
//                } else {
//                    builder.append("\"" + val + "\", ")
//                }
//            }
//            builder.append(" ] ]")
//
//            if (cs?.mfaFailureMode) {
//                builder.append(", " + System.lineSeparator() + "    \"failureMode\" : \"" + cs.mfaFailureMode + blockEnd)
//            } else {
//                builder.append(System.lineSeparator() + "  }, " + System.lineSeparator())
//            }
//        }

        if (false && cs?.releaseAttributes && !cs?.releaseAttributes?.equalsIgnoreCase("default")) {
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

//            if (cs?.authorizedToReleaseProxyGrantingTicket && cs?.authorizedToReleaseCredentialPassword) {
//                builder.append(", " + System.lineSeparator() + "    \"authorizedToReleaseProxyGrantingTicket\" : \"" + cs.authorizedToReleaseProxyGrantingTicket + singleLineEnd)
//                builder.append("    \"authorizedToReleaseCredentialPassword\" : \"" + cs.authorizedToReleaseCredentialPassword + blockEnd)
//            } else if (cs?.authorizedToReleaseProxyGrantingTicket) {
//                builder.append(", " + System.lineSeparator() + "    \"authorizedToReleaseProxyGrantingTicket\" : \"" + cs.authorizedToReleaseProxyGrantingTicket + blockEnd)
//            } else if (cs?.authorizedToReleaseCredentialPassword) {
//                builder.append(", " + System.lineSeparator() + "    \"authorizedToReleaseCredentialPassword\" : \"" + cs.authorizedToReleaseCredentialPassword + blockEnd)
//            } else {
                builder.append(System.lineSeparator() + "  }, " + System.lineSeparator())
//            }
        } else if (!cs?.releaseAttributes) {
            //TODO Make this optional!!!
            builder.append("  \"attributeReleasePolicy\" : { " + System.lineSeparator())
            builder.append("    \"@class\" : \"org.apereo.cas.support.saml.services.GroovySamlRegisteredServiceAttributeReleasePolicy" + singleLineEnd)
            builder.append("    \"groovyScript\" : \"file:/etc/cas/services/scripts/Saml2AttributeRelease.groovy" + blockEnd)
        }

//        if (cs?.publicKeyLocation && cs?.publicKeyAlgorithm) {
//            builder.append("  \"publicKey\" : { " + System.lineSeparator())
//            builder.append("    \"@class\" : \"org.apereo.cas.services.RegisteredServicePublicKeyImpl" + singleLineEnd)
//            builder.append("    \"location\" : \"" + cs.publicKeyLocation + singleLineEnd)
//            builder.append("    \"algorithm\" : \"" + cs.publicKeyAlgorithm + blockEnd)
//        }

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

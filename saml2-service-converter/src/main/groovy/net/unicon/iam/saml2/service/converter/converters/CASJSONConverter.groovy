package net.unicon.iam.saml2.service.converter.converters

import groovy.io.FileType
import org.hjson.JsonValue;
import net.unicon.iam.saml2.service.converter.result.ResultProcessor
import net.unicon.iam.saml2.service.converter.util.SAML2Service


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
                        resultProcessor.storeResult(consumeJSONSAML(file))
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
                    resultProcessor.storeResult(consumeJSONSAML(origLocation))

                } catch (Exception e) {
                    println "Error processing single 5x JSOn file with exception " + e
                }
            } else {
                println "\nCan't process file because it doesn't have valid JSON extension!"
            }
        }
    }

    private static SAML2Service consumeJSONSAML(final File file) throws Exception {
        def json = JsonValue.readHjson(file.text) //throws parse exception if invalid hjson

        if (json?.isObject() && json?.getAt("@class")?.toString()?.contains("SamlRegisteredService")) {
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
            //TODO Username=persistentIdGenerator, Encryption Algorithms?!?
            /*List<String> encryptableAttributes
            String requiredAuthenticationContextClass
            String metadataCriteriaPattern
            String metadataCriteriaDirection
            String metadataCriteriaRoles
            String metadataCriteriaRemoveEmptyEntitiesDescriptors
            String metadataCriteriaRemoveRolelessEntityDescriptors
            String nameIdQualifier
            String issuerEntityId
            String assertionAudiences
            String serviceProviderNameIdQualifier
            String signingCredentialFingerprint
            String signingCredentialType
            String signingSignatureReferenceDigestMethods
            String signingSignatureAlgorithms
            String signingSignatureBlackListedAlgorithms
            String signingSignatureCanonicalizationAlgorithm
            String encryptionDataAlgorithms
            String encryptionKeyAlgorithms
            String encryptionBlackListedAlgorithms
            String encryptionWhiteListedAlgorithms
            String whiteListBlackListPrecedence */

            return new SAML2Service(
                    serviceId: json?.serviceId?.asString(),
                    name: json?.name?.asString(),
                    id: json?.id?.asInt(),
                    description: json?.description?.asString(),
                    evaluationOrder: json?.evaluationOrder?.asInt(),
                    metadataLocation: json?.metadataLocation?.asString(),
                    metadataSignatureLocation: json?.metadataSignatureLocation?.asString(),
                    requireSignedRoot: json?.requireSignedRoot?.asString(),
                    signAssertions: json?.signAssertions?.asString(),
                    signResponses: json?.signResponses?.asString(),
                    encryptionOptional: json?.encryptionOptional?.asString(),
                    encryptAssertions: json?.encryptAssertions?.asString(),
                    //encryptAttributes: json?.encryptAttributes?.asString(), TODO
                    skewAllowance: json?.skewAllowance?.asString(),
                    requiredNameIdFormat: json?.requiredNameIdFormat?.asString(),
                    //attributeNameFormats: json?.attributeNameFormats?.asString(),TODO
                    //attributeValueTypes: json?.attributeValueTypes?.asString(), TODO
                    skipGeneratingAssertionNameId: json?.skipGeneratingAssertionNameId?.asString(),
                    skipGeneratingTransientNameId: json?.skipGeneratingTransientNameId?.asString(),
                    skipGeneratingSubjectConfirmationInResponseTo: json?.skipGeneratingSubjectConfirmationInResponseTo?.asString(),
                    skipGeneratingSubjectConfirmationNotOnOrAfter: json?.skipGeneratingSubjectConfirmationNotOnOrAfter?.asString(),
                    skipGeneratingSubjectConfirmationRecipient: json?.skipGeneratingSubjectConfirmationRecipient?.asString(),
                    skipGeneratingSubjectConfirmationNotBefore: json?.skipGeneratingSubjectConfirmationNotBefore?.asString(),
                    skipGeneratingSubjectConfirmationNameId: json?.skipGeneratingSubjectConfirmationNameId?.asString(),
                    usernameAttribute: handleUsernameAttribute(json),
                    logoutType: json?.logoutType?.asString(),
                    releaseAttributes: releaseAttributes,
            )

        } else {
            println "Skipping ${file.name} because it's not of the type RegexRegisteredService!"
            skipCount++
            return null
        }
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

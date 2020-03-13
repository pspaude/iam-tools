package net.unicon.iam.cas.service.converter

import groovy.io.FileType
import groovy.json.JsonSlurper
import net.unicon.iam.cas.service.converter.util.CasService


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

    private final originalFormats = ["cas3json", "casjson", "shibxml", "shibmetadata"]
    private final resultFormats = ["cas5json", "shibxml", "shibmetadata"]
    private final TreeMap servicesStorage = [:]
    //used for storing services in order to print out results based on evaluation order, value is list of services for that order
    private final Map usernameStorage = [:].withDefault { key -> return [] }
    //key is username to release, value is list of serviceIds that release that username
    private final Map proxyStorage = [:].withDefault { key -> return [] }
    //key is pair of key location and algorithm, value is list of serviceIds that use this key
    private final Map attributeStorage = [:].withDefault { key -> return [] }
    //key is set of release attributes, value is list of serviceIds that release those values (none, default, and all are also possible keys)

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

        if ( !originalFormats.contains ( origFormat ) ) {
            println "\nPlease enter a valid origformat. It must be one of: " + originalFormats
            return
        }

        if ( !resultFormats.contains ( resultFormat ) ) {
            println "\nPlease enter a valid resultformat. It must be one of: " + resultFormats
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
        if ( !isDirectory && originalFormats.get(0).equalsIgnoreCase(origFormat) ) {
            convertCAS3JSON()
        } else if ( originalFormats.get(1).equalsIgnoreCase(origFormat) ) {
            convertCASJSON(isDirectory)
        } else if ( !isDirectory && originalFormats.get(2).equalsIgnoreCase(origFormat) ) {
            convertShibXml()
        } else if ( originalFormats.get(3).equalsIgnoreCase(origFormat) ) {
            convertShibMetadata(isDirectory)
        } else {
            println "\nYou've provided an invalid origformat or scenario! No conversion can be performed, exiting..."
        }

        println "\nCAS Service Converter is Finished"
        return

    }

    def convertCAS3JSON() {
        println "\nProcessing CAS 3 JSON file..."
        //TODO Add CAS 3 JSON Processing
    }

    def convertCASJSON(isDirectory) {
        if (isDirectory) {
            def remainFileCount = origLocation.listFiles().length

            println "\nProcessing ${remainFileCount} CAS 5.x+ JSON Services..."
            origLocation.eachFileRecurse(FileType.FILES) { file ->
                if (file.name.endsWith(".json")) {
                    remainFileCount--
                    processResult(consumeJSONCAS(file), remainFileCount)
                } else {
                    println "\nSkipping ${file.name} because it doesn't have JSON extension!"
                }
            }
        } else {
            println "\nProcessing a single CAS 5.x+ JSON Service"
            if (origLocation.name.endsWith(".json")) {
                processResult(consumeJSONCAS(origLocation), 0)
            } else {
                println "\nCan't process file because it doesn't have valid JSON extension!"
            }
        }
    }

    def convertShibXml() {
        println "\nProcessing Shibboleth IdP CAS Xml bean definition file..."
        //TODO Add processing for Shibboleth CAS Service bean definition
    }

    def convertShibMetadata(isDirectory) {
        println "\nProcessing Shibboleth IdP CAS Xml metadata file(s)..."
        //TODO Add processing for Shibboleth CAS Service metadata
    }

    def consumeJSONCAS(file) {
        def json = new JsonSlurper().parseText(file.text)

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

    def processResult(casService, remainFileCount) {

        if (resultFormats.get(0).equalsIgnoreCase(resultFormat)) {
            //TODO CAS 5.x+ JSON output
        } else {
            //println "\nProcessing Service # [${remainFileCount}] with id [${casService.id}] with evaluationOrder [${casService.evaluationOrder}]"
            if (servicesStorage.containsKey(casService.evaluationOrder.toInteger())) {
                println "\nWARNING EvaluationOrder [${casService.evaluationOrder.toInteger()}] has duplicate(s)!"
                servicesStorage.get(casService.evaluationOrder.toInteger()).add(casService)
            } else {
                servicesStorage.put(casService.evaluationOrder.toInteger(), [casService])
            }

            if (casService.usernameAttribute && !casService.usernameAttribute.allWhitespace) {
                usernameStorage.get(casService.usernameAttribute).add(casService.serviceId)
            }

            if ((casService?.authorizedToReleaseProxyGrantingTicket == true || casService?.authorizedToReleaseCredentialPassword) && casService?.publicKeyLocation) {
                proxyStorage.get(casService.publicKeyLocation).add(casService.serviceId)
            }

            if (casService.releaseAttributes) {
                attributeStorage.get(casService.releaseAttributes.tokenize(',').toSet().asImmutable()).add((casService.name + casService.id))
            }

            if (remainFileCount < 1) {
                outputShibCASUsernames()
                outputShibCASServicesAndAttributes()
            }
        }
    }

    def outputShibCASUsernames() {
        def usernameFile = new File(resultLocation.getAbsolutePath() + File.separator + "relying-party.xml")
        println "\nCreating output file relying-party.xml in " + usernameFile.getPath()


        if (!usernameFile.exists()) {
            usernameFile.createNewFile()
            usernameFile.append("<!-- Place the beans below under RelyingPartyOverrides in idp_home/conf/relying-party.xml in your Shibboleth IdP. -->")
        }

        usernameStorage.each{ userIdAttribute, serviceIdList ->
            usernameFile.append(
                    "\n         <bean id=\"shibboleth.regexRelyingParty\" parent=\"RelyingParty\" >"
                            + "\n             <property name=\"activationCondition\" >"
                            + "\n                 <bean class=\"net.shibboleth.idp.profile.logic.RelyingPartyIdPredicate\" >"
                            + "\n                     <constructor-arg name=\"pred\" >"
                            + "\n                         <bean class=\"com.google.common.base.Predicates\" factory-method=\"or\" >"
                            + "\n                             <constructor-arg>"
                            + "\n                                 <util:list>")


            serviceIdList.each {
                usernameFile.append(
                        "\n                                     <bean class=\"com.google.common.base.Predicates\" factory-method=\"containsPattern\""
                                + "\n                                         c:_0=\"" + it + "\" />")
            }

            usernameFile.append(
                    "\n                                 </util:list>"
                            + "\n                             </constructor-arg>"
                            + "\n                         </bean>"
                            + "\n                     </constructor-arg>"
                            + "\n                 </bean>"
                            + "\n             </property>"
                            + "\n             <property name=\"profileConfigurations\">"
                            + "\n                 <list>"
                            + "\n                     <ref bean=\"CAS.LoginConfiguration\" />"
                            + "\n                     <ref bean=\"CAS.ProxyConfiguration\" />"
                            + "\n                     <ref bean=\"CAS.ValidateConfiguration\" p:userAttribute=\"" + userIdAttribute + "\" />"
                            + "\n                 </list>"
                            + "\n             </property>"
                            + "\n         </bean>")
        }
    }

    def outputShibCASServicesAndAttributes() {

        if (resultFormats.get(1).equalsIgnoreCase(resultFormat)) {
            def serviceFile = new File(resultLocation.getAbsolutePath() + File.separator + "cas-protocol.xml")
            println "\nCreating service/key file cas-protocol.xml in " + serviceFile.getPath()

            if (!serviceFile.exists()) {
                serviceFile.createNewFile()
                serviceFile.append("<!-- Place the beans below into idp_home/conf/cas-protocol.xml in your Shibboleth IdP."
                        + " Remember service order matters! -->")
            }

            def i = 0
            servicesStorage.keySet().sort().each{ id ->
                servicesStorage[id].each{ cs ->
                    i++
                    def slo = (cs.logoutType) ? true : false   //TODO Implement SLO?
                    def proxy = ((cs?.authorizedToReleaseProxyGrantingTicket == true || cs?.authorizedToReleaseCredentialPassword) && cs?.publicKeyLocation) ? true : false
                    serviceFile.append(
                            "\n                <!-- " + i + ". Name: " + cs.name + ", Description: " + cs.description + " Id: " + cs.id + ", EvalOrder: " + cs.evaluationOrder + " -->"
                                    + "\n                <bean class=\"net.shibboleth.idp.cas.service.ServiceDefinition\""
                                    + "\n                      c:regex=\"" + cs.serviceId + "\""
                                    + "\n                      p:group=\"" + cs.name + cs.id + "\""
                                    + "\n                      p:authorizedToProxy=\"" + proxy + "\""
                                    + "\n                      p:singleLogoutParticipant=\"" + slo + "\" />")
                }
            }

            println "\nThere were ${i} services/beans added to cas-protocol.xml!"

            if (proxyStorage.size() > 0) {
                serviceFile.append(
                        "\n\n<!-- Place the values below in the CASProxyTrustedCertificates list. Note double-check the values you may need to add paths! -->")
                proxyStorage.keySet().each {
                    serviceFile.append("\n    <value>" + it + "</value>")
                }
                serviceFile.append("\n\n")
            }
            println "\nFinished creating xml bean definitions!"
            outputShibCASAttributes(true)

        } else if (resultFormats.get(2).equalsIgnoreCase(resultFormat)) {
            def serviceMetadataFile = new File(resultLocation.getAbsolutePath() + File.separator + "cas-service-metadata.xml")
            println "\nCreating service metadata file cas-service-metadata.xml in " + serviceMetadataFile.getPath()

            if (!serviceMetadataFile.exists()) {
                serviceMetadataFile.createNewFile()
                serviceMetadataFile.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- Change the entityId below "
                        + " and place this file into a suitable location and reference via metadata provider. -->")
            }

            //TODO need to xml sanitize serviceId and figure out pattern match?
            //TODO implement proxy (if possible)!
            servicesStorage.keySet().sort().each{ id ->
                def cs = servicesStorage[id]
                serviceMetadataFile.append(
                        "\n<EntityDescriptor entityID=\"http://converted.cas.services.example/replace/with/something/better/\">"
                                + "\n    <SPSSODescriptor protocolSupportEnumeration=\"https://www.apereo.org/cas/protocol\">"
                                + "\n        <!-- " + cs.evaluationOrder + ". " + cs.name + ": " + cs.description + "; Former id: " + cs.id + " -->"
                                + "\n        <AssertionConsumerService"
                                + "\n                Binding=\"https://www.apereo.org/cas/protocol/login\""
                                + "\n                Location=\"" + cs.serviceId + "\""
                                + "\n                index=\"" + cs.evaluationOrder + "\"/>")
            }

            def slo = (cs.logoutType) ? true : false   //TODO Implement SLO?
            if (slo) {
                serviceMetadataFile.append(
                        "\n        <SingleLogoutService"
                                + "\n                Binding=\"https://www.apereo.org/cas/protocol/logout\""
                                + "\n                Location=\"urn:mace:shibboleth:profile:CAS:logout\"/>"
                                + "\n    </SPSSODescriptor>"
                                + "\n</EntityDescriptor>")
            } else {
                serviceMetadataFile.append(
                        "\n    </SPSSODescriptor>"
                                + "\n</EntityDescriptor>")
            }
            println "\nFinished creating metadata definitions!"
            outputShibCASAttributes(false)
        }
    }

    def outputShibCASAttributes(isBeanDef) {
        if (isBeanDef) {

            def attributeFile = new File(resultLocation.getAbsolutePath() + File.separator + "attribute-filter.xml")
            println "\nCreating attribute file attribute-filter.xml in " + attributeFile.getPath()

            if (!attributeFile.exists()) {
                attributeFile.createNewFile()
                attributeFile.append("<!-- Place the policies below into idp_home/conf/attribute-filter.xml in your Shibboleth IdP "
                        + " as a starting point and complete."
                        + " Double check attributeIDs match attribute-resolver config and that all are modified appropriately! -->")
            }

            attributeStorage.each{ attributeSet, serviceNameList ->
                if (attributeSet.size() < 2 && attributeSet.contains("default")) {
                    attributeFile.append(
                            "\n\n   <!-- Default CAS Service Attribute Release -->"
                                    + "\n   <AttributeFilterPolicy id=\"cas-default-attributes\">"
                                    + "\n       <PolicyRequirementRule xsi:type=\"OR\">")
                    serviceNameList.each {
                        attributeFile.append("\n           <Rule xsi:type=\"InEntityGroup\" groupID=\"" + it + "\" />")
                    }
                    attributeFile.append(
                            "\n       </PolicyRequirementRule>"
                                    + "\n       <AttributeRule attributeID=\"uid\" permitAny=\"true\" /> "
                                    + "\n  </AttributeFilterPolicy>")

                } else if (attributeSet.size() < 2 && attributeSet.contains("all")) {
                    attributeFile.append(
                            "\n\n    <!-- Release All CAS Service Attribute Release -->"
                                    + "\n    <AttributeFilterPolicy id=\"cas-all-attributes\">"
                                    + "\n        <PolicyRequirementRule xsi:type=\"OR\">")
                    serviceNameList.each {
                        attributeFile.append("\n           <Rule xsi:type=\"InEntityGroup\" groupID=\"" + it + "\" />")
                    }
                    attributeFile.append(
                            "\n       </PolicyRequirementRule>"
                                    + "\n       <!-- Insert All Attributes Here -->"
                                    + "\n  </AttributeFilterPolicy>")
                } else {
                    attributeFile.append(
                            "\n\n   <!-- Allowed CAS Service Attribute Release -->"
                                    + "\n    <AttributeFilterPolicy id=\"cas-allowed-attributes\">"
                                    + "\n        <PolicyRequirementRule xsi:type=\"OR\">")
                    serviceNameList.each {
                        attributeFile.append("\n           <Rule xsi:type=\"InEntityGroup\" groupID=\"" + it + "\" />")
                    }
                    attributeFile.append(
                            "\n       </PolicyRequirementRule>")
                    attributeSet.each {
                        attributeFile.append(
                                "\n       <AttributeRule attributeID=\"" + it + "\" permitAny=\"true\" />")
                    }
                    attributeFile.append(
                            "\n  </AttributeFilterPolicy>")
                }
            }
            println "\nFinished creating attribute definitions!"

        } else {
            //TODO Handle SAML Metadata Attribute Filtering!
        }
    }
}

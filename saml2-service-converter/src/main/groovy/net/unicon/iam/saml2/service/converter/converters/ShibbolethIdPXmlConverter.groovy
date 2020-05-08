package net.unicon.iam.saml2.service.converter.converters

import groovy.io.FileType
import net.unicon.iam.saml2.service.converter.result.ResultProcessor
import net.unicon.iam.saml2.service.converter.util.AttributeDefinition
import net.unicon.iam.saml2.service.converter.util.SAML2Service
import groovy.xml.Namespace


class ShibbolethIdPXmlConverter {

    private final static List<String> notes = [] //list of issues that need manual review
    private final static Map<String, String> spByMetadataName = [:] //SPs by MetadataFile Name (if provided)
    private final static Map<String, AttributeDefinition> attributes = [:] //Attributes by Shibboleth IdP ID (used to convert to LDAP names) key=id, value is ldap name
    private final static Map<String, List<String>> attributesToRelease = [:] //key is relyingPartyId, value is attributes to release
    private final static Map<String, String> nameIds = [:] //key is format and value is attributes
    private final static Map<String, SAML2Service> spByEntityId = [:] //key is entity Id, value is SAML2Service
    private final static Integer startEvaluationOrder = 1;


    static void convertShibbolethIdPXml(final File confDir, final ResultProcessor resultProcessor, final File metadataDir, final BigInteger startingId) {
        println "\n\nProcessing Shibboleth IdP Configuration at ${confDir.path}..."

        try {
            loadMetadata(metadataDir)
            loadAttributeResolver(confDir)
            loadAttributeFilter(confDir)
            loadRelyingParty(confDir)
            loadSamlNameId(confDir)
            consumeSPsFromMetadataProviders(confDir, resultProcessor, startingId)
            resultProcessor.storeMessages(notes)

            println "Processing Shibboleth IdP Configuration Complete!"
        } catch (Exception e) {
            println "Error processing Shibboleth IdP Configuration with exception " + e
        }
    }

    private static void loadMetadata(final File metadataDir) {
        if (metadataDir.exists()) {
            println "\n\nProcessing ${metadataDir.listFiles().length} possible metadata files..."
            def skipCount = 0
            def metadataCount = 0

            try {
                metadataDir.eachFileRecurse(FileType.FILES) { file ->
                    try {
                        if (file.name.endsWith(".xml") && !file.name.contains("InCommon") && !file.name.equalsIgnoreCase("idp-metadata")) {
                            def xml = new XmlParser().parse(file)
                            def entityIds = []

                            if (xml && xml?.attributes()?.containsKey("entityID")) {
                                entityIds.add(new XmlParser()?.parse(file)?.attributes()?.get("entityID")?.toString())

                            } else if (xml) {
                                def nodes = xml?.'**'?.findAll { node ->
                                    node?.attributes()?.find { it.key == "entityID"}
                                }

                                nodes.each { it ->
                                    entityIds.add(it?.attributes()?.get("entityID")?.toString())
                                 }
                            }

                            if (!entityIds.isEmpty()) {
                                spByMetadataName.put(file.name, entityIds.join("|"))
                                metadataCount++
                            } else {
                                notes.add("\nSkipped ${file.name} because it doesn't entityId or is an Aggregate!")
                                skipCount++
                            }

                        } else {
                            notes.add("\nSkipped ${file.name}'s entityId because it doesn't have XML extension or is an Aggregate!")
                            skipCount++
                        }
                    } catch (Exception e) {
                        println "Error processing metadata file with name ${file.name} with exception " + e
                        skipCount++
                    }
                }
               println "\n\nProcessed ${metadataCount - skipCount} out of ${metadataCount} Shibboleth IdP metadata Files!"

            } catch (Exception e) {
                println "Error processing Shibboleth IdP Metadata with exception " + e
            }

        } else {
            println "Skipping metadata processing since no metadata directory provided!"
        }
    }

    private static void loadAttributeResolver(final File confDir) {
        def definitionCount = 0
        def resolver = new Namespace("urn:mace:shibboleth:2.0:resolver")
        def xsi = new Namespace("http://www.w3.org/2001/XMLSchema-instance")
        println "Processing attribute-resolver.xml..."

        try {
            def attributeResolver = new XmlParser().parse(new File(confDir.getPath() + File.separator + "attribute-resolver.xml"))

            //TODO handle other formats (currently this is 3.xish format)
            attributeResolver[resolver.AttributeDefinition].each { definition ->
                try {
                    def attr = new AttributeDefinition()
                    def type = definition.attributes()[xsi.type].toString().trim()

                    attr.id = definition?.attributes()?.get("id")?.toString()?.trim()
                    attr.type = type

                    if (type.contains("Simple") || type.contains("Prescoped") || type.contains("Scoped")) {
                        attr.sourceId = definition?.attributes()?.get("sourceAttributeID")?.toString()?.trim()

                        if (attr.sourceId == null) {
                            attr.sourceId = definition[resolver.InputDataConnector]?.attributes()?.get("attributeNames")?.toString()?.trim()
                        }

                        attr.friendlyName = definition[resolver.AttributeEncoder].find({
                            it?.attributes()?.get(xsi.type)?.toString()?.contains("SAML2")
                        })?.attributes()?.get("friendlyName")?.toString()?.trim()
                        attr.saml2String = definition[resolver.AttributeEncoder].find({
                            it?.attributes()?.get(xsi.type)?.toString()?.contains("SAML2")
                        })?.attributes()?.get("name")?.toString()?.trim()

                    } else {
                        notes.add("Attribute with id " + attr.id + " needs to be handled manually! Type=" + type)
                    }

                    attributes.put(attr.id, attr)
                    definitionCount++

                } catch (Exception e) {
                    println "Error processing Shibboleth IdP Attributes with exception " + e
                }
            }

            attributeResolver[resolver.DataConnector].each { connector ->
                if (!connector?.attributes()[xsi.type]?.toString().contains("LDAPDirectory")) {
                    notes.add("Non-LDAP connector with id " + connector?.attributes()["id"]?.toString() + " needs to be handled manually! Type=" + connector?.attributes()[xsi.type]?.toString())
                }
            }

            notes.add("\n\nBEGIN List of possible encodings: ")
            notes.add(attributes.keySet().toString() + "\n")
            attributes.eachWithIndex{ k,v, int i ->
                notes.add("cas.authn.samlIdp.attributeFriendlyNames[" + i + "]=" + v.saml2String + "->" + v.friendlyName + "\n")
            }
            notes.add("END Attribute Encodings\n\n")

            println "\nProcessed ${definitionCount} Attribute Definitions in attribute-resolver.xml!"

        } catch (Exception e) {
            println "Error processing Shibboleth IdP attribute-resolver.xml file with exception " + e
        }
    }

    private static void loadAttributeFilter(final File confDir) {
        def xsi = new Namespace("http://www.w3.org/2001/XMLSchema-instance")
        def afp = new Namespace("urn:mace:shibboleth:2.0:afp")
        def filterCount = 0
        def notList = []
        final HashSet<String> requesters = new HashSet()
        println "Processing attribute-filter.xml..."

        try {
            def attributeFilter = new XmlParser().parse(new File(confDir.getPath() + File.separator + "attribute-filter.xml"))

            attributeFilter["AttributeFilterPolicy"].each { policy ->
                try {
                    final List<String> attrs = []
                    def type = policy[afp.PolicyRequirementRule][0]?.attributes()[xsi.type]
                    def id = policy?.attributes()?.get("id")

                    if (type?.contains("Requester") || type?.contains("OR") || type?.contains("AND")) {
                        policy[afp.AttributeRule].findAll {
                            it[afp.PermitValueRule][0]?.attributes()[xsi.type]?.contains("ANY")   //TODO harden this can cause errors
                        }.each {
                            attrs.add(it?.attributes()?.get("attributeID")?.toString()?.trim())
                        }

                        if (type?.contains("Requester")) {
                            def rpId = policy[afp.PolicyRequirementRule][0]?.attributes()["value"]?.toString()?.trim()
                            requesters.add(rpId)
                            attributesToRelease.put(rpId, attrs)

                        } else if (type?.contains("OR")) {
                            policy[afp.PolicyRequirementRule][0].findAll {
                                it?.attributes()[xsi.type] == "Requester"
                            }.each {
                                def rpId = it?.attributes()["value"]?.toString()?.trim()
                                requesters.add(rpId)
                                attributesToRelease.put(rpId, attrs)
                            }

                        } else { //AND
                            def ids = []
                            policy[afp.PolicyRequirementRule][0].findAll {
                                it?.attributes()[xsi.type] == "Requester"
                            }.each {
                                def rpId = it?.attributes()["value"]?.toString()?.trim()
                                ids.add(rpId)
                                requesters.add(rpId)
                                attributesToRelease.put(rpId, attrs)
                            }

                            if (policy[afp.PolicyRequirementRule][0].find {
                                it?.attributes()[xsi.type] == "NOT"
                            }) {
                                notList.add(ids)
                            } else {
                                notes.add("AttributeFilterPolicy special case with id " + id + " needs to be looked at!")
                            }
                        }

                        filterCount++

                    } else {
                        notes.add("AttributeFilterPolicy with id " + id + " needs to be handled manually! Type=" + type)
                    }

                } catch (Exception e) {
                    println "Error processing Shibboleth IdP Attribute Filter with exception " + e
                }
            }

            if (!notList.isEmpty()) {
                notes.add("RelyingParties with NOT Condition =" + notList.toString())
            }

            if (!requesters.isEmpty()) {
                notes.add("REQUESTER LIST = " + requesters.toString())
            }

            println "\nProcessed ${filterCount} Attribute Filter Policies in attribute-filter.xml!"
        } catch (Exception e) {
            println "Error processing Shibboleth IdP attribute-filter.xml file with exception " + e
        }
    }

    private static void loadRelyingParty(final File confDir) {
        def relyingPartyCount = 0
        def util = new Namespace("http://www.springframework.org/schema/util")
        def c = new Namespace("http://www.springframework.org/schema/c")
        def p = new Namespace("http://www.springframework.org/schema/p")
        println "Processing relying-party.xml..."

        try {
            def relyingParty = new XmlParser().parse(new File(confDir.getPath() + File.separator + "relying-party.xml"))

            relyingParty[util.list].find {
                it?.attributes()["id"] == "shibboleth.RelyingPartyOverrides"
            }.each { rp ->
                try {
                    def id = rp?.attributes()["id"] //may not be available
                    def parent = rp?.attributes()["parent"]

                    if (parent == "RelyingPartyByName") {
                        def ssoBean = rp.property.list.bean.find {
                            it?.attributes()["parent"] == "SAML2.SSO"
                        }

                        if (ssoBean && !ssoBean?.attributes()?.isEmpty()) {
                            def service = new SAML2Service()
                            def rpIds = rp?.attributes()[c.relyingPartyIds]?.toString()?.replaceAll("'|\"|}|#|\\{", "")?.trim()
                            service.requiredNameIdFormat = ssoBean?.attributes()[p.nameIDFormatPrecedence]?.toString()?.trim()
                            service.encryptAssertions = convertAlwaysNever(ssoBean?.attributes()[p.encryptAssertions]?.toString()?.trim())
                            service.signAssertions = convertAlwaysNever(ssoBean?.attributes()[p.signAssertions]?.toString()?.trim())
                            service.signResponses = convertAlwaysNever(ssoBean?.attributes()[p.signResponses]?.toString()?.trim())

                            rpIds.split(",").each {
                                spByEntityId.put(it.trim(), service)
                                relyingPartyCount++
                            }
                        } else {
                            notes.add("\nSkipped relyingParty " + id + " with parent " + parent)
                        }

                    } else {
                        notes.add("\nSkipped relyingParty " + id + " because it doesn't have SAML2.SSO bean or any properties!")
                    }

                } catch (Exception e) {
                    println "Error processing Shibboleth IdP Relying Party with exception " + e
                }
            }

            println "\nCompleted ${relyingPartyCount} Shibboleth IdP Relying Party Overrides in relying-party.xml!"

        } catch (Exception e) {
            println "Error processing Shibboleth IdP relying-party.xml with exception " + e
        }
    }

    private static void loadSamlNameId(final File confDir) {
        def nameIdBeanCount = 0
        def p = new Namespace("http://www.springframework.org/schema/p")
        def util = new Namespace("http://www.springframework.org/schema/util")
        println "Processing saml-nameid.xml..."

        try {
            def samlNameId = new XmlParser().parse(new File(confDir.getPath() + File.separator + "saml-nameid.xml"))

            def beans = samlNameId[util.list].find {
                it.'@id' == "shibboleth.SAML2NameIDGenerators"
            }

            beans.each { bean ->
                try {
                    if (bean.'@parent' == "shibboleth.SAML2AttributeSourcedGenerator") {
                        def format = bean?.attributes()[p.format]?.toString()
                        def attrs = bean?.attributes()[p.attributeSourceIds]?.toString()?.replaceAll("'|\"|}|#|\\{", "")?.trim()

                        nameIds.put(format, attrs)
                        nameIdBeanCount++
                        notes.add("NAMEID-" + format + " = [" + attrs + "]")
                    }
                } catch (Exception e) {
                    println "Error processing Shibboleth IdP SameNameId with exception " + e
                }
            }
            println "\nCompleted ${nameIdBeanCount} Shibboleth IdP NameId Overrides in saml-nameid.xml!"

        } catch (Exception e) {
            println "Error processing Shibboleth IdP saml-nameid.xml with exception " + e
        }
    }

    private static void consumeSPsFromMetadataProviders(final File confDir, final ResultProcessor processor, final BigInteger startId) {
        def providerCount = 0
        def xsi = new Namespace("http://www.w3.org/2001/XMLSchema-instance")
        def xmlns = new Namespace("urn:mace:shibboleth:2.0:metadata")
        println "Processing metadata-providers.xml..."

        try {
            def metadataProviders = new XmlParser().parse(new File(confDir.getPath() + File.separator + "metadata-providers.xml"))
            def startingId = startId

            metadataProviders["MetadataProvider"].each { provider ->
                try {
                    def type = provider?.attributes()[xsi.type]?.toString()?.trim()
                    def id = provider?.attributes()["id"]?.toString()?.trim()
                    startingId++
                    def metadataFile

                    if (type == "FilesystemMetadataProvider") {
                        def fileLocation = provider?.attributes()["metadataFile"].toString().trim()
                        metadataFile = fileLocation.substring(fileLocation.lastIndexOf("/")+1, fileLocation.length())

                        processor.storeResult(prepareSAML2Service(id, metadataFile, metadataFile,null, startingId))
                        providerCount++

                    } else if (type.contains("HTTP")) {
                        def metadataLocation = provider?.attributes()["metadataURL"].toString().trim()
                        def backingFile = provider?.attributes()["backingFile"].toString().trim()
                        metadataFile = backingFile.substring(backingFile.lastIndexOf("/")+1, backingFile.length())

                        def certFile = provider[xmlns.MetadataFilter].find {
                            it?.attributes()[xsi.type] == "SignatureValidation"
                        }?.attributes()

                        if (certFile != null) {
                            certFile = certFile["certificateFile"]?.toString()?.trim()
                            certFile = certFile.substring(certFile.lastIndexOf("/")+1, certFile.length())
                        }

                        processor.storeResult(prepareSAML2Service(id, metadataFile, metadataLocation, certFile, startingId))
                        providerCount++

                    } else {
                        notes.add("\nSkipped metadata provider with id " + id + " and type " + type)
                    }

                } catch (Exception e) {
                    println "Error processing Shibboleth IdP Metadata Provider with exception " + e
                }
            }

            outputRelyingPartyAndNameIdsToNotes()  //TODO Make Optional
            println "\nCompleted ${providerCount} Shibboleth IdP Metadata Providers found in metadata-providers.xml!"

        } catch (Exception e) {
            println "Error processing Shibboleth IdP metadata-providers.xml with exception " + e
        }
    }

    private static SAML2Service prepareSAML2Service(final String id, final String metadataFile, final String metadataLocation, final String certFile, final BigInteger startId) {
        def entityId = spByMetadataName.get(metadataFile)
        def sp = new SAML2Service()
        def releaseAttributes = []

        if (entityId && entityId?.trim()) {
            def rp = spByEntityId.containsKey(entityId) ? spByEntityId.get(entityId) : spByEntityId.find { it.key.contains(entityId) }

            if (rp) {
                //sp = rp
//                attributesToRelease.get(entityId).each { attrId ->
//                    def attrDef = attributes.get(attrId)
//                    if (attrDef) {
//                        releaseAttributes.add(attrDef.sourceId)  //TODO handle varios scenarios
//                        //        String attributeFormats
//                        //        String attributueTypes
//                        //        String nameIdQualifier
//                    }
//                }
//                sp.releaseAttributes = releaseAttributes
//
//                if (sp.requiredNameIdFormat && !sp.requiredNameIdFormat.isEmpty()) {
//                    sp.usernameAttribute = nameIds.get(sp.requiredNameIdFormat)
//                }
            }
            sp.serviceId = entityId //VERY IMPORTANT
        }

        if (!sp.serviceId) {
            sp.serviceId = ".+"
        }

        if (!metadataLocation.contains("http")) {
            sp.metadataLocation = "file:/etc/cas/services/sp-metadata/" + metadataLocation
        } else {
            sp.metadataLocation = metadataLocation
            if (certFile && !certFile.isEmpty()) {
                sp.metadataSignatureLocation = "file:/etc/cas/services/sp-metadata/" + certFile
            }
        }

        sp.id = startId.toString()
        sp.name = id
        sp.description = id
        sp.evaluationOrder = startEvaluationOrder.toString()
        startEvaluationOrder++

        return sp
    }

    private static String convertAlwaysNever(final String value) {
        if (value?.equalsIgnoreCase("Always")) {
            return "true"
        } else if (value?.equalsIgnoreCase("Never")) {
            return "false"
        } else {
            return value
        }
    }

    private static void outputRelyingPartyAndNameIdsToNotes() {
        notes.add("\n\n RELYING PARTIES \n")
        Set<String> attributesForRelease = new HashSet<>()


        //TODO refactor this use SAML2 Services Storage!!!!!
        spByEntityId.each { entityId,sp ->
            sp.serviceId = entityId
            def releaseAttributes = []

            attributesToRelease.get(entityId).each { attrId ->
                def attrDef = attributes.get(attrId)
                if (attrDef) {
                    releaseAttributes.add(attrDef.sourceId)
                }
            }
            sp.releaseAttributes = releaseAttributes.toString()

            if (sp.requiredNameIdFormat && !sp.requiredNameIdFormat.isEmpty()) {
       //         sp.usernameAttribute = nameIds.get(sp.requiredNameIdFormat)
            }
            notes.add("\n SP: serviceId=[" + sp.serviceId + "] nameId=[" + sp.usernameAttribute +
                    "] encryptAssertions=[" + sp.encryptAssertions + "] signAssertions=[" +
                    sp.signAssertions + "] signResponses=[" + sp.signResponses + "] releaseAttributes=[" + sp.releaseAttributes+ "]")

            if (releaseAttributes && releaseAttributes.size() > 0) {
                attributesForRelease.addAll(releaseAttributes)
            }
        }
        notes.add("\n Attributes for release = [" + attributesForRelease.toString() + "] \n")
        notes.add("\nEND Relying Parties! \n")
    }
}

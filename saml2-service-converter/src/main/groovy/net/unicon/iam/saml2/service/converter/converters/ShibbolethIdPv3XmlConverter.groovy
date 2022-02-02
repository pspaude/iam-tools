package net.unicon.iam.saml2.service.converter.converters

import net.unicon.iam.saml2.service.converter.result.ResultProcessor
import net.unicon.iam.saml2.service.converter.util.AttributeDefinition
import net.unicon.iam.saml2.service.converter.util.SAML2Service
import groovy.xml.Namespace


class ShibbolethIdPv3XmlConverter extends BaseShibbolethIdPXmlConverter {

    @Override
    void convertShibbolethIdPXml(final File confDir, final ResultProcessor resultProcessor, final File metadataDir, final BigInteger startingId, final BigInteger startingEvaluationId) {
        println "\n\nProcessing Shibboleth IdP Configuration at ${confDir.path}..."
        super.convertShibbolethIdPXml(confDir, resultProcessor, metadataDir, startingId, startingEvaluationId)

        try {
            loadMetadata(metadataDir)
            loadAttributeResolver(confDir)
            loadAttributeFilter(confDir)
            loadRelyingParty(confDir)
            loadSamlNameId(confDir)
            consumeSPsFromMetadataProviders(confDir, resultProcessor)
            resultProcessor.storeConversionMessages(notes)

            println "Processing Shibboleth IdP Configuration Complete!"
        } catch (Exception e) {
            println "Error processing Shibboleth IdP Configuration with exception " + e
        }
    }

    @Override
    protected void loadAttributeResolver(final File confDir) {
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

                        try {
                            def nameId = definition[resolver.AttributeEncoder].find({
                                it?.attributes()?.get(xsi.type)?.toString()?.contains("SAML2StringNameID")
                            })?.attributes()?.get("nameFormat")?.toString()?.trim()

                            if (nameId) {
                                nameIdsByFormat.put(nameId, attr.sourceId)
                            }
                        } catch (Exception e) {
                            //swallow
                        }

                        attr.friendlyName = definition[resolver.AttributeEncoder].find({
                            it?.attributes()?.get(xsi.type)?.toString()?.contains("SAML2")
                        })?.attributes()?.get("friendlyName")?.toString()?.trim()
                        attr.saml2String = definition[resolver.AttributeEncoder].find({
                            it?.attributes()?.get(xsi.type)?.toString()?.contains("SAML2")
                        })?.attributes()?.get("name")?.toString()?.trim()

                        try {
                            if (type.contains("Scoped")) {
                                def scope = definition?.attributes()?.get("scope")?.toString()
                                if (scope) {
                                    attr.scope = true
                                }
                            }
                        } catch (Exception e) {
                            //swallow
                            attr.scope = "HANDLE SCOPED ATTR MANUALLY ${attr.id}"
                        }

                        try {
                            if (type.contains("Mapped")) {
                                def returnValue = definition['ad:ValueMap']['ad:ReturnValue'][0].value().toString()
                                def sourceValue = definition['ad:ValueMap']['ad:SourceValue'][0].value().toString()

                                if (returnValue && sourceValue) {
                                    attr.map = new Tuple2<>(sourceValue, returnValue)
                                }
                            }
                        } catch (Exception e) {
                            //swallow
                            attr.map = new Tuple2<>("HANDLE MAPPED ATTR MANUALLY", attr.id)
                        }

                    } else {
                        notes.add("Attribute with id " + attr.id + " needs to be handled manually! Type=" + type)
                    }

                    attributesById.put(attr.id, attr)
                    definitionCount++

                } catch (Exception e) {
                    println "Error processing Shibboleth IdP Attributes with exception " + e
                }
            }

            attributeResolver[resolver.DataConnector].each { connector ->
                if (!connector?.attributes()[xsi.type]?.toString()?.contains("LDAPDirectory")) {
                    notes.add("Non-LDAP connector with id " + connector?.attributes()["id"]?.toString() + " needs to be handled manually! Type=" + connector?.attributes()[xsi.type]?.toString())
                }
            }

            println "\nProcessed ${definitionCount} Attribute Definitions in attribute-resolver.xml!"

        } catch (Exception e) {
            println "Error processing Shibboleth IdP attribute-resolver.xml file with exception " + e
        }
    }

    @Override
    protected void loadAttributeFilter(final File confDir) {
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

    @Override
    protected void loadRelyingParty(final File confDir) {
        def relyingPartyCount = 0
        def util = new Namespace("http://www.springframework.org/schema/util")
        def c = new Namespace("http://www.springframework.org/schema/c")
        def p = new Namespace("http://www.springframework.org/schema/p")
        println "Processing relying-party.xml..."

        try {
            def relyingPartyFile = new XmlParser().parse(new File(confDir.getPath() + File.separator + "relying-party.xml"))

            relyingPartyFile[util.list].find {
                it?.attributes()["id"] == "shibboleth.RelyingPartyOverrides"
            }.each { relyingParty ->
                try {
                    def id = relyingParty?.attributes()["id"] //may not be available
                    def parent = relyingParty?.attributes()["parent"]

                    if (parent == "RelyingPartyByName") {
                        def ssoBean = relyingParty.property.list.bean.find {
                            it?.attributes()["parent"] == "SAML2.SSO"
                        }

                        if (ssoBean && !ssoBean?.attributes()?.isEmpty()) {
                            def service = new SAML2Service()
                            def rpIds = relyingParty?.attributes()[c.relyingPartyIds]?.toString()?.replaceAll("'|\"|}|#|\\{", "")?.trim()
                            service.requiredNameIdFormat = ssoBean?.attributes()[p.nameIDFormatPrecedence]?.toString()?.trim()
                            service.encryptAssertions = convertAlwaysNever(ssoBean?.attributes()[p.encryptAssertions]?.toString()?.trim())
                            service.signAssertions = convertAlwaysNever(ssoBean?.attributes()[p.signAssertions]?.toString()?.trim())
                            service.signResponses = convertAlwaysNever(ssoBean?.attributes()[p.signResponses]?.toString()?.trim())

                            rpIds.split(",").each {
                                spByEntityId.put(it.trim(), service)
                                relyingPartyCount++
                            }
                        } else {
                            notes.add("\nSkipped relyingParty " + id + " because it doesn't have SAML2.SSO bean or any properties!")
                        }

                    } else {
                        notes.add("\nSkipped relyingParty " + id + " with parent " + parent)
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

    private void loadSamlNameId(final File confDir) {
        def nameIdBeanCount = 0
        def p = new Namespace("http://www.springframework.org/schema/p")
        def util = new Namespace("http://www.springframework.org/schema/util")
        def c = new Namespace("http://www.springframework.org/schema/c")
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

                        if (bean?.property?.get(0)?.toString()?.contains("activationCondition")) {
                            def propBean = bean?.property?.get(0)
                            if (propBean?.bean?.'@parent'?.toString()?.contains("RelyingPartyId")) {
                                if (propBean?.bean?.get(0)?.attributes()[c.candidate]) {
                                    nameIdsByRelyingParty.put(propBean?.bean?.get(0)?.attributes()[c.candidate]?.toString(), new Tuple2(format, attrs))
                                } else if (propBean?.bean?.get(0)?.attributes()[c.candidates]) {
                                    propBean?.bean?.get(0)?.attributes()[c.candidates].each { it ->
                                        nameIdsByRelyingParty.put(it.toString(), new Tuple2(format, attrs))
                                    }
                                }
                            }
                            //TODO handle other conditions/possibilities
                        } else {
                            nameIdsByFormat.put(format, attrs)
                        }
                        nameIdBeanCount++
                        notes.add("NAMEID-" + format + " = [" + attrs + "]")
                    }
                } catch (Exception e) {
                    println "Error processing Shibboleth IdP SameNameId with exception " + e
                }
            }
            println "\nCompleted ${nameIdBeanCount} Shibboleth IdP NameId Overrides in saml-nameid.xml!"

        } catch (FileNotFoundException fne) {
            println "Warn no saml-nameid.xml found skipping nameid processing! "
        } catch (Exception e) {
            println "Error processing Shibboleth IdP saml-nameid.xml with exception " + e
        }
    }

    @Override
    protected void consumeSPsFromMetadataProviders(final File confDir, final ResultProcessor processor) {
        def providerCount = 0
        def xsi = new Namespace("http://www.w3.org/2001/XMLSchema-instance")
        def xmlns = new Namespace("urn:mace:shibboleth:2.0:metadata")
        println "Processing metadata-providers.xml..."

        try {
            def metadataProvidersFile = new XmlParser().parse(new File(confDir.getPath() + File.separator + "metadata-providers.xml"))
            def startingId = startId

            //TODO handle MDQ, Dynamic, Folder, LocalDynamic types besides XML
            metadataProvidersFile["MetadataProvider"].each { provider ->
                try {
                    def type = provider?.attributes()[xsi.type]?.toString()?.trim()
                    def id = provider?.attributes()["id"]?.toString()?.trim()
                    startingId++
                    def metadataFile

                    if (type == "FilesystemMetadataProvider") {
                        def fileLocation = provider?.attributes()["metadataFile"].toString().trim()
                        metadataFile = fileLocation.substring(fileLocation.lastIndexOf("/")+1, fileLocation.length())

                        processor.storeResult(prepareSAML2Service(id, metadataFile, metadataFile,null))
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

            println "\nCompleted ${providerCount} Shibboleth IdP Metadata Providers found in metadata-providers.xml!"

        } catch (Exception e) {
            println "Error processing Shibboleth IdP metadata-providers.xml with exception " + e
        }

        //TODO process metadata files independent of metadata-providers.xml
    }
}

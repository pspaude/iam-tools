package net.unicon.iam.saml2.service.converter.converters

import net.unicon.iam.saml2.service.converter.result.ResultProcessor
import net.unicon.iam.saml2.service.converter.util.AttributeDefinition
import net.unicon.iam.saml2.service.converter.util.SAML2Service


class ShibbolethIdPv2XmlConverter extends BaseShibbolethIdPXmlConverter {

    @Override
    void convertShibbolethIdPXml(final File confDir, final ResultProcessor resultProcessor, final File metadataDir, final BigInteger startingId, final BigInteger startingEvaluationId) {
        println "\n\nProcessing Shibboleth IdP v2 Configuration at ${confDir.path}..."
        super.convertShibbolethIdPXml(confDir, resultProcessor, metadataDir, startingId, startingEvaluationId)

        try {
            loadMetadata(metadataDir)
            loadAttributeResolver(confDir)
            loadAttributeFilter(confDir)
            loadRelyingParty(confDir)
            consumeSPsFromMetadataProviders(confDir, resultProcessor)
            resultProcessor.storeConversionMessages(notes)

            println "Processing Shibboleth IdP v2 Configuration Complete!"
        } catch (Exception e) {
            println "Error processing Shibboleth IdP Configuration with exception " + e
        }
    }

    @Override
    protected void loadAttributeResolver(final File confDir) {
        def definitionCount = []
        println "Processing attribute-resolver.xml..."

        try {
            def attributeResolver = new XmlParser().parse(new File(confDir.getPath() + File.separator + "attribute-resolver.xml"))

            attributeResolver[resolver.AttributeDefinition].each { definition ->
                try {
                    def attr = new AttributeDefinition()
                    def type = definition.attributes()[xsi.type].toString().trim()

                    attr.id = definition?.attributes()?.get("id")?.toString()?.trim()
                    attr.type = type

                    if (type.contains("Simple") || type.contains("Prescoped") || type.contains("Scoped") || type.contains("Mapped")) {
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
                                    attr.scope = scope
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
                    definitionCount.add(attr.id)

                } catch (Exception e) {
                    println "Error processing Shibboleth IdP Attributes with exception " + e
                }
            }

            attributeResolver[resolver.DataConnector].each { connector ->
                if (!connector?.attributes()[xsi.type]?.toString()?.contains("LDAPDirectory")) {
                    notes.add("Non-LDAP connector with id " + connector?.attributes()["id"]?.toString() + " needs to be handled manually! Type=" + connector?.attributes()[xsi.type]?.toString())
                }
            }

            println "\nProcessed ${definitionCount.size()} Attribute Definitions in attribute-resolver.xml!"

        } catch (Exception e) {
            println "Error processing Shibboleth IdP attribute-resolver.xml file with exception " + e
        }
    }

    @Override
    protected void loadAttributeFilter(final File confDir) {
        def filterCount = []
        def notList = []
        final HashSet<String> requesters = new HashSet()
        println "Processing attribute-filter.xml..."

        try {
            def attributeFilter = new XmlParser().parse(new File(confDir.getPath() + File.separator + "attribute-filter.xml"))

            attributeFilter[afp.AttributeFilterPolicy].each { policy ->
                try {
                    final List<String> attrs = []
                    def type = policy[afp.PolicyRequirementRule][0]?.attributes()[xsi.type]
                    def id = policy?.attributes()?.get("id")

                    if (type?.contains("Requester") || type?.contains("OR") || type?.contains("AND")) {
                        policy[afp.AttributeRule].findAll {
                            it[afp.PermitValueRule][0]?.attributes()[xsi.type]?.contains("ANY")   //TODO harden this can cause errors!!!! DenyAttributeRule etc.
                        }.each {
                            attrs.add(it?.attributes()?.get("attributeID")?.toString()?.trim())
                        }

                        if (type?.contains("Requester")) {
                            def rpId = policy[afp.PolicyRequirementRule][0]?.attributes()["value"]?.toString()?.trim()
                            requesters.add(rpId)
                            attributesToRelease.put(rpId, attrs)

                        } else if (type?.contains("OR")) {
                            policy[afp.PolicyRequirementRule][0].findAll {
                                it?.attributes()[xsi.type] == "basic:AttributeRequesterString"
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

                        filterCount.add(id)

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

            println "\nProcessed ${filterCount.size()+1} Attribute Filter Policies in attribute-filter.xml!"
        } catch (Exception e) {
            println "Error processing Shibboleth IdP attribute-filter.xml file with exception " + e
        }
    }

    @Override
    protected void loadRelyingParty(final File confDir) {
        def relyingPartyCount = []
        println "Processing relying-party.xml..."

        try {
            def relyingPartyFile = new XmlParser().parse(new File(confDir.getPath() + File.separator + "relying-party.xml"))

            relyingPartyFile['rp:RelyingParty'].each { relyingParty ->
                try {
                    def service = new SAML2Service()
                    def id = relyingParty?.attributes()["id"] //may not be available

                    def ssoBean = relyingParty['rp:ProfileConfiguration'].find {
                        it.attributes()[xsi.type] == "saml:SAML2SSOProfile"
                    }

                    if (ssoBean && !ssoBean?.attributes()?.isEmpty()) {
                        service.encryptAssertions = convertAlwaysNever(ssoBean?.attributes()['encryptAssertions']?.toString()?.trim())
                        service.signAssertions = convertAlwaysNever(ssoBean?.attributes()['signAssertions']?.toString()?.trim())
                        service.signResponses = convertAlwaysNever(ssoBean?.attributes()['signResponses']?.toString()?.trim())

                        spByEntityId.put(id.trim(), service)
                        relyingPartyCount.add(id)
                    } else {
                        notes.add("\nSkipped relyingParty " + id + " because it doesn't have SAML2.SSO bean or any properties!")
                    }

                } catch (Exception e) {
                    println "Error processing Shibboleth IdP Relying Party with exception " + e
                }
            }

            println "\nCompleted ${relyingPartyCount.size()} Shibboleth IdP Relying Party Overrides in relying-party.xml!"

        } catch (Exception e) {
            println "Error processing Shibboleth IdP relying-party.xml with exception " + e
        }
    }

    @Override
    protected void consumeSPsFromMetadataProviders(final File confDir, final ResultProcessor processor) {
        def providerCount = []
        println "Processing metadata-providers.xml..."

        try {
            def metadataProvidersFile = new XmlParser().parse(new File(confDir.getPath() + File.separator + "relying-party.xml"))

            metadataProvidersFile['metadata:MetadataProvider']['metadata:MetadataProvider'].each { provider ->
                try {
                    def type = provider?.attributes()[xsi.type]?.toString()?.trim()
                    def id = provider?.attributes()["id"]?.toString()?.trim()
                    def metadataFile

                    if (type.toString().contains("FilesystemMetadataProvider")) {
                        def fileLocation = provider?.attributes()["metadataFile"].toString().trim()
                        metadataFile = fileLocation.substring(fileLocation.lastIndexOf("/")+1, fileLocation.length())

                        processor.storeResult(prepareSAML2Service(id, metadataFile, metadataFile,null))
                        providerCount.add(id)

                    } else if (type.toString().contains("HTTP")) {
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

                        processor.storeResult(prepareSAML2Service(id, metadataFile, metadataLocation, certFile))
                        providerCount.add(id)

                    } else {
                        notes.add("\nSkipped metadata provider with id " + id + " and type " + type)
                    }

                } catch (Exception e) {
                    println "Error processing Shibboleth IdP Metadata Provider with exception " + e
                }
            }

            println "\nCompleted ${providerCount.size()} Shibboleth IdP Metadata Providers found in metadata-providers.xml!"

        } catch (Exception e) {
            println "Error processing Shibboleth IdP metadata-providers.xml with exception " + e
        }
    }
}

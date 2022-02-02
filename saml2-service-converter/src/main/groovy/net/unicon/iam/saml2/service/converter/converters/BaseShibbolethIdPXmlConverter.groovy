package net.unicon.iam.saml2.service.converter.converters;

import groovy.io.FileType
import groovy.xml.Namespace
import net.unicon.iam.saml2.service.converter.result.ResultProcessor
import net.unicon.iam.saml2.service.converter.util.AttributeDefinition
import net.unicon.iam.saml2.service.converter.util.SAML2Service


abstract class BaseShibbolethIdPXmlConverter {

    protected static final Namespace c = new Namespace("http://www.springframework.org/schema/c")
    protected static final Namespace p = new Namespace("http://www.springframework.org/schema/p")
    protected static final Namespace xsi = new Namespace("http://www.w3.org/2001/XMLSchema-instance")
    protected static final Namespace resolver = new Namespace("urn:mace:shibboleth:2.0:resolver")
    protected static final Namespace afp = new Namespace("urn:mace:shibboleth:2.0:afp")
    protected static final Namespace xmlns = new Namespace("urn:mace:shibboleth:2.0:metadata")
    protected static final Namespace util = new Namespace("http://www.springframework.org/schema/util")
    protected static final Namespace rp = new Namespace("xmlns:rp=urn:mace:shibboleth:2.0:relying-party")

    protected static final List<String> notes = [] //list of issues that need manual review
    protected static final Map<String, String> spByMetadataName = [:] //SPs by MetadataFile Name (if provided)
    protected static final Map<String, AttributeDefinition> attributesById = [:] //Attributes by Shibboleth IdP ID (used to convert to LDAP names) key=id, value is ldap name
    protected static final Map<String, List<String>> attributesToRelease = [:] //key is relyingPartyId, value is attributes to release by id
    protected static final Map<String, String> nameIdsByFormat = [:] //key is nameId format and value is attributes that should be released as certain nameId
    protected static final Map<String, Tuple2<String, String>> nameIdsByRelyingParty = [:] //key is relyingPartyId and value is attribute that should be released as nameId
    protected static final Map<String, SAML2Service> spByEntityId = [:] //key is entity Id, value is SAML2Service

    protected static BigInteger startEvaluationOrder = 800
    protected static BigInteger startServiceId = 800


    protected abstract void loadAttributeResolver(final File confDir)
    protected abstract void loadAttributeFilter(final File confDir)
    protected abstract void loadRelyingParty(final File confDir)
    protected abstract void consumeSPsFromMetadataProviders(final File confDir, final ResultProcessor processor)


    BaseShibbolethIdPXmlConverter() {
    }

    public void convertShibbolethIdPXml(final File confDir, final ResultProcessor resultProcessor, final File metadataDir, final BigInteger startingId, final BigInteger startingEvaluationId) {
        if (startingId) {
            startServiceId = startingId
        }

        if (startingEvaluationId) {
            startEvaluationOrder = startingEvaluationId
        }
    }

    protected void loadMetadata(final File metadataDir) {
        if (metadataDir.exists()) {
            println "\n\nProcessing ${metadataDir.listFiles().length} possible metadata files..."
            def skipCount = 0
            def metadataCount = 0

            try {
                metadataDir.eachFileRecurse(FileType.FILES) {file ->
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
                println "\n\nProcessed ${metadataCount - skipCount+1} out of ${metadataCount+1} Shibboleth IdP metadata Files!"

            } catch (Exception e) {
                println "Error processing Shibboleth IdP Metadata with exception " + e
            }

        } else {
            println "Skipping metadata processing since no metadata directory provided!"
        }
    }

    protected SAML2Service prepareSAML2Service(final String id, final String metadataFile, final String metadataLocation, final String certFile) {
        def entityId = spByMetadataName.get(metadataFile)
        def sp = new SAML2Service()
        def releaseAttributes = []

        if (entityId && entityId?.trim()) {
            def rp = spByEntityId.containsKey(entityId) ? spByEntityId.get(entityId) : spByEntityId.find { it.key.contains(entityId) }

            if (rp) {
                sp = rp
            }
            sp.serviceId = entityId //VERY IMPORTANT

            attributesToRelease.get(entityId).each { attrId ->
                def attrDef = attributesById.get(attrId)
                if (attrDef && attrDef?.sourceId?.trim()) {
                    releaseAttributes.add(attrDef)
                    //TODO handle various scenarios
                    //        String attributeFormats
                    //        String attributeTypes
                    //        String nameIdQualifier
                }
            }
            sp.releaseAttributes = releaseAttributes

            if (sp.requiredNameIdFormat && !sp.requiredNameIdFormat.isEmpty()) {
                sp.usernameAttribute = nameIdsByFormat.get(sp.requiredNameIdFormat)
            } else if (nameIdsByRelyingParty.containsKey(entityId)) {
                def nameIdRp = nameIdsByRelyingParty.get(entityId)
                sp.requiredNameIdFormat = nameIdRp.first
                sp.usernameAttribute = nameIdRp.second
            }
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

        sp.id = startServiceId.toString()
        sp.name = id
        sp.description = id
        sp.evaluationOrder = startEvaluationOrder.toString()

        startServiceId++
        startEvaluationOrder++

        return sp
    }

    protected String convertAlwaysNever(final String value) {
        if (value?.equalsIgnoreCase("Always")) {
            return "true"
        } else if (value?.equalsIgnoreCase("Never")) {
            return "false"
        } else if (value?.equalsIgnoreCase("Conditional")) {
            return "true"
        } else {
            return value
        }
    }
}

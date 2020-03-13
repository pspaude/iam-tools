package net.unicon.iam.cas.service.converter.result

import net.unicon.iam.cas.service.converter.util.ResultFormats


class ShibbolethIdpResultProcessor extends ResultProcessor {

    ShibbolethIdpResultProcessor(resultLocation, resultFormat) {
        super(resultLocation, resultFormat)
    }

    @Override
    void processResults() {
        outputShibCASUsernames()
        outputShibCASServicesAndAttributes(resultFormat)
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

    def outputShibCASServicesAndAttributes(resultFormat) {

        if (resultFormat?.equalsIgnoreCase(ResultFormats.shibxml)) {
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

        } else if (resultFormat?.equalsIgnoreCase(ResultFormats.shibmetadata)) {
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

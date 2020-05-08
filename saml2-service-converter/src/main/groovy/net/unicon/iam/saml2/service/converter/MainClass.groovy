package net.unicon.iam.saml2.service.converter;

public class MainClass {

    static void main(String[] args) {
        println "\n\n*** Welcome to SAML 2 Service Configuration Converter! ***"
        new SAML2ServiceConverter(this.getResource('/converter.properties')).run()
        println "*** SAML2 Service Converter Complete! ***"
    }
}

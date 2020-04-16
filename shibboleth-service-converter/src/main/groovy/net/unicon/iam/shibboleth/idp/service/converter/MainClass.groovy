package net.unicon.iam.shibboleth.idp.service.converter;

public class MainClass {

    static void main(String[] args) {
        println "\n\n*** Welcome to Shibboleth IdP Service Configuration Converter! ***"
        new ShibbolethServiceConverter(this.getResource('/converter.properties')).run()
        println "*** Shibboleth Service Converter Complete! ***"
    }
}

package net.unicon.iam.cas.service.converter;

public class MainClass {

    static void main(String[] args) {
        println "\n\n*** Welcome to CAS Service Converter! ***"
        new CASServiceConverter(this.getResource('/converter.properties')).run()
        println "*** CAS Service Converter Complete! ***"
    }
}

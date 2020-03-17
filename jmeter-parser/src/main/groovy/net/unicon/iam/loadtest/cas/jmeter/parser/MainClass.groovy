package net.unicon.iam.loadtest.cas.jmeter.parser

public class MainClass {

    static void main(String[] args) {
        println "Starting"
        new LoadTestParser(this.getResource('/parser.properties')).run()
        println "Done"
    }
}

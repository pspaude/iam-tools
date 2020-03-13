package net.unicon.iam.jmeter.parser

import net.unicon.iam.jmeter.parser.LoadTestParser


public class MainClass {

    static void main(String[] args) {
        println "Starting"
        def script = new LoadTestParser()
        script.run()

        println "Done"
    }
}

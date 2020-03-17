package net.unicon.iam.motd;

public class MainClass {

    static void main(String[] args) {
        println "********** START *************"
        def script = new MOTDUpdater(this.getResource('/motd.properties')).run()
        println "********** END *************"
    }
}

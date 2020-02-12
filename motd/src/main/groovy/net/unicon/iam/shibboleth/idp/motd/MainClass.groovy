package net.unicon.iam.shibboleth.idp.motd;

public class MainClass {

    static void main(String[] args) {
        println "********** START *************"
        def script = new MOTDService()
        println "** Run 1 **"
        script.run()
        println "** End 1 **\n\n"
        println "** Run 2 **"
        script.run()
        println "** End 2 **\n\n"
        println "** Run 3 **"
        script.run()
        println "** End 3 **\n\n"
        println "** Run 4 **"
        script.run()
        println "** End 4 **\n\n"
        println "** Run 5 **"
        script.run()
        println "** End 5 **\n"
        println "********** END *************"
    }
}

package net.unicon.iam.motd

class LogN {

    def error (def message) {
        println "ERROR= " + message
    }

    def error (def message, def error) {
        println "ERROR= " + message + error
    }

    def warn (def message, def error) {
        println "WARN= " + message + error
    }

    def warn (def message) {
        println "WARN= " + message
    }

    def info (def message) {
        println "INFO= " + message
    }

    def debug (def message) {
        println "DEBUG= " + message
    }

    def debug (def message, def error) {
        println "DEBUG= " + message + error
    }

    def trace (def message) {
        println "TRACE= " + message
    }
}

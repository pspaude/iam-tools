package net.unicon.iam.loadtest.cas.jmeter.parser

class LoadTestParser implements Runnable {

    def existingLocation
    def outputLocation


    LoadTestParser(final URL configProps) {
        if (configProps) {
            println "CAS Jmeter Parser: Retrieving parser config..."
            def config = retrieveAndParsePropertiesFile(configProps.getPath()) //Retrieve existing parser.properties configuration
            existingLocation = config.getProperty("loadtest.cas.jmeter.currentresults").toString().trim()
            outputLocation = config.getProperty("loadtest.cas.jmeter.output").toString().trim()

        } else {
            println "CAS Jmeter Parser:  No Configuration found!"
        }
    }

    @Override
    void run() {
        println "CAS Jmeter Parser: Starting Load Test Parser"
        try {
            def testDir = new File(existingLocation)
            def counts = ["http": 0, "local": 0, "other": 0, "good": 0]

            testDir.eachFileRecurse { file ->
                try {
                    processJtlFileForCAS(file, counts)
                } catch (Exception e) {
                    println "CAS Jmeter Parser: Error processing results file ${file.getName()}! " + e
                }
            }

            printResults(counts)

        } catch (Exception e) {
            println "CAS Jmeter Parser: Error processing current results directory! " + e
        }
    }

    def processJtlFileForCAS(final File file, final Map<String,Integer> counts) throws Exception {
        def http = new File((outputLocation+"/httperrors.csv"))
        def local = new File((outputLocation+"/jmeterlocalerrors.csv"))
        def other = new File((outputLocation+"/othererrors.csv"))

        file.splitEachLine(",") {fields ->
            if (fields.size() >= 4) {

                def result = fields[3].trim()

                if (result.equalsIgnoreCase("200") || result.equalsIgnoreCase("302")) {
                    counts.put("good", counts.get("good")+1)
                    counts.compute("good", { k, v -> v + 1 })

                } else if (result.equalsIgnoreCase("401") || result.equalsIgnoreCase("500")) {
                    http.withWriterAppend { out ->
                        out.write(fields.toString() + System.lineSeparator())
                    }
                    counts.put("http", counts.get("http")+1)

                } else if (result.contains("SSLException") ||
                        result.contains("java.net.SocketException") ||
                        result.contains("java.net.SocketTimeoutException") ||
                        result.contains("ConnectTimeoutException") ||
                        result.contains("NoHttpResponseException") ||
                        result.contains("HttpHostConnectException") ||
                        result.contains(" UnknownHostException")) {
                    local.withWriterAppend { out ->
                        out.write(fields.toString() + System.lineSeparator())
                    }
                    counts.put("local", counts.get("local")+1)

                } else {
                    other.withWriterAppend { out ->
                        out.write(fields.toString() + System.lineSeparator())
                    }
                    counts.put("other", counts.get("other")+1)
                }
            }
        }
    }

    def printResults(final Map<String,Integer> counts) {
        try {
            def analysisFile = new File((outputLocation + "/analysis.txt"))
            analysisFile.withWriter { out ->
                out.write(createResults(counts))
            }
        } catch (Exception e) {
            println "CAS Jmeter Parser: Error writing organized results to ${outputLocation}! " + e
        }
    }

    def createResults(final Map<String,Integer> counts) {
        def text = new StringBuilder()
        text.append("Analysis of LoadTest Results" + System.lineSeparator())
        text.append("Success Count: " + counts.get("good") + "." + System.lineSeparator())
        text.append("HTTP Error Count: " + counts.get("http") + System.lineSeparator())
        text.append("Local Error Count: " + counts.get("local") + System.lineSeparator())
        text.append("Other Error Count : " + counts.get("other") + System.lineSeparator())

        def totalErrors = counts.get("http") + counts.get("local") + counts.get("other")
        text.append("Total Error Count: " + totalErrors + System.lineSeparator())

        return text.toString()
    }

    private def retrieveAndParsePropertiesFile (final String resource) {
        try {
            def returnProps = new Properties()
            def propsFile = new File(resource)

            propsFile.withInputStream {
                returnProps.load(it)
            }

            return returnProps

        } catch (Exception e) {
            println "CAS Jmeter Parser: Error couldn't find/parse " + resource + " file! Found error: " + e
            return null
        }
    }
}

package net.unicon.iam.jmeter.parser

class LoadTestParser implements Runnable {

    final String results = "/tmp/results"
    final String tests = "/tmp/tests"

    @Override
    void run() {
        println "Starting Load Test Parser"
        def testDir = new File(tests)
        def counts = ["http":0, "local":0, "other":0, "good":0]

        testDir.eachFileRecurse { file ->
            processJtlFileForCAS(file, counts)
        }

        printResults(counts)
    }

    def processJtlFileForCAS(final File file, final Map<String,Integer> counts) {
        def http = new File((results+"/httperrors.csv"))
        def local = new File((results+"/jmeterlocalerrors.csv"))
        def other = new File((results+"/othererrors.csv"))

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
        def analysisFile = new File((results+"/analysis.txt"))
        analysisFile.withWriter { out ->
            out.write(createResults(counts))
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
}

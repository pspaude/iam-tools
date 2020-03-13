package net.unicon.iam.cas.service.converter.result

import net.unicon.iam.cas.service.converter.util.CasService
import net.unicon.iam.cas.service.converter.util.ResultFormats


class CASJSONResultProcessor extends ResultProcessor {

    CASJSONResultProcessor(resultLocation, resultFormat) {
        super(resultLocation, resultFormat)
    }

    /**
     * Processes single CAS Service directly to CAS 5x JSON file
     * @param casService
     * @param remainFileCount
     * @return
     */
    @Override
    void processResults() {
        if (resultFormat?.equalsIgnoreCase(ResultFormats.cas5json)) {
            def fileCount = 0
            servicesStorage.keySet().sort().each { id ->
                servicesStorage[id].each { cs ->
                    try {
                        fileCount++
                        def serviceFile = new File(resultLocation.getAbsolutePath() + File.separator + createCASServiceFileName(cs.name, fileCount, cs.id))

                        if (!serviceFile.exists()) {
                            serviceFile.createNewFile()
                            serviceFile.append(casServiceTo5xJSON(cs))
                        }
                    } catch (Exception e) {
                        println "Error creating CAS 5x+ JSON File" + e
                    }
                }
            }
            println "Created ${fileCount} CAS 5.x+ JSON Service Files"
        } else {
            //Do nothing not the right format
        }
    }

    def createCASServiceFileName(final String serviceName, int fileCount, int id) {
        def name
        def end = "-"
        end = ((id) ? (end + id + ".json") : (end + fileCount + ".json"))

        if (!serviceName.isEmpty()) {
            name = serviceName.trim().replaceAll("\\s+", "_")
        } else {
            name = "CAS_Service"
        }

        return name + end
    }

    def casServiceTo5xJSON(final CasService cs) {
        def begin = "{" + System.lineSeparator()
        def end = "\", " + System.lineSeparator()
        def finalEnd = "\" " + System.lineSeparator() + "}" + System.lineSeparator()

        def builder = new StringBuilder()
        builder.append(begin)
        builder.append("\"@class\" : \"org.apereo.cas.services.RegexRegisteredService, \"" + System.lineSeparator())
        builder.append("\"serviceId\" : \"" + cs.serviceId + end)
        builder.append("\"name\" : \"" + cs.name + end)
        builder.append("\"id\" : \"" + cs.id + end)
        builder.append("\"evaluationOrder\" : \"" + cs.evaluationOrder + finalEnd)

        //TODO implement rest!

    }
}

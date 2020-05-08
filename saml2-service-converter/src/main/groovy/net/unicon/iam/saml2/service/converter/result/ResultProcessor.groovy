package net.unicon.iam.saml2.service.converter.result

import net.unicon.iam.saml2.service.converter.util.SAML2Service


abstract class ResultProcessor {

    protected final TreeMap servicesStorage = [:]   //used for storing services in order to print out results based on evaluation order, value is list of services for that order
    protected final Map usernameStorage = [:].withDefault { key -> return [] }  //key is username to release, value is list of serviceIds that release that username
    protected final Map attributeStorage = [:].withDefault { key -> return [] } //key is set of release attributes, value is list of serviceIds that release those values (none, default, and all are also possible keys)

    protected final Set<String> fileNamesUsed = [] //list of file names already used
    protected final List messageList = [] // list of messages (ususally skipped config or callouts to manually look at an SP)

    protected final File resultLocation
    protected final String resultFormat


    ResultProcessor(resultLocation, resultFormat) {
        this.resultLocation = resultLocation
        this.resultFormat = resultFormat
    }

    abstract void processResults()

    void storeResult(final SAML2Service saml2Service) {
        if (saml2Service) {
            //println "\nProcessing Service # [${remainFileCount}] with id [${saml2Service.id}] with evaluationOrder [${saml2Service.evaluationOrder}]"
            if (servicesStorage.containsKey(saml2Service.evaluationOrder.toInteger())) {
                println "\nWARNING EvaluationOrder [${saml2Service.evaluationOrder.toInteger()}] has duplicate(s)!"
                servicesStorage.get(saml2Service.evaluationOrder.toInteger()).add(saml2Service)
            } else {
                servicesStorage.put(saml2Service.evaluationOrder.toInteger(), [saml2Service])
            }

            if (saml2Service.usernameAttribute && !saml2Service.usernameAttribute.allWhitespace) {
                usernameStorage.get(saml2Service.usernameAttribute).add(saml2Service.serviceId)
            }

            if (saml2Service.releaseAttributes) {
                attributeStorage.get(saml2Service.releaseAttributes.tokenize(',').toSet().asImmutable()).add((saml2Service.name + saml2Service.id))
            }
        }
    }

    void storeNameId(final SAML2Service saml2Service) {
        if (saml2Service.usernameAttribute && !saml2Service.usernameAttribute.allWhitespace) {
            usernameStorage.get(saml2Service.usernameAttribute).add(saml2Service.serviceId)
        }
    }

    void storeAttributes(final SAML2Service saml2Service) {
        if (saml2Service.releaseAttributes) {
            attributeStorage.get(saml2Service.releaseAttributes.tokenize(',').toSet().asImmutable()).add((saml2Service.name + saml2Service.id))
        }
    }

    void storeMessages(final List<String> messages) {
        messageList.addAll(messages)
    }

}

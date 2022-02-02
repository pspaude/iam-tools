package net.unicon.iam.saml2.service.converter.result

import net.unicon.iam.saml2.service.converter.util.AttributeDefinition
import net.unicon.iam.saml2.service.converter.util.SAML2Service

import java.util.stream.Collectors


abstract class ResultProcessor {

    protected static final TreeMap servicesStorage = [:]   //used for storing services in order to print out results based on evaluation order, value is list of services for that order
    protected static final Map usernameStorage = [:].withDefault { key -> return [] }  //key is username to release, value is list of serviceIds that release that username
    protected static final Map attributeStorage = [:].withDefault { key -> return [] } //key is set of release attributes, value is list of serviceIds that release those values (none, default, and all are also possible keys)

    protected static final Set<String> fileNamesUsed = [] //list of file names already used
    protected static final List messageList = [] // list of messages (usually skipped config or callouts to manually look at an SP)

    protected final File resultLocation
    protected final String resultFormat


    ResultProcessor(resultLocation, resultFormat) {
        this.resultLocation = resultLocation
        this.resultFormat = resultFormat
    }

    abstract void processResults()
    abstract void outputNotes()

    static void storeResult(final SAML2Service saml2Service) {
        if (saml2Service) {
            //println "\nProcessing Service # [${remainFileCount}] with id [${saml2Service.id}] with evaluationOrder [${saml2Service.evaluationOrder}]"
            storeService(saml2Service)
            storeNameId(saml2Service)
            storeAttributes(saml2Service)
        }
    }

    static void storeService(final SAML2Service saml2Service) {
        if (servicesStorage.containsKey(saml2Service.evaluationOrder.toInteger())) {
            println "\nWARNING EvaluationOrder [${saml2Service.evaluationOrder.toInteger()}] has duplicate(s)!"
            servicesStorage.get(saml2Service.evaluationOrder.toInteger()).add(saml2Service)
        } else {
            servicesStorage.put(saml2Service.evaluationOrder.toInteger(), [saml2Service])
        }
    }

    static void storeNameId(final SAML2Service saml2Service) {
        if (saml2Service.usernameAttribute && !saml2Service.usernameAttribute.allWhitespace) {
            usernameStorage.get(saml2Service.usernameAttribute).add(saml2Service.serviceId)
            usernameStorage.get(saml2Service.usernameAttribute).add(saml2Service.serviceId)
        }
    }

    static void storeAttributes(final SAML2Service saml2Service) {
        if (saml2Service.releaseAttributes) {
            def attributeList = saml2Service.releaseAttributes.stream()
                    .filter({it != null})
                    .map({ it -> it.sourceId })
                    .collect(Collectors.toSet()).asImmutable()

            attributeStorage.get(attributeList).add((saml2Service.name + saml2Service.id))
        }
    }

    static void storeConversionMessages(final List<String> messages) {
        messageList.addAll(messages)
    }
}

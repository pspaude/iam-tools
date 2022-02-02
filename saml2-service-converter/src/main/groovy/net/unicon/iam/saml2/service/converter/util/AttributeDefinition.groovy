package net.unicon.iam.saml2.service.converter.util

class AttributeDefinition {
    String id
    String sourceId
    String friendlyName
    String saml2String
    String type
    String scope
    Tuple2<String,String> map //sourceValue, returnValue

    AttributeDefinition(final String id) {
        this.id = id
    }
}

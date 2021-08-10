package net.unicon.iam.cas.service.converter.util


class CasService {
    String serviceId
    String name
    String id
    String description
    String evaluationOrder
    String usernameAttribute
    String logoutType
    String mfaProviders
    String mfaFailureMode
    Map<String, String> mfaPrincipalAttributeTriggers
    String releaseAttributes
    String authorizedToReleaseCredentialPassword
    String authorizedToReleaseProxyGrantingTicket
    String publicKeyLocation
    String publicKeyAlgorithm
    String ssoEnabled
    String enabled
    String allowedToProxy
    String anonymousAccess
    String theme
    Map<String, String> staticAttributes
}
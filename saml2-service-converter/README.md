# SAML2 Service Provider Configuration Conversion

Tool for converting SAML2 Service Provider definitions between Shibboleth IdP and Apereo CAS Server definitions.

Set options in the `src/main/resources/converter.properties` file.
- currentdirorfile =   Current SAML2 location (should point to CAS JSON Services location or Shibboleth IdP idp_home/conf directories)
- currentformat =   Current service format
- resultformat =   The output format
- resultlocation =   The output location
- startingid = The starting Id to use (in the case of outputting to CAS 5x+ JSON)
- metadatalocation = The location of file based metadata (in the case of outputting to CAS 5x JSON and you want entityId to be populated automatically)

Current formats accepted: 
-    casjson (not available yet)
-    shib3x
    
Result formats accepted:
-    cas5json
-    shib4x (not available yet) //TODO May not be possible due to attribute resolver changes

Using those options above, Shibboleth Service Converter will convert Shibboleth service configuration
to the specified format and location.

**Example:** 
```
converter.currentdirorfile=/tmp/idp_home/conf
converter.currentformat=shib3x
converter.resultlocation=/tmp/new_cas/etc/cas/services
converter.resultformat=cas5json
converter.startingid=1
converter.metadatalocation=/tmp/idp_home/metadata
``` 

**Usage:**

`./gradlew run`
# Shibboleth Service Provider Configuration Conversion

Tool for converting between Shibboleth IdP configuration formats and also between CAS JSON definitions.

Set options in the `src/main/resources/converter.properties` file.
- currentdirorfile =   Current service location (can be file or directory path)
- currentformat =   Current service format
- resultformat =   The output format
- resultlocation =   The output location

Current formats accepted: 
-    cas3json (not available yet)
-    casjson (not available yet)
-    shib3x
    
Result formats accepted:
-    cas5json
-    shib4x (not available yet)

Using those options above, Shibboleth Service Converter will convert Shibboleth service configuration
to the specified format and location.

**Example:** 
```
converter.currentdirorfile=/tmp/idp_home/conf
converter.currentformat=shib3x
converter.resultlocation=/tmp/new_cas/etc/cas/services
converter.resultformat=cas5json
``` 

**Usage:**

`./gradlew run`
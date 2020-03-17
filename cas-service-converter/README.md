# CAS and Shibboleth Service Conversion

Tool for converting between CAS JSON formats and also between Shibboleth IdP CAS bean definitions.

Set options in the `src/main/resources/converter.properties` file.
- currentdirorfile =   Current service location (can be file or directory path)
- currentformat =   Current service format
- resultformat =   The output format
- resultlocation =   The output location

Current formats accepted: 
-    cas3json
-    casjson
-    shibxml (not available yet)
-    shibmetadata (not available yet)
    
Result formats accepted:
-    cas5json
-    shibxml
-    shibmetadata

Using those options above, CAS Service Converter will convert CAS services
to the specified format and location.

**Example:** 
```
converter.currentdirorfile=/tmp/old_cas_services/services.conf
converter.currentformat=cas3json
converter.resultlocation=/tmp/new_cas_services
converter.resultformat=shibxml
``` 

**Usage:**

`./gradlew run`
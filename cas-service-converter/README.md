# CAS and Shibboleth Service Conversion

Tool for converting between CAS JSON formats and also between Shibboleth IdP CAS bean definitions.

CLI options (use -help for help), if not using help it must contain all 4!
- --currentdir `arg`     =   Current service location (can be file or directory path)
- --currentformat `arg`  =   Current service format
- --resultformat `arg`   =   The output format
- --resultlocation `arg` =   The output location

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
./gradlew run -currentdir /tmp/old_cas_services -currentformat cas3json -resultlocation /tmp/new_cas_services -resultformat shibxml
``` 
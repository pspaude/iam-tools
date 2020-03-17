# motd

Implements a Message of the Day feature by consuming a remote URL and outputting into a
 messages.properties file. Use with cron or other task scheduling tool.
 
 MOTD Remote Format (text,  text file, or JSON):
 
 ```
Header="Some Header Here"
Body="Some Body and information <a href="http://www.example.com">here</a>!!!"
Effective=2020-01-28 23:59
Expiration=2020-01-29 23:59
```
 
This app allows up to two messages per Remote MOTD endpoint to allow for message rollover. Finally 
the Body can wrap multiple lines and that functionality is configurable.

TODO go over all possible configurations here!
- Configuration in `src/main/resources/motd.properties`
- Errors to Mail Server
- Errors to SNS Topic
- Remote MOTD formatting configurations
- Error throttling
 
 
 
Usage

`./gradlew run`
#!/bin/bash

# This script parses Shibboleth IdP default Audit Logs by Entity Id and Outputs Username
#
# Note: If the audit format varies and the username is not the 9th piped value, then this script 
#    must be adjusted!
#
# Usage: ./parseIdPAudit.sh entityIds.txt
#
# The argument is a file that should contain the list of entityIds each on one line. This script
# will parse through the input file and for each entityId output all usernames found in the 
# log directory configured below. 
#
# Output: results.txt
# 
#  This file contains information on which log file the usernames/userIds etc. are 
#  found in and a comma separated list if any are found.
#

LOG_DIRECTORY=/path/to/log/files
OUT_FILE=results.txt
LINES=$(cat $1)

for ENTITY_ID in $LINES; 
do
  echo "Processing: $ENTITY_ID..."
  
  echo -e "\n$ENTITY_ID=" >> $OUT_FILE
  grep -ri "$ENTITY_ID" $LOG_DIRECTORY/* | cut -d "|" -f9 | sort | uniq -u | tr '\n' ',' >> $OUT_FILE
  echo -e "\n" >> $OUT_FILE
  
  echo "Complete"
done

echo "parseIdPAudit complete!"

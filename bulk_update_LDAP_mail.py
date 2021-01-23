#Python Script to use Python ldap3 library to modify attribute(s) in any LDAP compliant server
#Created by Unicon Inc. 01/22/2021

## Instructions
# 1. Make sure you have Python version 3 installed: 
#
# python3 --version  or  python --version
# Adjust commands (python or python3) below accordingly.
#
# 2. Make sure dependencies are available: (Note you may have to adjust depending on your OS.)
#
# apt-get install python3 python-dev python3-dev build-essential libssl-dev libsasl2-dev libldap2-dev python-pip
#
# 3. Install the LDAP module/library:
# 
#  python3 -m pip install python-ldap
#
# 4. Edit the script below for your LDAP Server Configuration and run the script!
#
## End Instructions


# Imports
from ldap3 import Server, Connection, Tls, SUBTREE, MODIFY_REPLACE, ALL
import ssl

#LDAP Connection Info
ldapServer = "ldaps://ldapssl"
port = 10636
useSsl = True
bindDn = "cn=admin,dc=examplessl,dc=edu"
password = "admin"
baseDn = "dc=examplessl,dc=edu"
searchFilter = "(objectClass=*)"
searchAttributes = ["cn", "mail"]
uniqueIdentifier="uid"
pageSize = 5

#Attribute Config
mailAttribute = "mail"  #Set to LDAP Server's mail attribute
domain = "@mygreatexample.edu" #Set this to the desired domain

#Configuration Options
shouldUpdateDomain=True #If False, no modifications to LDAP will be made! If True modifications to at least entries that don't have a domain in the mail attribute will be made.
shouldModifyEntriesThatHaveDomainButDoNotMatch=True #If False and the above config is True, this will not modify entries that have a mail attribute and a domain, but the domain doesn't match. If True it will modify and make all mail attibutes consistent irregadless of existing domain.

#Open Connection and Bind to the LDAP server
print ("LDAP Change/Modify Attribute Script Starting...\n")
tls_configuration = Tls(validate=ssl.CERT_REQUIRED, version=ssl.PROTOCOL_TLSv1)
ldap_server = Server(ldapServer, port, use_ssl=useSsl, get_info=ALL)
ldap_connection = Connection(ldap_server, bindDn, password, auto_bind=True)

#Begin LDAP Search
if ldap_connection.result["description"] != "success":
    print ("ERROR connecting to LDAP! LDAP Response: " + ldap_connection.connection.result["description"])
    ldap_connection.unbind()
else:
    totalLDAPEntriesFound = 0
    entriesWithEmailDomain = 0
    entriesWithoutAttributes = []
    entriesWithoutMailAttributes = []
    entriesWithoutMailDomain = {}
    entriesWithoutRequiredMailDomain = {}
    entriesNotProcessedDueToError = {}
    
    try:
        print ("\nSearching LDAP...")
        entries = ldap_connection.extend.standard.paged_search(
            search_base=baseDn, 
            search_filter=searchFilter,
            search_scope=SUBTREE,
            get_operational_attributes=True,
            attributes=searchAttributes,
            generator=True,
            paged_size=pageSize
        )
        
        for entry in entries:
            totalLDAPEntriesFound += 1

            try:
                dn = str(entry["dn"])

                if (not entry["attributes"]):
                    # If the entry doesn't have any attributes (almost impossible) we can't process it
                    entriesWithoutAttributes.append(dn)
                elif (uniqueIdentifier not in dn):
                    # We don't want to process non users such as OUs 
                    totalLDAPEntriesFound -= 1
                else:
                    mail = " ".join(entry["attributes"]["mail"])          

                    if (not mail):
                        entriesWithoutMailAttributes.append(dn)
                    else:
                        if ("@" not in mail):
                            entriesWithoutMailDomain[dn]=mail
                        elif (domain not in mail):
                            entriesWithoutRequiredMailDomain[dn]=mail
                        else:
                            entriesWithEmailDomain += 1
            
            except Exception as ie:
                entriesNotProcessedDueToError[dn]=("Error= [" + str(ie) + "] occurred while processing [" + dn + "].")
    
    except Exception as e:
        print ("LDAP Error occurred: " + str(e))
        print(e.with_traceback)
        ldap_connection.unbind()

print("\nLDAP Search complete!")   
#End LDAP Search 

#Print Results of LDAP Search Above
print("\n\nFound " + str(totalLDAPEntriesFound) + " total LDAP entry records.")
print("\nFound " + str(entriesWithEmailDomain) + " LDAP entries with domains in mail attribute.")

print("\nFound " + str(len(entriesWithoutAttributes)) + " LDAP entries without attributes!")
if len(entriesWithoutAttributes) > 0:
    print ("DNs without attributes are: " + " ".join(entriesWithoutAttributes))

print("\nFound " + str(len(entriesWithoutMailAttributes)) + " LDAP entries without a mail attribute.")
if len(entriesWithoutMailAttributes) > 0:
    print ("DNs without or empty mail attribute are: \n" + "\n".join(entriesWithoutMailAttributes))

print("\nFound " + str(len(entriesNotProcessedDueToError)) + " LDAP entries that were processed with error.")
if len(entriesNotProcessedDueToError) > 0:
    print ("DNs searched with error are: ")
    for dn, error in entriesNotProcessedDueToError.items():
        print("{} ({})".format(dn, error))

print("\nFound " + str(len(entriesWithoutRequiredMailDomain)) + " LDAP entries that did not contain a matching domain in the mail attribute.")
if len(entriesWithoutRequiredMailDomain) > 0:
    print ("DNs that don't contain a matching domain in mail attribute are: ")
    for dn, error in entriesWithoutRequiredMailDomain.items():
        print("{} ({})".format(dn, error))

print("\nFound " + str(len(entriesWithoutMailDomain)) + " LDAP entries that did not contain any domain in the mail attribute.")
if len(entriesWithoutMailDomain) > 0:
    print ("DNs without mail domain are: ")
    for dn, error in entriesWithoutMailDomain.items():
        print("{} ({})".format(dn, error))


#Add/Update mail attribute with domain
if (shouldUpdateDomain):
    print ("\n\nAdding or Updating domain on mail attribute for users that don't have it or the required domain...")
    totalLDAPEntriesModified = 0
    ldapEntriesModifiedWithError = {}
    ldapEntriesModifiedToAddDomainWhereNonExisted = {}
    ldapEntriesModifiedToUpdateDomainToMatchRequired = {}

    for dn, mail in entriesWithoutMailDomain.items():
        
        totalLDAPEntriesModified += 1
        try:
            newMailAttribute = mail + domain
            ldap_connection.modify(dn, {mailAttribute: [(MODIFY_REPLACE, [newMailAttribute])]})
            
            if (ldap_connection.result["description"] != "success"):
                ldapEntriesModifiedWithError[dn]=("Error occurred while adding domain to mail attribute with description: " + ldap_connection.result["description"])
            else:
                ldapEntriesModifiedToAddDomainWhereNonExisted[dn]=(mail, newMailAttribute)

        except Exception as me:
            ldapEntriesModifiedWithError[dn]=("Error occurred while adding domain to mail attribute with error: " + str(me))
    
    if (shouldModifyEntriesThatHaveDomainButDoNotMatch):
        for dn, mail in entriesWithoutRequiredMailDomain.items():
            
            totalLDAPEntriesModified += 1
            try:
                mailWithoutDomain = mail.split("@", 1)[0]
                newMailAttribute = mailWithoutDomain + domain
                ldap_connection.modify(dn, {mailAttribute: [(MODIFY_REPLACE, [newMailAttribute])]})
                
                if (ldap_connection.result["description"] != "success"):
                    ldapEntriesModifiedWithError[dn]=("Error occurred while adding domain to mail attribute due to required not matching with description: " + ldap_connection.result["description"])
                else:
                    ldapEntriesModifiedToUpdateDomainToMatchRequired[dn]=(mail, newMailAttribute)
                
            except Exception as mre:
                ldapEntriesModifiedWithError[dn]=("Error occurred while adding required domain to mail attribute with error: " + str(mre))      

    ldap_connection.unbind()
    print("\nAdd/Update LDAP Operation Complete!")
    
    #Print Modify Operation results
    print("\n\nModified " + str(totalLDAPEntriesModified) + " total LDAP entries.")
    print("\nModified " + str(len(ldapEntriesModifiedWithError)) + " LDAP entries with error.")
    if len(ldapEntriesModifiedWithError) > 0:
        print ("DNs modified with error were: ")
        for dn, error in ldapEntriesModifiedWithError.items():
            print("{} ({})".format(dn, error))

    print("\nModified " + str(len(ldapEntriesModifiedToUpdateDomainToMatchRequired)) + " LDAP entries that did not contain a matching domain in the mail attribute.")
    if len(ldapEntriesModifiedToUpdateDomainToMatchRequired) > 0:
        print ("DNs modifed were: (format is dn (old, new))")
        for dn, error in ldapEntriesModifiedToUpdateDomainToMatchRequired.items():
            print("{} ({})".format(dn, error))

    print("\nModified " + str(len(ldapEntriesModifiedToAddDomainWhereNonExisted)) + " LDAP entries that did not contain any domain in the mail attribute.")
    if len(ldapEntriesModifiedToAddDomainWhereNonExisted) > 0:
        print ("DNs modifed were: (format is dn (old, new))")
        for dn, error in ldapEntriesModifiedToAddDomainWhereNonExisted.items():
            print("{} ({})".format(dn, error))
    #End Print Modify Results

else:
    ldap_connection.unbind()
    print("\nShould Update config was False so no changes made to LDAP!")    
#End Add/Update LDAP Operation


print ("\n\nLDAP Script Completed!")
#End of Script

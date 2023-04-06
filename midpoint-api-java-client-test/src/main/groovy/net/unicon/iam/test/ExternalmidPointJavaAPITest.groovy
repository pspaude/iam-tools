package net.unicon.iam.test

import com.evolveum.midpoint.client.impl.restjaxb.*
import org.apache.cxf.transport.local.LocalConduit;

public class ExternalmidPointJavaAPITest implements Runnable {

    private final LogN log = new LogN()

    ExternalmidPointJavaAPITest() {
    }

    void run() {
        log.info("TEST: Processing started.")

        def ENDPOINT_ADDRESS = "https://midpoint.local/midpoint/ws/rest/";
        def ADMIN = "administrator";
        def ADMIN_PASS = "5ecr3t";

        log.info("Building Service")

        try {
            def serviceBuilder = new RestJaxbServiceBuilder().password(ADMIN_PASS);
            def service = serviceBuilder.authentication(AuthenticationType.BASIC).username(ADMIN).url(ENDPOINT_ADDRESS).build();
            service.getClientConfiguration().getRequestContext().put(LocalConduit.DIRECT_DISPATCH, Boolean.TRUE);
            def rest = service

            log.info("Service Complete. Performing actions...")
            def user = rest.users().oid("c94df118-0881-44af-bbdf-72da21d95357").get();

            if (user) {
                def oid = user.getOid()
                log.info("User is: [" + user.getName() + "]!")

                def modifications = new HashMap<>();
                modifications.put("extension/isactivestudent", "Y");

                log.info("Modifying...")
                //Uncomment below for fun! Will need to use the SQL update statement below to fix!
//                def result = rest.users().oid(oid)
//                        .modify()
//                        .replace(modifications)
//                        .post();

//                log.info("Result: " + result)
                def modifiedUser = rest.users().oid("c94df118-0881-44af-bbdf-72da21d95357").get();  // check results
                log.info("User " + modifiedUser.getName() + " was possibly modified value is now: [" + modifiedUser.getExtension() + "].") //TODO

            } else {
                log.error("User wasn't found!")
            }

            //SELECT cast(fullobject::TEXT as jsonb) FROM "public"."m_user" WHERE oid = 'c94df118-0881-44af-bbdf-72da21d95357';
            //To fix the user:
            // UPDATE m_user SET fullobject = REPLACE(fullobject::TEXT, '"isactivestudent":"Y"', '"isactivestudent":"true"')::bytea WHERE oid = 'c94df118-0881-44af-bbdf-72da21d95357';

        } catch (Exception e) {
            log.error("ERROR: " + e)
        }

        log.info("TEST: Processing complete see above for success or errors.")
    }
}

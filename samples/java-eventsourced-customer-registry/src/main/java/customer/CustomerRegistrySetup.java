package customer;

import akka.javasdk.JsonSupport;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES;

// NOTE: This default ACL settings is very permissive as it allows any traffic from the internet.
// Our samples default to this permissive configuration to allow users to easily try it out.
// However, this configuration is not intended to be reproduced in production environments.
// Documentation at https://docs.kalix.io/java/access-control.html
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
// tag::object-mapper[]
public class CustomerRegistrySetup implements ServiceSetup {
  // end::object-mapper[]

  private static final Logger logger = LoggerFactory.getLogger(CustomerRegistrySetup.class);

  // tag::object-mapper[]

  @Override
  public void onStartup() {
    // end::object-mapper[]
    logger.info("Starting Akka Application");
    // tag::object-mapper[]
    JsonSupport.getObjectMapper()
            .configure(FAIL_ON_NULL_CREATOR_PROPERTIES, true); // <1>
  }
}
// end::object-mapper[]
package customer;

import akka.platform.javasdk.JsonSupport;
import akka.platform.javasdk.ServiceLifecycle;
import akka.platform.javasdk.annotations.Acl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES;

// NOTE: This default ACL settings is very permissive as it allows any traffic from the internet.
// Our samples default to this permissive configuration to allow users to easily try it out.
// However, this configuration is not intended to be reproduced in production environments.
// Documentation at https://docs.kalix.io/java/access-control.html
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
// tag::object-mapper[]
public class Main implements ServiceLifecycle {
  // end::object-mapper[]

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  // tag::object-mapper[]

  @Override
  public void onStartup() {
    // end::object-mapper[]
    logger.info("Starting Kalix Application");
    // tag::object-mapper[]
    // FIXME should this perhaps be a pre-start rather?
    JsonSupport.getObjectMapper()
            .configure(FAIL_ON_NULL_CREATOR_PROPERTIES, true); // <1>
  }
}
// end::object-mapper[]
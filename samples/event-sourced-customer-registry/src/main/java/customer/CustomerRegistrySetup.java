package customer;

import akka.javasdk.DependencyProvider;
import akka.javasdk.JsonSupport;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import customer.application.CustomerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES;

// tag::object-mapper[]
@Setup
public class CustomerRegistrySetup implements ServiceSetup {
  // end::object-mapper[]

  private static final Logger logger = LoggerFactory.getLogger(CustomerRegistrySetup.class);

  @Override
  public DependencyProvider createDependencyProvider() {
    return DependencyProvider.single(new CustomerStore());
  }


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
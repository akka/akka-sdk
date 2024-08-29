package customer.api;


import akka.javasdk.http.HttpClient;
import akka.javasdk.http.StrictResponse;
import akka.util.ByteString;
import customer.api.CustomerRegistryEndpoint.CreateCustomerRequest;
import customer.application.CustomersByNameView;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test exercises the integration between the current service (customer-registry-subscriber) and the customer-registry service.
 * <p>
 * The customer registry service is started as a Docker container as well as it own Akka Runtime. The current service is
 * started as a local JVM process (not dockerized), but its own Akka Runtime starts as a docker container.
 * The `docker-compose-integration.yml` file is used to start all these services.
 * <p>
 * The subscriber service will first create a customer on customer-registry service. The customer will be streamed back
 * to the subscriber service and update its view.
 * <p>
 * This test will exercise the following:
 * - service under test can read settings from docker-compose file and correctly configure itself.
 * - resolution of service port mappings from docker-compose file allows for cross service calls (eg: create customer from subscriber service)
 * - resolution of service port mappings passed to akka-runtime allows for service to service streaming (eg: customer view is updated in subscriber service)
 */
public class CustomerIntegrationTest extends CustomerRegistryIntegrationTest {

  final private Duration timeout = Duration.of(5, SECONDS);
  private Logger logger = LoggerFactory.getLogger(getClass());


  // this test relies on a source Akka service to which it subscribes. Such service should be running on :9000
  @Test
  public void create()  {
    // start the real test now
    String id = UUID.randomUUID().toString();
    CreateCustomerRequest createRequest = new CreateCustomerRequest(id, "foo@example.com", "Johanna", new CustomerRegistryEndpoint.Address("street", "city"));

    //TODO improve this when dealing with 2 HttpClients
    HttpClient client = createClient("http://localhost:9001");

    // try until it succeeds
    Awaitility.await()
      .ignoreExceptions()
      .pollInterval(5, TimeUnit.SECONDS)
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {

        StrictResponse<ByteString> res = await(
          client.POST("/customer/create")
            .withRequestBody(createRequest).invokeAsync()
        );

        assertThat(res.httpResponse().status().isSuccess()).isTrue();
      });


    // the view is eventually updated
    // on this service (updated via s2s streaming)
    Awaitility.await()
      .ignoreExceptions()
      .pollInterval(5, TimeUnit.SECONDS)
      .atMost(60, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        String name = await(
          componentClient.forView()
            .method(CustomersByNameView::findByName)
            .invokeAsync(new CustomersByNameView.QueryParameters("Johanna"))
        ).customers().stream().findFirst().get().name();

        assertThat(name).isEqualTo("Johanna");
      });
  }

}

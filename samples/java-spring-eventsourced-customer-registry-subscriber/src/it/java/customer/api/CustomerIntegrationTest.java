package customer.api;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import customer.actions.CustomerRegistryAction;
import customer.views.Customer;
import kalix.javasdk.JsonSupport;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;

/**
 * This test exercises the integration between the current service (customer-registry-subscriber) and the customer-registry service.
 * <p>
 * The customer registry service is started as a Docker container as well as it own Kalix Runtime. The current service is
 * started as a local JVM process (not dockerized), but its own Kalix Runtime starts as a docker container.
 * The `docker-compose-integration.yml` file is used to start all these services.
 * <p>
 * The subscriber service will first create a customer on customer-registry service. The customer will be streamed back
 * to the subscriber service and update its view.
 * <p>
 * This test will exercise the following:
 * - service under test can read settings from docker-compose file and correctly configure itself.
 * - resolution of service port mappings from docker-compose file allows for cross service calls (eg: create customer from subscriber service)
 * - resolution of service port mappings passed to kalix-runtime allows for service to service streaming (eg: customer view is updated in subscriber service)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CustomerIntegrationTest {

  final private Duration timeout = Duration.of(5, SECONDS);

  // FIXME integration test support for starting another service as a prerequisite - would still be docker?

  public CustomerIntegrationTest(ApplicationContext applicationContext) {
    Map<String, Object> confMap = new HashMap<>();
    // don't kill the test JVM when terminating the KalixRunner
    confMap.put("kalix.system.akka.coordinated-shutdown.exit-jvm", "off");
    confMap.put("kalix.dev-mode.docker-compose-file", "docker-compose-integration.yml");
    confMap.put("kalix.user-function-interface", "0.0.0.0");

    Config config = ConfigFactory.parseMap(confMap).withFallback(ConfigFactory.load());

    // kalixSpringApplication = new KalixSpringApplication(applicationContext, config);
  }


  private HttpStatusCode assertSourceServiceIsUp(WebClient webClient) {
    try {
      return webClient.get()
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
          Mono.empty()
        )
        .toBodilessEntity()
        .block(timeout)
        .getStatusCode();

    } catch (WebClientRequestException ex) {
      throw new RuntimeException("This test requires an external kalix service to be running on localhost:9000 but was not able to reach it.");
    }
  }

  /* create the client but only return it after verifying that service is reachable */
  private WebClient createClient(String url) {

    var webClient =
      WebClient
        .builder()
        .baseUrl(url)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .codecs(configurer ->
          configurer.defaultCodecs().jackson2JsonEncoder(
            new Jackson2JsonEncoder(JsonSupport.getObjectMapper(), MediaType.APPLICATION_JSON)
          )
        )
        .build();

    // wait until customer service is up
    await()
      .ignoreExceptions()
      .pollInterval(5, TimeUnit.SECONDS)
      .atMost(5, TimeUnit.MINUTES)
      .until(() -> assertSourceServiceIsUp(webClient),
        new IsEqual(HttpStatus.NOT_FOUND)  // NOT_FOUND is a sign that the customer registry service is there
      );

    return webClient;
  }


  /**
   * This test relies on a source Kalix service to which it subscribes. Such service should be running on :9000
   */
  @Test
  public void create()  {

    createClient("http://localhost:9000");
    WebClient localWebClient = createClient("http://localhost:9001");

    // start the real test now  
    String id = UUID.randomUUID().toString();
    CustomerRegistryAction.Customer customer = new CustomerRegistryAction.Customer("foo@example.com", "Johanna", new CustomerRegistryAction.Address("street", "city"));

        // try until it succeeds
    await()
      .ignoreExceptions()
      .pollInterval(5, TimeUnit.SECONDS)
      .atMost(60, TimeUnit.SECONDS)
      .until(() ->
        localWebClient.post()
        .uri("/customer/" + id + "/create")
        .bodyValue(customer)
        .retrieve()
        .bodyToMono(CustomerRegistryAction.Confirm.class)
        .block(timeout).msg(),
        new IsEqual<>("done")
      );


    // the view is eventually updated
    // on this service (updated via s2s streaming)
    await()
      .ignoreExceptions()
      .pollInterval(5, TimeUnit.SECONDS)
      .atMost(60, TimeUnit.SECONDS)
      .until(() ->
          localWebClient.get()
            .uri("/customers/by_name/Johanna")
            .retrieve()
            .bodyToFlux(Customer.class)
            .blockFirst()
            .name(),
        new IsEqual("Johanna")
      );
  }

}

package customer.api;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import kalix.javasdk.testkit.KalixTestKit;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;

public abstract class CustomerRegistryIntegrationTest extends KalixIntegrationTestKitSupport {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private HttpClient httpClient;

  @BeforeAll
  public void beforeAll() {
    Map<String, Object> confMap = new HashMap<>();
    // don't kill the test JVM when terminating the KalixRunner
    confMap.put("kalix.system.akka.coordinated-shutdown.exit-jvm", "off");
    confMap.put("kalix.dev-mode.service-port-mappings.customer-registry", "localhost:9000");
    // avoid conflits with upstream service using port 9000 and 25520
    confMap.put("kalix.proxy.http-port", "9001");
    confMap.put("akka.remote.artery.canonical.port", "25521");

    Config config = ConfigFactory.parseMap(confMap);

    try {
      kalixTestKit = (new KalixTestKit(kalixTestKitSettings())).start(config);
      componentClient = kalixTestKit.getComponentClient();
    } catch (Exception ex) {
      logger.error("Failed to startup Kalix service", ex);
      throw ex;
    }

    // createClient("http://localhost:9000");
  }

/* FIXME non spring webclient
  protected HttpStatusCode assertSourceServiceIsUp(WebClient webClient) {
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

  // create the client but only return it after verifying that service is reachable
  protected WebClient createClient(String url) {

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
    Awaitility.await()
        .ignoreExceptions()
        .pollInterval(5, TimeUnit.SECONDS)
        .atMost(5, TimeUnit.MINUTES)
        .until(() -> assertSourceServiceIsUp(webClient),
            new IsEqual(HttpStatus.NOT_FOUND)  // NOT_FOUND is a sign that the customer registry service is there
        );

    return webClient;
  }

 */

}

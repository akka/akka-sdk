package customer.api;

import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import akka.platform.javasdk.http.HttpClient;
import akka.platform.javasdk.testkit.KalixTestKit;
import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    httpClient = createClient("http://localhost:9000");
  }


  protected StatusCode assertSourceServiceIsUp(HttpClient httpClient) {
    try {
      return await(httpClient.GET("")
          .invokeAsync()
      ).httpResponse().status();
    } catch (Exception ex) {
      throw new RuntimeException("This test requires an external kalix service to be running on localhost:9000 but was not able to reach it.", ex);
    }
  }

  // create the client but only return it after verifying that service is reachable
  protected HttpClient createClient(String url) {

    var httpClient = new akka.platform.javasdk.http.HttpClient(kalixTestKit.getActorSystem(), url);

    // wait until customer service is up
    Awaitility.await()
        .ignoreExceptions()
        .pollInterval(5, TimeUnit.SECONDS)
        .atMost(5, TimeUnit.MINUTES)
        .until(() -> assertSourceServiceIsUp(httpClient),
            new IsEqual(StatusCodes.NOT_FOUND)  // NOT_FOUND is a sign that the customer registry service is there
        );

    return httpClient;
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }
}

package customer.api;

import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import akka.javasdk.http.HttpClient;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class CustomerRegistryIntegrationTest extends TestKitSupport {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private HttpClient httpClient;

  @BeforeAll
  public void beforeAll() {
    Map<String, Object> confMap = new HashMap<>();
    // avoid conflicts with upstream service using port 9000
    // FIXME why is this kalix.proxy.http-port and why doesn't akka.runtime.dev-mode.http-port=9000 work?
    confMap.put("kalix.proxy.http-port", "9001");

    Config config = ConfigFactory.parseMap(confMap);

    try {
      testKit = (new TestKit(kalixTestKitSettings())).start(config);
      componentClient = testKit.getComponentClient();
    } catch (Exception ex) {
      logger.error("Failed to startup Akka service", ex);
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
      throw new RuntimeException("This test requires an external Akka service to be running on localhost:9000 but was not able to reach it.", ex);
    }
  }

  // create the client but only return it after verifying that service is reachable
  protected HttpClient createClient(String url) {

    var httpClient = new HttpClient(testKit.getActorSystem(), url);

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

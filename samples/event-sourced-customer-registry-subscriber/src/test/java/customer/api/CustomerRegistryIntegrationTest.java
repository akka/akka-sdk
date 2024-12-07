package customer.api;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.typesafe.config.ConfigFactory;

import java.util.Map;

/**
 * Needs to be a base class for all integration tests in this project to avoid port conflict
 * since CI will already be running another service on port 9000
 */
public abstract class CustomerRegistryIntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    // note, we need to use kalix.proxy keys because this config object
    // won't participate in the config transfer in the runtime
    var customPortConfig = ConfigFactory.parseMap(Map.of(
        "akka.javasdk.dev-mode.http-port", "9001"));
    return TestKit.Settings.DEFAULT.withAdditionalConfig(customPortConfig);
  }
}

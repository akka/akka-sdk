package counter.application;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.http.HttpClient;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestKit;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// tag::class[]
public class CounterWithRealPubSubIntegrationTest extends TestKitSupport { // <1>

// end::class[]
    
  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withEventingSupport(TestKit.Settings.EventingSupport.GOOGLE_PUBSUB);
  }

  @Test
  public void verifyCounterEventSourcedConsumesFromPubSub() {
    // using random id to ensure isolation when running tests locally
    // with a pubsub container since the container preserves state
    var counterId = UUID.randomUUID().toString();

    var msg = """
        { "counterId": "%s", "value":20 }
      """.formatted(counterId);

    var messageBody = buildMessageBody(msg, CounterCommandFromTopicConsumer.IncreaseCounter.class.getName());

    var pubSubClient = new HttpClient(testKit.getActorSystem(), "http://localhost:8085");
    var response = pubSubClient.POST("/v1/projects/test/topics/counter-commands:publish")
            .withRequestBody(ContentTypes.APPLICATION_JSON, messageBody.getBytes(StandardCharsets.UTF_8))
            .invokeAsync();

    assertEquals(StatusCodes.OK, await(response).httpResponse().status());

    var getCounterState =
      componentClient.forEventSourcedEntity(counterId).method(CounterEntity::get);


    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .until(() -> await(getCounterState.invokeAsync(), Duration.ofSeconds(3)), new IsEqual<>(20));
  }
  // end::test-topic[]

  // builds a message in PubSub format, ready to be injected
  private String buildMessageBody(String jsonMsg, String ceType) {
    var data = Base64.getEncoder().encodeToString(jsonMsg.getBytes());

    return """
      {
          "messages": [
              {
                  "data": "%s",
                  "attributes": {
                      "Content-Type": "application/json",
                      "ce-specversion": "1.0",
                      "ce-type": "%s"
                  }
              }
          ]
      }
      """.formatted(data, ceType);
  }

// tag::class[]
}
// end::class[]

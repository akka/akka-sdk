package com.example;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import com.example.actions.CounterCommandFromTopicAction;
import kalix.javasdk.testkit.KalixTestKit;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// tag::class[]
public class CounterIntegrationWithRealPubSubTest extends KalixIntegrationTestKitSupport { // <1>

// end::class[]
    
  @Override
  protected KalixTestKit.Settings kalixTestKitSettings() {
    return KalixTestKit.Settings.DEFAULT.withAclEnabled()
      .withEventingSupport(KalixTestKit.Settings.EventingSupport.GOOGLE_PUBSUB);
  }

  @Test
  public void verifyCounterEventSourcedConsumesFromPubSub() {
    var counterId = "testRealPubSub";
    var messageBody = buildMessageBody(
      "{\"counterId\":\"" + counterId + "\",\"value\":20}",
      CounterCommandFromTopicAction.IncreaseCounter.class.getName());

    var projectId = "test";
    var pubSubClient = new kalix.javasdk.http.HttpClient(kalixTestKit.getActorSystem(), "http://localhost:8085");
    var response = pubSubClient.POST("/v1/projects/" + projectId + "/topics/counter-commands:publish")
            .withRequestBody(ContentTypes.APPLICATION_JSON, messageBody.getBytes(StandardCharsets.UTF_8))
            .invokeAsync();
    assertEquals(StatusCodes.OK, await(response, Duration.ofSeconds(3)).httpResponse().status());

    var getCounterState =
      componentClient.forEventSourcedEntity(counterId)
        .method(Counter::get);


    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(
        () -> getCounterState.invokeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS),
        new IsEqual("20"));
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

package com.example.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKitSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExampleA2aEndpointIntegrationTest extends TestKitSupport {

  // tag::test[]
  public void asksForThePolicyNumberAndCompletes() throws Exception {
    var first = send(message("Water damage in the kitchen", null)); // <1>
    assertThat(first.get("status").get("state").asText()).isEqualTo(
      "TASK_STATE_INPUT_REQUIRED"
    ); // <2>

    var second = send(message("P-1234", first.get("id").asText())); // <3>
    assertThat(second.get("status").get("state").asText()).isEqualTo("TASK_STATE_COMPLETED");
  }

  private JsonNode send(Map<String, Object> message) throws Exception {
    var request = Map.of(
      "jsonrpc",
      "2.0",
      "id",
      1,
      "method",
      "SendMessage",
      "params",
      Map.of("message", message)
    );
    var response = httpClient
      .POST("/a2a")
      .addHeader("Authorization", "Bearer " + unsignedToken()) // <4>
      .withRequestBody(
        ContentTypes.APPLICATION_JSON,
        JsonSupport.getObjectMapper().writeValueAsBytes(request)
      )
      .invoke();
    return JsonSupport.getObjectMapper()
      .readTree(response.body().utf8String())
      .get("result")
      .get("task");
  }

  // end::test[]

  private static Map<String, Object> message(String text, String taskId) {
    var message = new LinkedHashMap<String, Object>();
    message.put("messageId", UUID.randomUUID().toString());
    message.put("role", "ROLE_USER");
    message.put("parts", List.of(Map.of("text", text)));
    message.put("metadata", Map.of("skillId", "review-claim"));
    if (taskId != null) message.put("taskId", taskId);
    return message;
  }

  private static String unsignedToken() {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    var header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes());
    var claims = encoder.encodeToString(
      "{\"iss\":\"https://idp.example.com\",\"sub\":\"tester\"}".getBytes()
    );
    return header + "." + claims + ".";
  }
}

/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.eventsourcedentities.counter.Counter;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.views.counter.CountersByValue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class ViewAsToolIntegrationTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(CounterQueryAgent.class, testModelProvider);
  }

  @AfterEach
  public void clearModelProviderState() {
    testModelProvider.reset();
  }

  private String newSessionId() {
    return UUID.randomUUID().toString();
  }

  /** An agent that uses CountersByValue view as a tool to query counters. */
  @Component(
      id = "counter-query-agent",
      name = "CounterQueryAgent",
      description = "Agent that queries counters using view tools")
  public static class CounterQueryAgent extends Agent {

    public record QueryRequest(int value) {}

    public record QueryResponse(String message, Counter counter) {}

    public Effect<QueryResponse> findCounterByValue(QueryRequest request) {
      return effects()
          .systemMessage("You are a counter query agent.")
          .tools(CountersByValue.class)
          .userMessage("Find a counter with value " + request.value)
          .responseConformsTo(Counter.class)
          .map(
              counter -> {
                return new QueryResponse("Counter query completed", counter);
              })
          .thenReply();
    }
  }

  @Test
  public void agentShouldCallViewToQueryCounter() throws InterruptedException {
    String counterId = "counter-456";
    int counterValue = 77;

    // Step 1: Directly increase the counter using the entity
    componentClient
        .forEventSourcedEntity(counterId)
        .method(CounterEntity::increase)
        .invoke(counterValue);

    // Step 2: Wait for the view to be updated and verify directly
    // Poll the view until it's updated (views are eventually consistent)
    Awaitility.await()
        .ignoreExceptions()
        .atMost(15, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var byValue =
                  componentClient
                      .forView()
                      .method(CountersByValue::getCounterByValue)
                      .invoke(CountersByValue.queryParam(counterValue));

              assertThat(byValue).hasValue(new Counter(counterValue));
            });

    // Step 3: Now use the agent to query the view with the tool
    testModelProvider
        .whenMessage(s -> s.contains("Find a counter with value"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "CountersByValue_getCounterByValue",
                "{\"params\": { \"value\":" + counterValue + "}}"));

    // Final response after querying the view
    testModelProvider
        .whenToolResult(result -> result.name().equals("CountersByValue_getCounterByValue"))
        // the returned value is sent back as structured content - expected to be Optional<Counter>
        .thenReply(result -> new TestModelProvider.AiResponse(result.content()));

    // Execute the agent
    var response =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(CounterQueryAgent::findCounterByValue)
            .invoke(new CounterQueryAgent.QueryRequest(counterValue));

    // Verify the agent response
    assertThat(response).isNotNull();
    assertThat(response.message()).isEqualTo("Counter query completed");
    assertThat(response.counter.value()).isEqualTo(counterValue);
  }
}

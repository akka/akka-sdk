/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.eventsourcedentities.counter.Counter;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class EventSourcedEntityAsToolIntegrationTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(CounterManagerAgent.class, testModelProvider);
  }

  @AfterEach
  public void clearModelProviderState() {
    testModelProvider.reset();
  }

  private String newSessionId() {
    return UUID.randomUUID().toString();
  }

  /** An agent that uses CounterEntity as a tool to manage counters. */
  @Component(
      id = "counter-manager-agent",
      name = "CounterManagerAgent",
      description = "Agent that manages counters using entity tools")
  public static class CounterManagerAgent extends Agent {

    public record CounterRequest(String counterId, int increaseAmount) {}

    public record CounterResponse(String message, Counter counter) {}

    public Effect<CounterResponse> increaseAndGetCounter(CounterRequest request) {
      return effects()
          .systemMessage("You are a counter management agent.")
          .tools(CounterEntity.class)
          .userMessage(
              "Increase the counter by "
                  + request.increaseAmount
                  + " and then get its current state")
          .responseConformsTo(Counter.class)
          .map(
              counter ->
                  new CounterResponse("Counter increased and retrieved successfully", counter))
          .thenReply();
    }
  }

  @Test
  public void agentShouldCallEventSourcedEntityToIncreaseAndGetCounter() {
    // Setup test model to simulate LLM calling the tools
    String counterId = "counter-123";
    int increaseAmount = 42;

    // First tool call - increase counter
    testModelProvider
        .whenMessage(s -> s.contains("Increase the counter"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "CounterEntity_increase",
                "{\"uniqueId\":\"" + counterId + "\",\"value\":" + increaseAmount + "}"));

    testModelProvider
        .whenToolResult(result -> result.name().equals("CounterEntity_increase"))
        // after increasing the counter, we fetch its state
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "CounterEntity_getState", "{\"uniqueId\":\"" + counterId + "\"}"));

    // Final response after getting counter state
    testModelProvider
        .whenToolResult(result -> result.name().equals("CounterEntity_getState"))
        // the returned value is just sent back as structured content
        // expected to be the Counter serialized as json
        .thenReply(result -> new TestModelProvider.AiResponse(result.content()));

    // Execute the agent
    var response =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(CounterManagerAgent::increaseAndGetCounter)
            .invoke(new CounterManagerAgent.CounterRequest(counterId, increaseAmount));

    // Verify the response
    assertThat(response).isNotNull();
    assertThat(response.message()).isEqualTo("Counter increased and retrieved successfully");
    assertThat(response.counter).isNotNull();
    assertThat(response.counter.value()).isEqualTo(increaseAmount);
  }
}

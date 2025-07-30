/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.javasdk.CommandException;
import akka.javasdk.DependencyProvider;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akka.stream.javadsl.Sink;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.MyException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class AgentIntegrationTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    var depsProvider =
        new DependencyProvider() {
          @Override
          @SuppressWarnings("unchecked")
          public <T> T getDependency(Class<T> clazz) {
            if (clazz.isAssignableFrom(SomeAgentWithTool.TrafficService.class)) {
              return (T) new SomeAgentWithTool.TrafficService();
            }
            return null;
          }
        };

    return TestKit.Settings.DEFAULT
        .withModelProvider(SomeAgent.class, testModelProvider)
        .withModelProvider(SomeAgentWithTool.class, testModelProvider)
        .withModelProvider(SomeStructureResponseAgent.class, testModelProvider)
        .withModelProvider(SomeStreamingAgent.class, testModelProvider)
        .withModelProvider(SomeAgentWithBadlyConfiguredTool.class, testModelProvider)
        .withDependencyProvider(depsProvider);
  }

  @AfterEach
  public void afterEach() {
    testModelProvider.reset();
  }

  private String newSessionId() {
    return UUID.randomUUID().toString();
  }

  @Test
  public void shouldMapStringResponse() {
    // given
    testModelProvider.whenMessage(s -> s.equals("hello")).reply("123456");

    // when
    SomeAgent.SomeResponse result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeAgent::mapLlmResponse)
            .invoke("hello");

    // then
    assertThat(result.response()).isEqualTo("123456");
  }

  @Test
  public void shouldMapStructuredResponse() {
    // given
    testModelProvider.whenMessage(s -> s.equals("structured")).reply("{\"response\": \"123456\"}");

    // when
    SomeStructureResponseAgent.SomeResponse result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeStructureResponseAgent::mapStructureResponse)
            .invoke("structured");

    // then
    assertThat(result.response()).isEqualTo("123456");
  }

  @Test
  public void shouldMapFailedJsonParsingResponse() {
    // given
    testModelProvider
        .whenMessage(s -> s.equals("structured"))
        .reply("{\"corrupted json: \"123456\"}");

    // when
    SomeStructureResponseAgent.SomeResponse result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeStructureResponseAgent::mapStructureResponse)
            .invoke("structured");

    // then
    assertThat(result.response()).isEqualTo("default response");
  }

  @Test
  public void shouldCallToolFunctionsFromInstances() {

    var userQuestion = "How is the weather today in Leuven?";

    // when asking for the weather, it first look up for today's date
    testModelProvider
        .whenMessage(s -> s.equals(userQuestion))
        .reply(new ToolInvocationRequest("SomeAgentWithTool_getDateOfToday", ""));

    // when receiving the date back, model asks for calling getWeather
    testModelProvider
        .whenToolResult(result -> result.content().equals("2025-01-01"))
        .reply(
            new ToolInvocationRequest(
                "WeatherService_getWeather",
                """
                { "location" : "Leuven", "date" : "2025-01-01" }"""));

    // receives weather info
    testModelProvider
        .whenToolResult(result -> result.content().startsWith("The weather is"))
        .thenReply(result -> new AiResponse(result.content()));

    // when
    var response =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeAgentWithTool::query)
            .invoke(userQuestion);

    // then
    assertThat(response.response()).isEqualTo("The weather is sunny in Leuven. (date=2025-01-01)");
  }

  @Test
  public void shouldCallToolFunctionsFromClasses() {

    var userQuestion = "How is the traffic today in Leuven?";

    // when asking for the traffic, call the traffic service

    testModelProvider
        .whenUserMessage(msg -> msg.content().equals(userQuestion))
        .reply(
            new ToolInvocationRequest(
                "TrafficService_getTrafficNow",
                """
                { "location" : "Leuven" }"""));

    // receives the traffic info as the final answer
    testModelProvider
        .whenToolResult(result -> result.content().startsWith("There is traffic jam"))
        .thenReply(result -> new AiResponse(result.content()));

    // when
    var response =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeAgentWithTool::query)
            .invoke(userQuestion);

    // then
    assertThat(response.response()).isEqualTo("There is traffic jam in Leuven.");
  }

  @Test
  public void shouldFailForBadlyConfiguredTool() {

    var userQuestion = "How is the traffic today in Leuven?";
    testModelProvider.fixedResponse("Hello");

    try {
      componentClient
          .forAgent()
          .inSession(newSessionId())
          .method(SomeAgentWithBadlyConfiguredTool::query)
          .invoke(userQuestion);
      fail("Should have thrown an exception");
    } catch (Exception e) {
      assertThat(e.getMessage()).startsWith("Component client error");
    }
  }

  @Test
  public void shouldFailWithClearMessageIfToolClassCannotBeInit() {

    var userQuestion = "How is the traffic today in Leuven?";

    testModelProvider
        .whenUserMessage(msg -> msg.content().equals(userQuestion))
        .reply(new ToolInvocationRequest("TrafficService_getTrafficNow", ""));

    try {
      componentClient
          .forAgent()
          .inSession(newSessionId())
          .method(SomeAgentWithTool::query)
          .invoke(userQuestion);

      fail("Should have thrown an exception");
    } catch (Exception e) {
      // FIXME: errors message in dev-mode/test should be propagate
      assertThat(e.getMessage()).contains("Unexpected error");
    }
  }

  @Test
  public void shouldStreamResponse() throws Exception {
    // given
    testModelProvider.whenMessage(s -> s.equals("hello")).reply("Hi mate, how are you today?");

    // when
    var source =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .tokenStream(SomeStreamingAgent::ask)
            .source("hello");

    var resultTokens =
        source
            .runWith(Sink.seq(), testKit.getMaterializer())
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    // then
    assertThat(resultTokens.size()).isEqualTo(13); // by word + separators
    assertThat(resultTokens.getFirst()).isEqualTo("Hi");
    assertThat(resultTokens.getLast()).isEqualTo("?");
  }

  @Test
  public void shouldIncludeAgentsInRegistry() {
    assertThat(
            testKit.getAgentRegistry().allAgents().stream()
                .map(AgentRegistry.AgentInfo::id)
                .toList())
        .contains("some-agent", "some-streaming-agent", "structured-response-agent");
    assertThat(
            testKit.getAgentRegistry().agentsWithRole("streaming").stream()
                .map(AgentRegistry.AgentInfo::id)
                .toList())
        .isEqualTo(List.of("some-streaming-agent"));

    var someStructuredInfo = testKit.getAgentRegistry().agentInfo("structured-response-agent");
    assertThat(someStructuredInfo.id()).isEqualTo("structured-response-agent");
    assertThat(someStructuredInfo.name()).isEqualTo("Dummy Agent");
    assertThat(someStructuredInfo.description()).isEqualTo("Not very smart agent");

    // SomeAgent doesn't define AgentDescription but has default info
    var someInfo = testKit.getAgentRegistry().agentInfo("some-agent");
    assertThat(someInfo.id()).isEqualTo("some-agent");
    assertThat(someInfo.name()).isEqualTo("some-agent");
    assertThat(someInfo.description()).isEqualTo("");
  }

  @Test
  public void shouldSupportDynamicCall() {
    testModelProvider.whenMessage(s -> s.equals("hello")).reply("123456");

    var obj =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .dynamicCall("some-agent")
            .invoke("hello");
    SomeAgent.SomeResponse result = (SomeAgent.SomeResponse) obj;

    assertThat(result.response()).isEqualTo("123456");
  }

  @Test
  public void shouldDetectWrongArityOfDynamicCall() {
    testModelProvider.whenMessage(s -> s.equals("hello")).reply("123456");

    try {
      LoggingTestKit.warn("requires a parameter, but was invoked without parameter")
          .expect(
              testKit.getActorSystem(),
              () ->
                  componentClient
                      .forAgent()
                      .inSession(newSessionId())
                      .dynamicCall("some-agent")
                      .invoke());

      fail("Expected exception");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).startsWith("Component client error");
    }
  }

  @Test
  public void shouldDetectWrongParameterTypeOfDynamicCall() {
    testModelProvider.whenMessage(s -> s.equals("hello")).reply("123456");

    try {
      componentClient.forAgent().inSession(newSessionId()).dynamicCall("some-agent").invoke(17);

      fail("Expected exception");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).startsWith("Unexpected error");
    }
  }

  @Test
  public void shouldBeConstructedOnVirtualThread() {
    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SomeAgentWithTool::query)
            .invoke("Running on virtual thread?");

    assertThat(result.response()).isEqualTo("Query on vt: true, constructed on vt: true");
  }

  @Test
  public void shouldTestExceptions() {
    var exc1 =
        Assertions.assertThrows(
            CommandException.class,
            () -> {
              componentClient
                  .forAgent()
                  .inSession(newSessionId())
                  .method(SomeAgentReturningErrors::run)
                  .invoke("errorMessage");
            });
    assertThat(exc1.getMessage()).isEqualTo("errorMessage");

    var exc2 =
        Assertions.assertThrows(
            CommandException.class,
            () -> {
              componentClient
                  .forAgent()
                  .inSession(newSessionId())
                  .method(SomeAgentReturningErrors::run)
                  .invoke("errorCommandException");
            });
    assertThat(exc2.getMessage()).isEqualTo("errorCommandException");

    var exc3 =
        Assertions.assertThrows(
            MyException.class,
            () -> {
              componentClient
                  .forAgent()
                  .inSession(newSessionId())
                  .method(SomeAgentReturningErrors::run)
                  .invoke("errorMyException");
            });
    assertThat(exc3.getMessage()).isEqualTo("errorMyException");
    assertThat(exc3.getData()).isEqualTo(new MyException.SomeData("some data"));

    var exc4 =
        Assertions.assertThrows(
            MyException.class,
            () -> {
              componentClient
                  .forAgent()
                  .inSession(newSessionId())
                  .method(SomeAgentReturningErrors::run)
                  .invoke("throwMyException");
            });
    assertThat(exc4.getMessage()).isEqualTo("throwMyException");
    assertThat(exc4.getData()).isEqualTo(new MyException.SomeData("some data"));

    var exc5 =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> {
              componentClient
                  .forAgent()
                  .inSession(newSessionId())
                  .method(SomeAgentReturningErrors::run)
                  .invoke("throwRuntimeException");
            });
    assertThat(exc5.getMessage())
        .contains("Component client error"); // it's not the original message, but the one from the
    // runtime
  }
}

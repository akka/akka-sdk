/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.stream.javadsl.Sink;
import akkajavasdk.Junit5LogCapturing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(Junit5LogCapturing.class)
public class AgentIntegrationTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(SomeAgent.class, testModelProvider)
        .withModelProvider(SomeStructureResponseAgent.class, testModelProvider)
        .withModelProvider(SomeStreamingAgent.class, testModelProvider);
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
    //given
    testModelProvider.mockResponse(s -> s.equals("hello"), "123456");

    //when
    SomeAgent.SomeResponse result = componentClient.forAgent().inSession(newSessionId())
      .method(SomeAgent::mapLlmResponse)
      .invoke("hello");

    //then
    assertThat(result.response()).isEqualTo("123456");
  }

  @Test
  public void shouldMapStructuredResponse() {
    //given
    testModelProvider.mockResponse(s -> s.equals("structured"), "{\"response\": \"123456\"}");

    //when
    SomeStructureResponseAgent.SomeResponse result = componentClient.forAgent().inSession(newSessionId())
      .method(SomeStructureResponseAgent::mapStructureResponse)
      .invoke("structured");

    //then
    assertThat(result.response()).isEqualTo("123456");
  }

  @Test
  public void shouldMapFailedJsonParsingResponse() {
    //given
    testModelProvider.mockResponse(s -> s.equals("structured"), "{\"corrupted json: \"123456\"}");

    //when
    SomeStructureResponseAgent.SomeResponse result = componentClient.forAgent().inSession(newSessionId())
      .method(SomeStructureResponseAgent::mapStructureResponse)
      .invoke("structured");

    //then
    assertThat(result.response()).isEqualTo("default response");
  }

  @Test
  public void shouldStreamResponse() throws Exception {
    //given
    testModelProvider.mockResponse(s -> s.equals("hello"), "Hi mate, how are you today?");

    //when
    var source = componentClient.forAgent().inSession(newSessionId())
        .tokenStream(SomeStreamingAgent::ask)
        .source("hello");

    var resultTokens = source.runWith(Sink.seq(), testKit.getMaterializer())
        .toCompletableFuture().get(5, TimeUnit.SECONDS);

    //then
    assertThat(resultTokens.size()).isEqualTo(13); // by word + separators
    assertThat(resultTokens.getFirst()).isEqualTo("Hi");
    assertThat(resultTokens.getLast()).isEqualTo("?");
  }

  @Test
  public void shouldIncludeAgentsInRegistry() {
    assertThat(testKit.getAgentRegistry().allAgents().stream().map(AgentRegistry.AgentInfo::id).toList())
        .contains("some-agent", "some-streaming-agent", "structured-response-agent");
    assertThat(testKit.getAgentRegistry().agentsWithRole("streaming").stream().map(AgentRegistry.AgentInfo::id).toList())
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
    testModelProvider.mockResponse(s -> s.equals("hello"), "123456");

    var obj = componentClient.forAgent().inSession(newSessionId())
        .dynamicCall("some-agent")
        .invoke("hello");
    SomeAgent.SomeResponse result = (SomeAgent.SomeResponse) obj;

    assertThat(result.response()).isEqualTo("123456");
  }

  @Test
  public void shouldDetectWrongArityOfDynamicCall() {
    testModelProvider.mockResponse(s -> s.equals("hello"), "123456");

    try {
      LoggingTestKit.warn("requires a parameter, but was invoked without parameter")
          .expect(
              testKit.getActorSystem(),
              () -> {
                return componentClient.forAgent().inSession(newSessionId())
                    .dynamicCall("some-agent")
                    .invoke();
              });

      fail("Expected exception");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).startsWith("Component client error");
    }
  }

  @Test
  public void shouldDetectWrongParameterTypeOfDynamicCall() {
    testModelProvider.mockResponse(s -> s.equals("hello"), "123456");

    try {
      componentClient.forAgent().inSession(newSessionId())
          .dynamicCall("some-agent")
          .invoke(17);

      fail("Expected exception");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).startsWith("Unexpected error");
    }
  }
}

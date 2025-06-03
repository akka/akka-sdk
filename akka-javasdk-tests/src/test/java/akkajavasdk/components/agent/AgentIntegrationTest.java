/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.javasdk.DependencyProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.stream.javadsl.Sink;
import akkajavasdk.Junit5LogCapturing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(Junit5LogCapturing.class)
public class AgentIntegrationTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();
  private final ModelProvider.Custom modelProvider = ModelProvider.custom(testModelProvider);

  private DependencyProvider mockDependencyProvider = new DependencyProvider() { // <1>
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getDependency(Class<T> clazz) {
      if (clazz.equals(ModelProvider.class)) {
        return (T) modelProvider;
      } else {
        throw new IllegalArgumentException("Unknown dependency type: " + clazz);
      }
    }
  };

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withDependencyProvider(mockDependencyProvider);
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
    testModelProvider.mockResponse(s -> s.equals("hello"), "123456");

    //when
    var source = componentClient.forAgent().inSession(newSessionId())
        .tokenStream(SomeStreamingAgent::ask)
        .source("hello");

    var resultTokens = source.runWith(Sink.seq(), testKit.getMaterializer())
        .toCompletableFuture().get(5, TimeUnit.SECONDS);

    //then
    assertThat(resultTokens.size()).isEqualTo("123456".length());
    assertThat(resultTokens.getFirst()).isEqualTo("1");
    assertThat(resultTokens.getLast()).isEqualTo("6");
  }

  @Test
  public void shouldIncludeAgentsInRegistry() {
    assertThat(testKit.getAgentRegistry().allAgentIds())
        .contains("some-agent", "some-streaming-agent", "structured-response-agent");
    assertThat(testKit.getAgentRegistry().agentIdsWithRole("streaming"))
        .isEqualTo(Set.of("some-streaming-agent"));

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

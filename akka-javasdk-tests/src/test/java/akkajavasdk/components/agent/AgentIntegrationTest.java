/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.DependencyProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

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

  @Test
  public void shouldMapStringResponse() {
    //given
    testModelProvider.mockResponse(s -> s.equals("hello"), "123456");

    //when
    SomeAgent.SomeResponse result = componentClient.forAgent().inSession("1")
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
    SomeStructureResponseAgent.SomeResponse result = componentClient.forAgent().inSession("1")
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
    SomeStructureResponseAgent.SomeResponse result = componentClient.forAgent().inSession("1")
      .method(SomeStructureResponseAgent::mapStructureResponse)
      .invoke("structured");

    //then
    assertThat(result.response()).isEqualTo("default response");
  }
}

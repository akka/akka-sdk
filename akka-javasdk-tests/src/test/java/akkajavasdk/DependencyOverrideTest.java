/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.OverridableDependency;
import akkajavasdk.protocol.TestGrpcServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class DependencyOverrideTest extends TestKitSupport {

  private static final OverridableDependency override = new OverridableDependency("test");

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withDependencyOverride(OverridableDependency.class, new OverridableDependency("replaced"))
        .withDependencyOverride(OverridableDependency.class, override);
  }

  @Test
  public void shouldInjectTheOverrideIntoAComponent() {
    var response =
        httpClient.GET("/dependency-override/name").responseBodyAs(String.class).invoke();
    assertThat(response.body()).isEqualTo("test");
  }

  @Test
  public void shouldInjectTheOverrideIntoAClassifierCreatedAtStartup() {
    var result =
        getClassifierClient().classifyWithResult("dependency-override-test-classifier", "anything");
    assertThat(result.classification().label()).contains("test");
  }

  @Test
  public void shouldKeepTheDependenciesOfTheServiceThatHaveNoOverride() {
    var result =
        getClassifierClient().classifyWithResult("dependency-provided-test-classifier", "anything");
    assertThat(result.classification().label()).contains("resolved");
    assertThat(getDependency(TestGrpcServiceClient.class)).isNotNull();
  }

  @Test
  public void shouldReturnTheOverrideFromTheTestKit() {
    assertThat(getDependency(OverridableDependency.class)).isSameAs(override);
  }
}

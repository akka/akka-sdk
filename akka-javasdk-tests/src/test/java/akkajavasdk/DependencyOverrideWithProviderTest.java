/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.OtherDependency;
import akkajavasdk.components.OverridableDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class DependencyOverrideWithProviderTest extends TestKitSupport {

  private static final OverridableDependency override = new OverridableDependency("test");

  private static final DependencyProvider testProvider =
      new DependencyProvider() {
        @SuppressWarnings("unchecked")
        @Override
        public <T> T getDependency(Class<T> clazz) {
          if (clazz == OverridableDependency.class) {
            return (T) new OverridableDependency("provider");
          } else if (clazz == OtherDependency.class) {
            return (T) new OtherDependency("provider");
          } else {
            return null;
          }
        }
      };

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withDependencyOverride(OverridableDependency.class, override)
        .withDependencyProvider(testProvider);
  }

  @Test
  public void shouldInjectTheOverrideInPlaceOfTheDependencyOfTheTestProvider() {
    var response =
        httpClient.GET("/dependency-override/name").responseBodyAs(String.class).invoke();
    assertThat(response.body()).isEqualTo("test");
    assertThat(getDependency(OverridableDependency.class)).isSameAs(override);
  }

  @Test
  public void shouldInjectTheDependenciesOfTheTestProviderThatHaveNoOverride() {
    var response =
        httpClient.GET("/dependency-override/other-name").responseBodyAs(String.class).invoke();
    assertThat(response.body()).isEqualTo("provider");
  }
}

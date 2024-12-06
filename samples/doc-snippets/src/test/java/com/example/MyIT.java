package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

// tag::test-di-provider[]
public class MyIT extends TestKitSupport {

  private static final DependencyProvider mockDependencyProvider = new DependencyProvider() { // <1>
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getDependency(Class<T> clazz) {
      if (clazz.equals(MyAppSettings.class)) {
           return (T) new MyAppSettings(true);
      } else {
        throw new IllegalArgumentException("Unknown dependency type: " + clazz);
      }
    }
  };

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withDependencyProvider(mockDependencyProvider); // <2>
  }

  // end::test-di-provider[]

  @Test
  public void testThatMockDiIsUsed() {
    assertSame(mockDependencyProvider, testKit.getDependencyProvider().get());
  }
}

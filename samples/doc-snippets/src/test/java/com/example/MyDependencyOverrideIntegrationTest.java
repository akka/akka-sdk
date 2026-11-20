package com.example;

import static org.junit.jupiter.api.Assertions.assertSame;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

// tag::test-di-override[]
public class MyDependencyOverrideIntegrationTest extends TestKitSupport {

  private static final MyAppSettings testSettings = new MyAppSettings(true); // <1>

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withDependencyOverride(MyAppSettings.class, testSettings); // <2>
  }

  // end::test-di-override[]

  @Test
  public void testThatOverrideIsUsed() {
    assertSame(testSettings, getDependency(MyAppSettings.class));
  }
}

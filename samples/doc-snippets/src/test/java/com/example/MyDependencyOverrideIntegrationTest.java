package com.example;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

// tag::test-di-override[]
public class MyDependencyOverrideIntegrationTest extends TestKitSupport {

  private static final PaymentGateway fakePaymentGateway = orderId -> "test-payment"; // <1>

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withDependencyOverride(
      PaymentGateway.class,
      fakePaymentGateway
    ); // <2>
  }

  // end::test-di-override[]

  @Test
  public void testThatOverrideIsUsed() {
    assertSame(fakePaymentGateway, getDependency(PaymentGateway.class));
  }

  @Test
  public void testThatOtherDependenciesAreKept() {
    assertFalse(getDependency(MyAppSettings.class).someFeatureFlag());
  }
}

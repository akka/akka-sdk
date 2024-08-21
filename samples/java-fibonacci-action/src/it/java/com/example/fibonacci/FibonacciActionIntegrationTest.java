package com.example.fibonacci;

import akka.http.javadsl.model.StatusCodes;
import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import akka.platform.javasdk.http.StrictResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class FibonacciActionIntegrationTest extends KalixIntegrationTestKitSupport {

  // TODO enable after introducing TimedAction
  @Disabled
  public void calculateNextNumber() {
    StrictResponse<Number> res = await(
      httpClient.GET("/fibonacci/5/next").responseBodyAs(Number.class).invokeAsync()
    );

    Assertions.assertEquals(200, res.httpResponse().status().intValue());
    Assertions.assertEquals(8, res.body().value());
  }

  @Disabled
  public void calculateNextNumberWithLimitedFibo() {

    StrictResponse<Number> res = await(
      httpClient.GET("/fibonacci/5/next").responseBodyAs(Number.class).invokeAsync()
    );

    Assertions.assertEquals(200, res.httpResponse().status().intValue());
    Assertions.assertEquals(8, res.body().value());
  }

  @Disabled
  public void wrongNumberReturnsError() {

    StrictResponse<String> res = await(
      httpClient.GET("/fibonacci/7/next").parseResponseBody(String::new).invokeAsync()
    );

    Assertions.assertEquals("java.lang.RuntimeException: Input number is not a Fibonacci number, received '7'", res.body());
    Assertions.assertEquals(StatusCodes.BAD_REQUEST, res.httpResponse().status());
  }
}

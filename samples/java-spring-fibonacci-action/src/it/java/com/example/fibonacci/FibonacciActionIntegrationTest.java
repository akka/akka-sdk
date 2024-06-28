package com.example.fibonacci;

import akka.http.javadsl.model.StatusCodes;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import kalix.javasdk.http.StrictResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FibonacciActionIntegrationTest extends KalixIntegrationTestKitSupport {

  @Test
  public void calculateNextNumber() {
    StrictResponse<Number> res = await(
      httpClient.GET("/limitedfibonacci/5/next").responseBodyAs(Number.class).invokeAsync()
    );

    Assertions.assertEquals(200, res.httpResponse().status().intValue());
    Assertions.assertEquals(8, res.body().value());
  }

  @Test
  public void calculateNextNumberWithLimitedFibo() {

    StrictResponse<Number> res = await(
      httpClient.GET("/limitedfibonacci/5/next").responseBodyAs(Number.class).invokeAsync()
    );

    Assertions.assertEquals(200, res.httpResponse().status().intValue());
    Assertions.assertEquals(8, res.body().value());
  }

  @Test
  public void wrongNumberReturnsError() {

    StrictResponse<String> res = await(
      httpClient.GET("/limitedfibonacci/7/next").parseResponseBody(String::new).invokeAsync()
    );

    Assertions.assertEquals("java.lang.RuntimeException: Input number is not a Fibonacci number, received '7'", res.body());
    Assertions.assertEquals(StatusCodes.BAD_REQUEST, res.httpResponse().status());
  }
}

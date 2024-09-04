package com.example.fibonacci;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.http.StrictResponse;
import com.example.fibonacci.domain.Number;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FibonacciIntegrationTest extends TestKitSupport {

  @Test
  public void calculateNextNumber() {
    StrictResponse<Number> res = await(
      httpClient.GET("/fibonacci/5/next").responseBodyAs(Number.class).invokeAsync()
    );

    Assertions.assertEquals(200, res.httpResponse().status().intValue());
    Assertions.assertEquals(8, res.body().value());
  }

  @Test
  public void calculateNextNumberWithLimitedFibo() {

    StrictResponse<Number> res = await(
      httpClient.GET("/fibonacci/5/next").responseBodyAs(Number.class).invokeAsync()
    );

    Assertions.assertEquals(200, res.httpResponse().status().intValue());
    Assertions.assertEquals(8, res.body().value());
  }

  @Test
  public void wrongNumberReturnsError() {

    StrictResponse<String> res = await(
      httpClient.GET("/fibonacci/7/next").parseResponseBody(String::new).invokeAsync()
    );

    Assertions.assertEquals("Input number is not a Fibonacci number, received '7'", res.body());
    Assertions.assertEquals(StatusCodes.BAD_REQUEST, res.httpResponse().status());
  }
}

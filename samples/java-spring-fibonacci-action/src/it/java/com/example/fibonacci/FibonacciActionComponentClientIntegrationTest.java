package com.example.fibonacci;

import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

// tag::testing-action[]
public class FibonacciActionComponentClientIntegrationTest extends KalixIntegrationTestKitSupport {

  private String serviceUrl = "http://localhost:9000";
  private HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  public void calculateNextNumber() throws ExecutionException, InterruptedException, TimeoutException {

    Number response =
      await(
        componentClient.forAction()
          .method(FibonacciAction::nextNumber)
          .invokeAsync(new Number(5)));

    Assertions.assertEquals(8, response.value());
  }
  // end::testing-action[]

  @Test
  public void calculateNextNumberWithLimitedFibo() throws ExecutionException, InterruptedException, TimeoutException {

    HttpRequest httpRequest = HttpRequest.newBuilder()
      .uri(URI.create(serviceUrl + "/limitedfibonacci/5/next"))
      .build();

    Number response =
      await(httpClient.sendAsync(httpRequest, new JsonResponseHandler<>(Number.class))).body();

    Assertions.assertEquals(8, response.value());
  }

  // tag::testing-action[]
}
// end::testing-action[]

package com.example.callanotherservice;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// tag::http-mock[]
public class DelegatingServiceEndpointMockedTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withMockedHttpService( // <1>
      "counter",
      request ->
        HttpResponse.create()
          .withStatus(StatusCodes.OK)
          .withEntity(ContentTypes.APPLICATION_JSON, "{\"value\":42}")
    );
  }

  @AfterEach
  public void resetMocks() {
    testKit.getMockedHttpServices().reset(); // <2>
  }

  @Test
  public void delegatingEndpointReturnsValueFromMockedUpstream() {
    var body = new DelegatingServiceEndpoint.IncreaseRequest(1);
    var response = httpClient
      .POST("/delegate/counter/abc/increase")
      .withRequestBody(body)
      .responseBodyAs(String.class)
      .invoke();

    assertThat(response.body()).isEqualTo("New counter value: 42");
  }

  @Test
  public void delegatingEndpointFailsWhenUpstreamReturnsError() {
    testKit
      .getMockedHttpServices()
      .mockResponse( // <3>
        "counter",
        request -> HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR)
      );

    var body = new DelegatingServiceEndpoint.IncreaseRequest(1);

    org.assertj.core.api.Assertions.assertThatThrownBy(
      () -> httpClient.POST("/delegate/counter/abc/increase").withRequestBody(body).invoke()
    ).hasMessageContaining("500");
  }
}
// end::http-mock[]

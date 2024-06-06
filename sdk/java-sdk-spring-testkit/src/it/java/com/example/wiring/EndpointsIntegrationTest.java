/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring;

import com.example.wiring.actions.echo.ActionWithHttpResponse;
import com.example.wiring.actions.echo.Message;
import kalix.javasdk.HttpResponse;
import kalix.javasdk.testkit.KalixTestKit;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static java.time.Duration.ofMillis;
import static java.time.temporal.ChronoUnit.SECONDS;
import static kalix.javasdk.StatusCode.Success.CREATED;
import static kalix.javasdk.StatusCode.Success.OK;
import static org.assertj.core.api.Assertions.assertThat;


// These are mainly about API specification and validation.
public class EndpointsIntegrationTest extends KalixIntegrationTestKitSupport {

  private Duration timeout = Duration.of(10, SECONDS);

  @Override
  protected KalixTestKit.Settings kalixTestKitSettings() {
    // here only to show how to set different `Settings` in a test.
    return KalixTestKit.Settings.DEFAULT
            .withAclEnabled()
            .withAdvancedViews()
            .withWorkflowTickInterval(ofMillis(500));
  }


  @Test
  public void failRequestWithRequiredQueryParam() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/optional-params-action")
        .retrieve()
        .toEntity(String.class)
        .onErrorResume(WebClientResponseException.class, error -> Mono.just(ResponseEntity.status(error.getStatusCode()).body(error.getResponseBodyAsString())))
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isEqualTo("Required request parameter is missing: longValue");
  }

  @Test
  public void notAcceptRequestWithMissingPathParamIfNotEntityId() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/echo/message/") // missing param
        .retrieve()
        .toEntity(String.class)
        .onErrorResume(WebClientResponseException.class, error -> Mono.just(ResponseEntity.status(error.getStatusCode()).body(error.getResponseBodyAsString())))
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  public void failRequestWithMissingRequiredIntPathParam() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/echo/int/") // missing param
        .retrieve()
        .toEntity(String.class)
        .onErrorResume(WebClientResponseException.class, error -> Mono.just(ResponseEntity.status(error.getStatusCode()).body(error.getResponseBodyAsString())))
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isEqualTo("Path contains value of wrong type! Expected field of type INT32.");
  }

  @Test
  public void shouldReturnTextBody() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/text-body")
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get("Content-Type")).contains("text/plain");
    assertThat(response.getBody()).isEqualTo("test");
  }

  @Test
  public void shouldReturnTextBodyWithComponentClient() {

    HttpResponse response = await(componentClient.forAction().method(ActionWithHttpResponse::textBody).invokeAsync());

    assertThat(response.getStatusCode()).isEqualTo(OK);
    assertThat(response.getContentType()).contains("text/plain");
    assertThat(response.getBody()).contains("test".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void shouldReturnEmptyCreatedMethod() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/empty-text-body")
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().get("Content-Type")).contains("application/octet-stream");
    assertThat(response.getBody()).isNull();
  }

  @Test
  public void shouldReturnEmptyCreatedWithComponentClient() {

    HttpResponse response = await(componentClient.forAction().method(ActionWithHttpResponse::emptyCreated).invokeAsync());

    assertThat(response.getStatusCode()).isEqualTo(CREATED);
    assertThat(response.getContentType()).isEqualTo("application/octet-stream");
    assertThat(response.getBody()).isEmpty();
  }

  @Test
  public void shouldReturnJsonString() {

    ResponseEntity<Message> response =
      webClient
        .get()
        .uri("/json-string-body")
        .retrieve()
        .toEntity(Message.class)
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get("Content-Type")).contains("application/json");
    assertThat(response.getBody()).isEqualTo(new Message("123"));
  }

  @Test
  public void shouldReturnJsonStringWithComponentClient() {

    HttpResponse response = await(componentClient.forAction().method(ActionWithHttpResponse::jsonStringBody).invokeAsync());

    assertThat(response.getStatusCode()).isEqualTo(OK);
    assertThat(response.getContentType()).contains("application/json");
    assertThat(response.getBody()).contains("{\"text\": \"123\"}".getBytes());
    assertThat(response.bodyAsJson(Message.class)).isEqualTo(new Message("123"));
  }

  @Test
  public void shouldReturnEmptyBody() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/empty-text-body")
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().get("Content-Type")).contains("application/octet-stream");
    assertThat(response.getBody()).isNull();
  }

  @Test
  public void verifyRequestWithOptionalQueryParams() {

    Message response =
      webClient
        .get()
        .uri("/optional-params-action?longValue=1")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    assertThat(response.text()).isEqualTo("1nullnull");
  }

  @Test
  public void verifyRequestWithProtoDefaultValues() {

    Message response =
      webClient
        .get()
        .uri("/action/0/0/0/0?shortValue=0&byteValue=0&charValue=97&booleanValue=false")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    assertThat(response.text()).isEqualTo("0.00.00000afalse");
  }

  @Test
  public void verifyJavaPrimitivesAsParams() {

    Message response =
      webClient
        .get()
        .uri("/action/1.0/2.0/3/4?shortValue=5&byteValue=6&charValue=97&booleanValue=true")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    assertThat(response.text()).isEqualTo("1.02.03456atrue");

    Message responseCollections =
      webClient
        .get()
        .uri("/action_collections?ints=1&ints=0&ints=2")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    assertThat(responseCollections.text()).isEqualTo("1,0,2");
  }

}


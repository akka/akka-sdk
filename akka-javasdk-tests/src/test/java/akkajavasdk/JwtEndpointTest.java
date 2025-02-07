/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.grpc.GrpcServiceException;
import akka.grpc.javadsl.SingleResponseRequestBuilder;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.http.StrictResponse;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.protocol.TestJwtsGrpcServiceClient;
import akkajavasdk.protocol.TestGrpcServiceOuterClass;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(Junit5LogCapturing.class)
public class JwtEndpointTest extends TestKitSupport {

  @Test
  public void shouldReturnIssuerAndSubject() {
    var token = bearerTokenWith(Map.of("iss", "my-issuer-123", "sub", "my-subject-123"));
    
    CompletableFuture<StrictResponse<String>> call = httpClient.GET("/hello").addHeader("Authorization", token)
        .responseBodyAs(String.class)
        .invokeAsync().toCompletableFuture();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          assertThat(call.get().body()).isEqualTo("issuer: my-issuer-123, subject: my-subject-123");
        });

  }

  @Test
  public void shouldReturnForbidden() {
    var token = bearerTokenWith(Map.of("iss", "my-issuer-123"));

    var call = httpClient.GET("/hello").addHeader("Authorization", token)
        .invokeAsync().toCompletableFuture();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          assertThat(call.get().status()).isEqualTo(StatusCodes.FORBIDDEN);
        });
  }

  @Test
  public void shouldRaiseExceptionWhenAccessingJWT() {
    var token = bearerTokenWith(Map.of("iss", "my-issuer-123"));

    var call = httpClient.GET("/missingjwt").addHeader("Authorization", token)
        .responseBodyAs(String.class).invokeAsync().toCompletableFuture();

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var exception = assertThrows(Exception.class, call::get);
          assertThat(exception.getCause().getClass()).isEqualTo(RuntimeException.class);
          assertTrue(exception.getCause().getMessage().contains(
              "There are no JWT claims defined but trying accessing the JWT claims. The class or the method needs to be annotated with @JWT."));
        });

  }


  // from here down, JWT validation tests for gRPC endpoints
  TestGrpcServiceOuterClass.In request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();

  @Test
  public void shouldValidateJwtIssuer() {
    String bearerToken = bearerTokenWith(Map.of("iss", "my-issuer"));
    var wrongIss = getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
        .addRequestHeader("Authorization", bearerToken);
    expectFailWith(wrongIss.jwtIssuer(), "UNAUTHENTICATED: Bearer token from wrong issuer");

    var correctIss = getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
        .addRequestHeader("Authorization", bearerTokenWith(Map.of("iss", "my-issuer-123")));
    var response = await(correctIss.jwtIssuer().invoke(request));
    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldValidateJwtStaticClaim() {
    String wrongSub = bearerTokenWith(
        Map.of("iss", "my-issuer-123", "sub", "incorrect-subject"));
    var client = getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
        .addRequestHeader("Authorization", wrongSub);
    expectFailWith(client.jwtStaticClaimValue(), "PERMISSION_DENIED: Bearer token does not contain required claims");

    var correctSub = getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
        .addRequestHeader("Authorization", bearerTokenWith(Map.of("iss", "my-issuer-123", "sub", "my-subject-123")));
    var response = await(correctSub.jwtStaticClaimValue().invoke(request));
    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldValidateJwtStaticClaimPattern() {
    String wrongClaimFormat = bearerTokenWith(
        Map.of("iss", "my-issuer-123", "sub", "not-my-subject-456"));
    var client = getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
        .addRequestHeader("Authorization", wrongClaimFormat);
    expectFailWith(client.jwtStaticClaimPattern(), "PERMISSION_DENIED: Bearer token does not contain required claims");

    var correctSub = getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
        .addRequestHeader("Authorization", bearerTokenWith(Map.of("iss", "my-issuer-456", "sub", "my-subject-456")));
    var response = await(correctSub.jwtStaticClaimPattern().invoke(request));
    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldCorrectlyInheritFromClass() {
    String classIssuer = bearerTokenWith(Map.of("iss", "class-level-issuer"));
    var client = getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
        .addRequestHeader("Authorization", classIssuer);
    var response = await(client.jwtInherited().invoke(request));
    assertThat(response.getData()).isEqualTo(request.getData());

    var correctSub = getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
        .addRequestHeader("Authorization", bearerTokenWith(Map.of("iss", "my-issuer-123")));
    expectFailWith(correctSub.jwtInherited(), "UNAUTHENTICATED: Bearer token from wrong issuer");
  }

  private String bearerTokenWith(Map<String, String> claims) {
    try {
      // setting algorithm to none
      String alg = Base64.getEncoder().encodeToString("{\"alg\": \"none\"}".getBytes());
      byte[] jsonClaims = new ObjectMapper().writeValueAsBytes(claims);

      // no signature validation is done, thus no valid signature required
      return "Bearer " + alg + "." + Base64.getEncoder().encodeToString(jsonClaims);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void expectFailWith(SingleResponseRequestBuilder<TestGrpcServiceOuterClass.In, TestGrpcServiceOuterClass.Out> method, String expected) {
    try {
      await(method.invoke(request));
      fail("Expected exception");
    } catch (GrpcServiceException e) {
      assertThat(e.getMessage()).contains(expected);
    }
  }

}

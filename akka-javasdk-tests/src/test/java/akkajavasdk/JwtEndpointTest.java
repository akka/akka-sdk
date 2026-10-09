/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.*;

import akka.grpc.GrpcServiceException;
import akka.grpc.javadsl.SingleBlockingResponseRequestBuilder;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.http.StrictResponse;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.jwt.ClaimsJwtEndpoint;
import akkajavasdk.protocol.TestGrpcServiceOuterClass;
import akkajavasdk.protocol.TestJwtsGrpcServiceClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class JwtEndpointTest extends TestKitSupport {

  @Test
  public void shouldReturnIssuerAndSubject() {
    var token = bearerTokenWith(Map.of("iss", "my-issuer-123", "sub", "my-subject-123"));

    StrictResponse<String> call =
        httpClient
            .GET("/hello")
            .addHeader("Authorization", token)
            .responseBodyAs(String.class)
            .invoke();

    assertThat(call.body()).isEqualTo("issuer: my-issuer-123, subject: my-subject-123");
  }

  // Note: env var set in sbt build
  @Test
  public void shouldAllowIssuerFromEnv() {
    var token = bearerTokenWith(Map.of("iss", "issuer-from-env", "sub", "my-subject-123"));

    StrictResponse<String> call =
        httpClient
            .GET("/hello")
            .addHeader("Authorization", token)
            .responseBodyAs(String.class)
            .invoke();

    assertThat(call.body()).isEqualTo("issuer: issuer-from-env, subject: my-subject-123");
  }

  @Test
  public void shouldReturnForbidden() {
    var token = bearerTokenWith(Map.of("iss", "my-issuer-123"));

    var call = httpClient.GET("/hello").addHeader("Authorization", token).invoke();

    assertThat(call.status()).isEqualTo(StatusCodes.FORBIDDEN);
  }

  @Test
  public void shouldRaiseExceptionWhenAccessingJWT() {
    var token = bearerTokenWith(Map.of("iss", "my-issuer-123"));

    var call =
        httpClient
            .GET("/missingjwt")
            .addHeader("Authorization", token)
            .responseBodyAs(String.class)
            .invokeAsync()
            .toCompletableFuture();

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var exception = assertThrows(Exception.class, call::get);
              assertThat(exception.getCause().getClass()).isEqualTo(RuntimeException.class);
              assertTrue(
                  exception
                      .getCause()
                      .getMessage()
                      .contains(
                          "There are no JWT claims defined but trying accessing the JWT claims. The"
                              + " class or the method needs to be annotated with @JWT."));
            });
  }

  @Test
  public void shouldReturnTypedClaims() {
    var token =
        bearerTokenWith(
            Map.ofEntries(
                Map.entry("iss", "my-issuer-123"),
                Map.entry("exp", 4102444800L),
                Map.entry("iat", 1700000000L),
                Map.entry("nbf", 1700000000L),
                Map.entry("count", 42),
                Map.entry("ratio", 0.5),
                Map.entry("admin", true),
                Map.entry("roles", List.of("reader", "writer")),
                Map.entry("levels", List.of(1, 2, 3)),
                Map.entry("dates", List.of(1700000000L, 4102444800L)),
                Map.entry("address", Map.of("city", "Lisbon"))));

    var claims = getClaims(token);

    assertThat(claims.expirationTime()).isEqualTo("2100-01-01T00:00:00Z");
    assertThat(claims.issuedAt()).isEqualTo("2023-11-14T22:13:20Z");
    assertThat(claims.notBefore()).isEqualTo("2023-11-14T22:13:20Z");
    assertThat(claims.count()).isEqualTo(42);
    assertThat(claims.countAsLong()).isEqualTo(42L);
    assertThat(claims.countAsString()).isEqualTo("42");
    assertThat(claims.countAsBoolean()).isNull();
    assertThat(claims.ratio()).isEqualTo(0.5);
    assertThat(claims.admin()).isTrue();
    assertThat(claims.issAsInteger()).isNull();
    assertThat(claims.roles()).containsExactly("reader", "writer");
    assertThat(claims.rolesAsString()).isEqualTo("[\"reader\",\"writer\"]");
    assertThat(claims.rolesAsObject()).isNull();
    assertThat(claims.levels()).containsExactly(1, 2, 3);
    assertThat(claims.dates()).containsExactly("2023-11-14T22:13:20Z", "2100-01-01T00:00:00Z");
    assertThat(claims.address()).isEqualTo("{\"city\":\"Lisbon\"}");
  }

  @Test
  public void shouldReturnClaimsEncodedAsStringsAndFractionalDates() {
    var claimValues = new LinkedHashMap<String, Object>();
    claimValues.put("iss", "my-issuer-123");
    claimValues.put("exp", new BigDecimal("4102444800.5"));
    claimValues.put("count", "42");
    claimValues.put("ratio", null);
    claimValues.put("admin", false);
    claimValues.put("roles", "[\"reader\",\"writer\"]");
    claimValues.put("dates", List.of(new BigDecimal("1700000000.25")));
    claimValues.put("address", "{\"city\":\"Lisbon\"}");

    var claims = getClaims(bearerTokenWith(claimValues));

    assertThat(claims.expirationTime()).isEqualTo("2100-01-01T00:00:00.500Z");
    assertThat(claims.count()).isEqualTo(42);
    assertThat(claims.countAsLong()).isEqualTo(42L);
    assertThat(claims.countAsString()).isEqualTo("42");
    assertThat(claims.ratio()).isNull();
    assertThat(claims.ratioAsString()).isNull();
    assertThat(claims.ratioAsList()).isNull();
    assertThat(claims.admin()).isFalse();
    assertThat(claims.roles()).containsExactly("reader", "writer");
    assertThat(claims.rolesAsString()).isEqualTo("[\"reader\",\"writer\"]");
    assertThat(claims.dates()).containsExactly("2023-11-14T22:13:20.250Z");
    assertThat(claims.address()).isEqualTo("{\"city\":\"Lisbon\"}");
  }

  private ClaimsJwtEndpoint.Claims getClaims(String token) {
    return httpClient
        .GET("/jwt-claims")
        .addHeader("Authorization", token)
        .responseBodyAs(ClaimsJwtEndpoint.Claims.class)
        .invoke()
        .body();
  }

  // from here down, JWT validation tests for gRPC endpoints
  TestGrpcServiceOuterClass.In request =
      TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();

  @Test
  public void shouldValidateJwtIssuer() {
    String bearerToken = bearerTokenWith(Map.of("iss", "my-issuer"));
    var wrongIss =
        getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
            .addRequestHeader("Authorization", bearerToken);
    expectFailWith(wrongIss.jwtIssuer(), "UNAUTHENTICATED: Bearer token from wrong issuer");

    var correctIss =
        getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
            .addRequestHeader("Authorization", bearerTokenWith(Map.of("iss", "my-issuer-123")));
    var response = correctIss.jwtIssuer().invoke(request);
    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldValidateJwtStaticClaim() {
    String wrongSub = bearerTokenWith(Map.of("iss", "my-issuer-123", "sub", "incorrect-subject"));
    var client =
        getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
            .addRequestHeader("Authorization", wrongSub);
    expectFailWith(
        client.jwtStaticClaimValue(),
        "PERMISSION_DENIED: Bearer token does not contain required claims");

    var correctSub =
        getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
            .addRequestHeader(
                "Authorization",
                bearerTokenWith(Map.of("iss", "my-issuer-123", "sub", "my-subject-123")));
    var response = correctSub.jwtStaticClaimValue().invoke(request);
    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldValidateJwtStaticClaimPattern() {
    String wrongClaimFormat =
        bearerTokenWith(Map.of("iss", "my-issuer-123", "sub", "not-my-subject-456"));
    var client =
        getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
            .addRequestHeader("Authorization", wrongClaimFormat);
    expectFailWith(
        client.jwtStaticClaimPattern(),
        "PERMISSION_DENIED: Bearer token does not contain required claims");

    var correctSub =
        getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
            .addRequestHeader(
                "Authorization",
                bearerTokenWith(Map.of("iss", "my-issuer-456", "sub", "my-subject-456")));
    var response = correctSub.jwtStaticClaimPattern().invoke(request);
    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldCorrectlyInheritFromClass() {
    String classIssuer = bearerTokenWith(Map.of("iss", "class-level-issuer"));
    var client =
        getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
            .addRequestHeader("Authorization", classIssuer);
    var response = client.jwtInherited().invoke(request);
    assertThat(response.getData()).isEqualTo(request.getData());

    var correctSub =
        getGrpcEndpointClient(TestJwtsGrpcServiceClient.class)
            .addRequestHeader("Authorization", bearerTokenWith(Map.of("iss", "my-issuer-123")));
    expectFailWith(correctSub.jwtInherited(), "UNAUTHENTICATED: Bearer token from wrong issuer");
  }

  private String bearerTokenWith(Map<String, ?> claims) {
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

  private void expectFailWith(
      SingleBlockingResponseRequestBuilder<
              TestGrpcServiceOuterClass.In, TestGrpcServiceOuterClass.Out>
          method,
      String expected) {
    try {
      method.invoke(request);
      fail("Expected exception");
    } catch (GrpcServiceException e) {
      assertThat(e.getMessage()).contains(expected);
    }
  }
}

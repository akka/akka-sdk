package hellojwt.api;

import akka.javasdk.http.StrictResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import akka.javasdk.testkit.TestKitSupport;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class HelloJwtIntegrationTest extends TestKitSupport {

  // tag::bearer-token-claims-test[]
  @Test
  public void shouldReturnIssuerAndSubject() throws JsonProcessingException {

    String bearerToken = bearerTokenWith(
            Map.of("iss", "my-issuer", "sub", "my-subject")); // <1>

    CompletableFuture<StrictResponse<String>> call = httpClient.GET("/hello/claims").addHeader("Authorization","Bearer "+ bearerToken) // <2>
            .responseBodyAs(String.class)
            .invokeAsync().toCompletableFuture();

    Awaitility.await()
            .ignoreExceptions()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
              assertThat(call.get().body()).isEqualTo("issuer: my-issuer, subject: my-subject");
            });
  }

  private String bearerTokenWith(Map<String, String> claims) throws JsonProcessingException {
    // setting algorithm to none
    String header = Base64.getEncoder().encodeToString("""
        {
          "alg": "none"
        }
        """.getBytes()); // <3>
    byte[] jsonClaims = new ObjectMapper().writeValueAsBytes(claims);
    String payload = Base64.getEncoder().encodeToString(jsonClaims);

    // no validation is done for integration tests, thus no signature required
    return header + "." + payload; // <4>
  }
  // end::bearer-token-claims-test[]
}

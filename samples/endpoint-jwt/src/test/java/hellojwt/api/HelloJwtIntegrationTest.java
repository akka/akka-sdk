package hellojwt.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.JsonSupport;
import akka.javasdk.http.StrictResponse;
import akka.javasdk.testkit.TestKitSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class HelloJwtIntegrationTest extends TestKitSupport {

  // tag::bearer-token-claims-test[]
  @Test
  public void shouldReturnIssuerAndSubject() throws JsonProcessingException {
    String bearerToken = bearerTokenWith(Map.of("iss", "my-issuer", "sub", "my-subject")); // <1>

    StrictResponse<String> call = httpClient
      .GET("/hello/claims")
      .addHeader("Authorization", "Bearer " + bearerToken) // <2>
      .responseBodyAs(String.class)
      .invoke();

    assertThat(call.body()).isEqualTo("issuer: my-issuer, subject: my-subject");
  }

  private String bearerTokenWith(Map<String, String> claims) throws JsonProcessingException {
    // setting algorithm to none
    String header = Base64
      .getEncoder()
      .encodeToString("""
        {
          "alg": "none"
        }
        """.getBytes()); // <3>
    byte[] jsonClaims = JsonSupport.getObjectMapper().writeValueAsBytes(claims);
    String payload = Base64.getEncoder().encodeToString(jsonClaims);

    // no validation is done for integration tests, thus no signature required
    return header + "." + payload; // <4>
  }
  // end::bearer-token-claims-test[]
}

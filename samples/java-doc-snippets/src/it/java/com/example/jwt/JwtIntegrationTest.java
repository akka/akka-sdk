package com.example.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import akka.javasdk.testkit.AkkaSdkTestKitSupport;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


public class JwtIntegrationTest extends AkkaSdkTestKitSupport {

  // tag::bearer-token-claims-test[]
  private String bearerTokenWith(Map<String, String> claims) throws JsonProcessingException {
    // setting algorithm to none
    String alg = Base64.getEncoder().encodeToString("""
        {
          "alg": "none"
        }
        """.getBytes()); // <3>
    byte[] jsonClaims = new ObjectMapper().writeValueAsBytes(claims);

    // no validation is done for integration tests, thus no valid signature required
    return alg + "." +  Base64.getEncoder().encodeToString(jsonClaims); // <4>
  }
  // end::bearer-token-claims-test[]
}

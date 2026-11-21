/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.javasdk.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Optional;

/**
 * Reads public keys from a JSON Web Key Set (RFC 7517): {@code EC} keys on P-256 or P-384, and
 * {@code RSA} keys.
 */
final class Jwks {
  private Jwks() {}

  /** The key with the given id in the key set, if the set has one of a supported type. */
  static Optional<PublicKey> find(String jwksJson, String keyId) {
    try {
      JsonNode keys = JsonSupport.getObjectMapper().readTree(jwksJson).path("keys");
      for (JsonNode jwk : keys) {
        if (keyId.equals(jwk.path("kid").asText(null))) {
          return Optional.of(toPublicKey(jwk));
        }
      }
      return Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private static PublicKey toPublicKey(JsonNode jwk) throws Exception {
    String kty = jwk.path("kty").asText();
    switch (kty) {
      case "EC" -> {
        String curve =
            switch (jwk.path("crv").asText()) {
              case "P-256" -> "secp256r1";
              case "P-384" -> "secp384r1";
              default -> throw new IllegalArgumentException("Unsupported curve");
            };
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curve));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(integer(jwk, "x"), integer(jwk, "y"));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec));
      }
      case "RSA" -> {
        return KeyFactory.getInstance("RSA")
            .generatePublic(new RSAPublicKeySpec(integer(jwk, "n"), integer(jwk, "e")));
      }
      default -> throw new IllegalArgumentException("Unsupported key type " + kty);
    }
  }

  private static BigInteger integer(JsonNode jwk, String member) {
    return new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path(member).asText()));
  }
}

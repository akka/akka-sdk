/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aAgentCard;
import akka.javasdk.impl.a2a.AgentCardSigning.KeyResolver;
import akka.javasdk.impl.a2a.AgentCardSigning.Signer;
import akka.javasdk.impl.a2a.AgentCardSigning.Verification;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgentCardSigningTest {

  private static final ObjectMapper mapper = JsonSupport.getObjectMapper();
  private static final ObjectMapper exampleReader =
      mapper.copy().enable(JsonParser.Feature.ALLOW_COMMENTS);

  private static A2aAgentCard card() throws Exception {
    try (InputStream in =
        AgentCardSigningTest.class.getResourceAsStream(
            "/a2a/8.5-sample-agent-card.AgentCard.json")) {
      return exampleReader.readValue(in, A2aAgentCard.class);
    }
  }

  private static KeyPair keyPair(String algorithm) throws Exception {
    if (algorithm.startsWith("ES")) {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(
          new ECGenParameterSpec(algorithm.equals("ES256") ? "secp256r1" : "secp384r1"));
      return generator.generateKeyPair();
    }
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static A2aAgentCard viaJson(A2aAgentCard card) throws Exception {
    return mapper.readValue(mapper.writeValueAsString(card), A2aAgentCard.class);
  }

  private static A2aAgentCard withSignatures(
      A2aAgentCard c, List<A2aAgentCard.Signature> signatures) {
    return new A2aAgentCard(
        c.name(),
        c.description(),
        c.supportedInterfaces(),
        c.provider(),
        c.version(),
        c.documentationUrl(),
        c.capabilities(),
        c.securitySchemes(),
        c.securityRequirements(),
        c.defaultInputModes(),
        c.defaultOutputModes(),
        c.skills(),
        signatures,
        c.iconUrl());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ES256", "ES384", "RS256", "RS384", "RS512"})
  void aSignedCardVerifiesAfterItIsSentAsJson(String algorithm) throws Exception {
    KeyPair keys = keyPair(algorithm);
    A2aAgentCard signed =
        AgentCardSigning.sign(
            card(),
            new Signer(keys.getPrivate(), "key-1", algorithm, "https://example.com/jwks.json"));

    assertEquals(1, signed.signatures().size());
    String header =
        new String(
            Base64.getUrlDecoder().decode(signed.signatures().get(0).protectedHeader()),
            StandardCharsets.UTF_8);
    assertEquals(
        "{\"alg\":\""
            + algorithm
            + "\",\"typ\":\"JOSE\",\"kid\":\"key-1\",\"jku\":\"https://example.com/jwks.json\"}",
        header);

    Verification result =
        AgentCardSigning.verify(viaJson(signed), KeyResolver.forKey("key-1", keys.getPublic()));
    assertTrue(result.verified(), result.failure());
    assertEquals("key-1", result.keyId());
  }

  @Test
  void aSignatureVerifiesAgainstAJwks() throws Exception {
    KeyPair ec = keyPair("ES256");
    KeyPair rsa = keyPair("RS256");
    String jwks =
        "{\"keys\":["
            + jwk("ec-key", ec.getPublic())
            + ","
            + jwk("rsa-key", rsa.getPublic())
            + "]}";

    A2aAgentCard byEc =
        AgentCardSigning.sign(card(), new Signer(ec.getPrivate(), "ec-key", "ES256", null));
    A2aAgentCard byRsa =
        AgentCardSigning.sign(card(), new Signer(rsa.getPrivate(), "rsa-key", "RS256", null));
    assertTrue(AgentCardSigning.verify(byEc, KeyResolver.forJwks(jwks)).verified());
    assertTrue(AgentCardSigning.verify(byRsa, KeyResolver.forJwks(jwks)).verified());
  }

  @Test
  void aCardAlteredAfterSigningIsRefused() throws Exception {
    KeyPair keys = keyPair("ES256");
    A2aAgentCard signed =
        AgentCardSigning.sign(card(), new Signer(keys.getPrivate(), "k", "ES256", null));
    A2aAgentCard altered =
        new A2aAgentCard(
            signed.name(),
            signed.description() + " (changed)",
            signed.supportedInterfaces(),
            signed.provider(),
            signed.version(),
            signed.documentationUrl(),
            signed.capabilities(),
            signed.securitySchemes(),
            signed.securityRequirements(),
            signed.defaultInputModes(),
            signed.defaultOutputModes(),
            signed.skills(),
            signed.signatures(),
            signed.iconUrl());

    Verification result =
        AgentCardSigning.verify(altered, KeyResolver.forKey("k", keys.getPublic()));
    assertFalse(result.verified());
    assertNotNull(result.failure());
  }

  @Test
  void aSignatureWithTheWrongKeyIdIsRefused() throws Exception {
    KeyPair keys = keyPair("ES256");
    A2aAgentCard signed =
        AgentCardSigning.sign(card(), new Signer(keys.getPrivate(), "key-1", "ES256", null));

    Verification result =
        AgentCardSigning.verify(signed, KeyResolver.forKey("key-2", keys.getPublic()));
    assertFalse(result.verified());
    assertTrue(result.failure().contains("key-1"));
  }

  @Test
  void aSignatureMadeWithAnotherKeyIsRefused() throws Exception {
    KeyPair signing = keyPair("ES256");
    KeyPair other = keyPair("ES256");
    A2aAgentCard signed =
        AgentCardSigning.sign(card(), new Signer(signing.getPrivate(), "k", "ES256", null));

    assertFalse(
        AgentCardSigning.verify(signed, KeyResolver.forKey("k", other.getPublic())).verified());
  }

  @Test
  void aKeyThatDoesNotFitTheAlgorithmIsRefused() throws Exception {
    KeyPair ec = keyPair("ES256");
    KeyPair rsa = keyPair("RS256");
    assertThrows(
        IllegalArgumentException.class, () -> new Signer(rsa.getPrivate(), "k", "ES256", null));
    A2aAgentCard signed =
        AgentCardSigning.sign(card(), new Signer(ec.getPrivate(), "k", "ES256", null));
    assertFalse(
        AgentCardSigning.verify(signed, KeyResolver.forKey("k", rsa.getPublic())).verified());
  }

  @Test
  void aCardWithoutSignaturesIsNotVerified() throws Exception {
    Verification result =
        AgentCardSigning.verify(card(), KeyResolver.forKey("k", keyPair("ES256").getPublic()));
    assertFalse(result.verified());
  }

  @Test
  void aSignatureWithAnUnsupportedAlgorithmIsRefused() throws Exception {
    KeyPair keys = keyPair("ES256");
    String none =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"alg\":\"none\",\"kid\":\"k\"}".getBytes(StandardCharsets.UTF_8));
    A2aAgentCard forged =
        withSignatures(card(), List.of(new A2aAgentCard.Signature(none, "", null)));
    assertFalse(
        AgentCardSigning.verify(forged, KeyResolver.forKey("k", keys.getPublic())).verified());
  }

  @Test
  void oneValidSignatureAmongSeveralVerifiesTheCard() throws Exception {
    KeyPair oldKey = keyPair("ES256");
    KeyPair newKey = keyPair("ES256");
    A2aAgentCard byOld =
        AgentCardSigning.sign(card(), new Signer(oldKey.getPrivate(), "old", "ES256", null));
    A2aAgentCard byNew =
        AgentCardSigning.sign(card(), new Signer(newKey.getPrivate(), "new", "ES256", null));
    A2aAgentCard both =
        withSignatures(byNew, List.of(byOld.signatures().get(0), byNew.signatures().get(0)));

    Verification result =
        AgentCardSigning.verify(both, KeyResolver.forKey("new", newKey.getPublic()));
    assertTrue(result.verified());
    assertEquals("new", result.keyId());
  }

  @Test
  void theSignerIsReadFromConfiguration() throws Exception {
    KeyPair keys = keyPair("ES256");
    String pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keys.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";

    Signer signer = Signer.fromConfig(signingConfig(pem, "cfg-key", "ES256")).orElseThrow();
    assertEquals("cfg-key", signer.keyId());
    assertNull(signer.jku());
    assertFalse(signer.toString().contains("PRIVATE"));

    A2aAgentCard signed = AgentCardSigning.sign(card(), signer);
    assertTrue(
        AgentCardSigning.verify(signed, KeyResolver.forKey("cfg-key", keys.getPublic()))
            .verified());

    assertTrue(Signer.fromConfig(signingConfig("", "", "ES256")).isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () -> Signer.fromConfig(signingConfig("not a key", "k", "ES256")));
    assertThrows(
        IllegalArgumentException.class, () -> Signer.fromConfig(signingConfig(pem, "", "ES256")));
  }

  @Test
  void referenceConfigurationLeavesSigningOff() {
    Config signing = ConfigFactory.load().getConfig("akka.javasdk.a2a.endpoint.signing");
    assertTrue(Signer.fromConfig(signing).isEmpty());
  }

  private static Config signingConfig(String key, String keyId, String algorithm) {
    return ConfigFactory.parseMap(
        Map.of("key", key, "key-id", keyId, "algorithm", algorithm, "jku", ""));
  }

  // The JWK members are built here from the key's own numbers, so that the reader is checked
  // against an independent encoding.
  private static String jwk(String kid, PublicKey key) {
    Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
    if (key instanceof ECPublicKey ec) {
      return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"kid\":\""
          + kid
          + "\",\"x\":\""
          + b64.encodeToString(fixed(ec.getW().getAffineX(), 32))
          + "\",\"y\":\""
          + b64.encodeToString(fixed(ec.getW().getAffineY(), 32))
          + "\"}";
    }
    RSAPublicKey rsa = (RSAPublicKey) key;
    return "{\"kty\":\"RSA\",\"kid\":\""
        + kid
        + "\",\"n\":\""
        + b64.encodeToString(unsigned(rsa.getModulus()))
        + "\",\"e\":\""
        + b64.encodeToString(unsigned(rsa.getPublicExponent()))
        + "\"}";
  }

  private static byte[] unsigned(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes[0] == 0 && bytes.length > 1) {
      byte[] trimmed = new byte[bytes.length - 1];
      System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
      return trimmed;
    }
    return bytes;
  }

  private static byte[] fixed(BigInteger value, int size) {
    byte[] raw = unsigned(value);
    byte[] out = new byte[size];
    System.arraycopy(raw, 0, out, size - raw.length, raw.length);
    return out;
  }
}

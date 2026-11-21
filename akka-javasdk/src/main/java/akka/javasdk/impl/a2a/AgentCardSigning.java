/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aAgentCard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * INTERNAL API
 *
 * <p>Signing and verification of an Agent Card as JSON Web Signatures (A2A v1.0, section 8.4).
 *
 * <p>The payload of every signature is the canonical form of the card: the card as JSON without its
 * {@code signatures}, with the members that hold a default value removed, canonicalized as RFC 8785
 * requires. The signature entries are detached JWS values: each has a protected header and a
 * signature, and the payload is recomputed from the card on verification. The signing input is
 * {@code BASE64URL(protected header) + "." + BASE64URL(canonical payload)}.
 *
 * <p>The supported algorithms are ES256, ES384, RS256, RS384 and RS512. Keys that do not fit the
 * algorithm, and RSA keys under 2048 bits, are refused.
 */
@InternalApi
public final class AgentCardSigning {
  private AgentCardSigning() {}

  private static final ObjectMapper mapper = JsonSupport.getObjectMapper();
  private static final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder base64UrlDecoder = Base64.getUrlDecoder();

  /**
   * The key that signs the card and what the signature says about it.
   *
   * @param key the private key
   * @param keyId the {@code kid} of the signature: how a verifier finds the public key
   * @param algorithm the JWS algorithm, for example {@code ES256}
   * @param jku a URL of the JSON Web Key Set that holds the public key, or null
   */
  public record Signer(PrivateKey key, String keyId, String algorithm, String jku) {

    public Signer {
      if (key == null) throw new IllegalArgumentException("A signing key is required");
      if (keyId == null || keyId.isBlank()) {
        throw new IllegalArgumentException("A key id is required");
      }
      Algorithm.of(algorithm).checkKey(key);
      if (jku != null && jku.isBlank()) jku = null;
    }

    /**
     * Reads the {@code akka.javasdk.a2a.endpoint.signing} section: {@code key} (PEM text, PKCS#8)
     * or {@code key-file} (the path of a PEM file), {@code key-id}, {@code algorithm} and the
     * optional {@code jku}.
     *
     * @return empty when no key is configured, which means cards are served unsigned
     * @throws IllegalArgumentException when a key is configured but is unusable or has no key id
     */
    public static Optional<Signer> fromConfig(Config signing) {
      String text = signing.getString("key").strip();
      String file = signing.hasPath("key-file") ? signing.getString("key-file").strip() : "";
      if (!text.isEmpty() && !file.isEmpty()) {
        throw new IllegalArgumentException(
            "Set only one of akka.javasdk.a2a.endpoint.signing.key and"
                + " akka.javasdk.a2a.endpoint.signing.key-file");
      }
      String pem;
      if (!file.isEmpty()) {
        try {
          pem = Files.readString(Path.of(file));
        } catch (IOException | RuntimeException e) {
          // The cause is not in the error, so that a key pasted into the setting is not printed
          throw new IllegalArgumentException(
              "akka.javasdk.a2a.endpoint.signing.key-file must be the path of a readable PEM file");
        }
      } else {
        pem = text;
      }
      if (pem.isEmpty()) return Optional.empty();
      String algorithm = signing.getString("algorithm");
      PrivateKey key = parsePrivateKey(pem, Algorithm.of(algorithm));
      return Optional.of(
          new Signer(key, signing.getString("key-id"), algorithm, signing.getString("jku")));
    }

    // The key is never printed.
    @Override
    public String toString() {
      return "Signer(keyId=" + keyId + ", algorithm=" + algorithm + ", jku=" + jku + ")";
    }
  }

  /** Finds the public key for a signature from the {@code kid} and {@code jku} of its header. */
  @FunctionalInterface
  public interface KeyResolver {
    /**
     * @param keyId the {@code kid} of the signature
     * @param jku the {@code jku} of the signature, or null. It is chosen by whoever produced the
     *     card, so a resolver must fetch it only when the address is one it trusts.
     * @return the key, or empty when the resolver does not know the key id
     */
    Optional<PublicKey> resolve(String keyId, String jku);

    /** Accepts only signatures with this key id, and verifies them with this key. */
    static KeyResolver forKey(String keyId, PublicKey key) {
      return (kid, jku) -> keyId.equals(kid) ? Optional.of(key) : Optional.empty();
    }

    /** Looks the key id up in a JSON Web Key Set that the caller has already fetched. */
    static KeyResolver forJwks(String jwksJson) {
      return (kid, jku) -> Jwks.find(jwksJson, kid);
    }

    /** Verifies every signature with this key, whatever its key id. */
    static KeyResolver forAnyKeyId(PublicKey key) {
      return (kid, jku) -> Optional.of(key);
    }
  }

  /**
   * The outcome of verifying a card.
   *
   * @param verified true when at least one signature of the card is valid
   * @param keyId the key id of the first valid signature, or null
   * @param failure why the card was not verified, or null when it was
   */
  public record Verification(boolean verified, String keyId, String failure) {}

  /**
   * Signs the card. The card's existing signatures are replaced by one signature made with the
   * given signer.
   */
  public static A2aAgentCard sign(A2aAgentCard card, Signer signer) {
    Algorithm algorithm = Algorithm.of(signer.algorithm());
    // Members are written in this order, as the specification's example does.
    ObjectNode header = JsonNodeFactory.instance.objectNode();
    header.put("alg", signer.algorithm());
    header.put("typ", "JOSE");
    header.put("kid", signer.keyId());
    if (signer.jku() != null) header.put("jku", signer.jku());
    String protectedHeader =
        base64Url.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));

    String signingInput = protectedHeader + "." + base64Url.encodeToString(canonicalPayload(card));
    byte[] signature;
    try {
      Signature jca = Signature.getInstance(algorithm.jca);
      jca.initSign(signer.key());
      jca.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      signature = jca.sign();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not sign the Agent Card", e);
    }
    if (algorithm.ecSize > 0) signature = derToJose(signature, algorithm.ecSize);

    A2aAgentCard.Signature entry =
        new A2aAgentCard.Signature(protectedHeader, base64Url.encodeToString(signature), null);
    return withSignatures(card, List.of(entry));
  }

  /**
   * Verifies the signatures of the card. The card is verified when at least one of its signatures
   * is valid, as the specification advises. A signature is invalid when its header cannot be read,
   * its algorithm is not supported, the resolver has no key for its key id, the key does not fit
   * the algorithm, or the signature does not match the card as it is now.
   *
   * <p>Never throws for a bad card or signature; the reasons are collected in {@link
   * Verification#failure()}.
   */
  public static Verification verify(A2aAgentCard card, KeyResolver resolver) {
    if (card.signatures().isEmpty()) {
      return new Verification(false, null, "The card has no signatures");
    }
    String encodedPayload;
    try {
      encodedPayload = base64Url.encodeToString(canonicalPayload(card));
    } catch (RuntimeException e) {
      return new Verification(false, null, "The card cannot be canonicalized: " + e.getMessage());
    }
    return verify(card.signatures(), encodedPayload, resolver);
  }

  /**
   * Verifies the signatures of a card as it was received, so that members this version of the
   * protocol does not know are covered by the signature too. Otherwise as {@link
   * #verify(A2aAgentCard, KeyResolver)}.
   */
  public static Verification verify(JsonNode card, KeyResolver resolver) {
    if (card == null || !card.isObject()) {
      return new Verification(false, null, "The card is not a JSON object");
    }
    List<A2aAgentCard.Signature> signatures;
    String encodedPayload;
    try {
      JsonNode entries = card.path("signatures");
      signatures = new ArrayList<>();
      for (JsonNode entry : entries) {
        signatures.add(mapper.treeToValue(entry, A2aAgentCard.Signature.class));
      }
      ObjectNode copy = card.deepCopy();
      addRequired(copy);
      encodedPayload = base64Url.encodeToString(Jcs.canonicalize(canonicalPayload(copy)));
    } catch (IOException | RuntimeException e) {
      return new Verification(false, null, "The card cannot be canonicalized: " + e.getMessage());
    }
    if (signatures.isEmpty()) return new Verification(false, null, "The card has no signatures");
    return verify(signatures, encodedPayload, resolver);
  }

  /**
   * Reads a public key from PEM text ({@code -----BEGIN PUBLIC KEY-----}, X.509
   * SubjectPublicKeyInfo), an EC or an RSA key.
   *
   * @throws IllegalArgumentException when the text is not such a key
   */
  public static PublicKey parsePublicKey(String pem) {
    String base64 =
        pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der;
    try {
      der = Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The public key is not PEM text");
    }
    for (String type : List.of("EC", "RSA")) {
      try {
        return KeyFactory.getInstance(type).generatePublic(new X509EncodedKeySpec(der));
      } catch (GeneralSecurityException e) {
        // try the next key type
      }
    }
    throw new IllegalArgumentException("The public key is not an EC or RSA public key");
  }

  private static Verification verify(
      List<A2aAgentCard.Signature> signatures, String encodedPayload, KeyResolver resolver) {
    List<String> failures = new ArrayList<>();
    for (A2aAgentCard.Signature entry : signatures) {
      try {
        return new Verification(true, verifyOne(entry, encodedPayload, resolver), null);
      } catch (SignatureFailure | GeneralSecurityException | IOException | RuntimeException e) {
        failures.add(e.getMessage());
      }
    }
    return new Verification(false, null, String.join("; ", failures));
  }

  private static String verifyOne(
      A2aAgentCard.Signature entry, String encodedPayload, KeyResolver resolver)
      throws SignatureFailure, GeneralSecurityException, IOException {
    if (entry.protectedHeader() == null || entry.signature() == null) {
      throw new SignatureFailure("The signature has no protected header or no signature value");
    }
    JsonNode header = mapper.readTree(base64UrlDecoder.decode(entry.protectedHeader()));
    String alg = header.path("alg").asText("");
    String kid = header.path("kid").asText("");
    String jku = header.path("jku").asText(null);
    Algorithm algorithm =
        Algorithm.find(alg)
            .orElseThrow(() -> new SignatureFailure("Unsupported algorithm '" + alg + "'"));
    if (kid.isEmpty()) throw new SignatureFailure("The protected header has no kid");
    PublicKey key =
        resolver
            .resolve(kid, jku)
            .orElseThrow(() -> new SignatureFailure("No key for key id '" + kid + "'"));
    algorithm.checkKey(key);

    byte[] signature = base64UrlDecoder.decode(entry.signature());
    if (algorithm.ecSize > 0) signature = joseToDer(signature, algorithm.ecSize);
    Signature jca = Signature.getInstance(algorithm.jca);
    jca.initVerify(key);
    jca.update(
        (entry.protectedHeader() + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII));
    if (!jca.verify(signature)) {
      throw new SignatureFailure("The signature for key id '" + kid + "' does not match the card");
    }
    return kid;
  }

  /**
   * The canonical payload of a card, as UTF-8 bytes.
   *
   * <p>Fields marked required in the protocol definition are written even when empty; every other
   * field that is null, empty or the default of its type is left out. The extension {@code params}
   * are signed as they are.
   */
  static byte[] canonicalPayload(A2aAgentCard card) {
    ObjectNode tree = mapper.valueToTree(card);
    addRequired(tree);
    return Jcs.canonicalize(canonicalPayload(tree));
  }

  /** As {@link #canonicalPayload(A2aAgentCard)}, for a card that is already a JSON tree. */
  static JsonNode canonicalPayload(JsonNode card) {
    ObjectNode copy = card.deepCopy();
    copy.remove("signatures");
    return strip(copy, "");
  }

  private static final Set<String> TOP =
      Set.of(
          "name",
          "description",
          "supportedInterfaces",
          "version",
          "capabilities",
          "defaultInputModes",
          "defaultOutputModes",
          "skills");

  // The JSON of a record leaves out empty values, so the required ones are added back.
  private static void addRequired(ObjectNode card) {
    JsonNodeFactory nodes = JsonNodeFactory.instance;
    for (String name : TOP) {
      if (!card.has(name)) {
        card.set(
            name,
            switch (name) {
              case "supportedInterfaces", "defaultInputModes", "defaultOutputModes", "skills" ->
                  nodes.arrayNode();
              case "capabilities" -> nodes.objectNode();
              default -> nodes.textNode("");
            });
      }
    }
    for (JsonNode element : card.path("supportedInterfaces")) {
      requireStrings((ObjectNode) element, "url", "protocolBinding", "protocolVersion");
    }
    for (JsonNode skill : card.path("skills")) {
      requireStrings((ObjectNode) skill, "id", "name", "description");
      if (!skill.has("tags")) ((ObjectNode) skill).set("tags", nodes.arrayNode());
    }
    if (card.path("provider").isObject()) {
      requireStrings((ObjectNode) card.get("provider"), "url", "organization");
    }
  }

  private static void requireStrings(ObjectNode object, String... names) {
    for (String name : names) {
      if (!object.has(name)) object.put(name, "");
    }
  }

  // The members that are required where they are found; "" is the top level of the card.
  private static Set<String> requiredAt(String path) {
    return switch (path) {
      case "" -> TOP;
      case "provider" -> Set.of("url", "organization");
      case "supportedInterfaces[]" -> Set.of("url", "protocolBinding", "protocolVersion");
      case "skills[]" -> Set.of("id", "name", "description", "tags");
      default -> Set.of();
    };
  }

  private static boolean isDefault(JsonNode value) {
    return value.isNull()
        || (value.isTextual() && value.textValue().isEmpty())
        || (value.isContainerNode() && value.isEmpty());
  }

  private static JsonNode strip(JsonNode node, String path) {
    if (node.isObject()) {
      ObjectNode out = JsonNodeFactory.instance.objectNode();
      Set<String> required = requiredAt(path);
      node.fields()
          .forEachRemaining(
              member -> {
                String name = member.getKey();
                String childPath = path.isEmpty() ? name : path + "." + name;
                JsonNode value = member.getValue();
                // An extension's required flag is a plain boolean, so false is its default.
                if (path.equals("capabilities.extensions[]")
                    && name.equals("required")
                    && !value.asBoolean(false)) {
                  return;
                }
                // Struct payloads are signed exactly as given.
                JsonNode kept =
                    childPath.equals("capabilities.extensions[].params")
                        ? value
                        : strip(value, childPath);
                if (isDefault(kept) && !required.contains(name)) return;
                out.set(name, kept);
              });
      return out;
    }
    if (node.isArray()) {
      ArrayNode out = JsonNodeFactory.instance.arrayNode();
      for (JsonNode element : node) out.add(strip(element, path + "[]"));
      return out;
    }
    return node;
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

  private static PrivateKey parsePrivateKey(String pem, Algorithm algorithm) {
    String base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    try {
      byte[] der = Base64.getDecoder().decode(base64);
      return KeyFactory.getInstance(algorithm.ecSize > 0 ? "EC" : "RSA")
          .generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "akka.javasdk.a2a.endpoint.signing.key is not a PKCS#8 PEM private key for "
              + algorithm.name,
          e);
    }
  }

  private static final class SignatureFailure extends Exception {
    SignatureFailure(String message) {
      super(message);
    }
  }

  private enum Algorithm {
    ES256("ES256", "SHA256withECDSA", 32, 256),
    ES384("ES384", "SHA384withECDSA", 48, 384),
    RS256("RS256", "SHA256withRSA", 0, 0),
    RS384("RS384", "SHA384withRSA", 0, 0),
    RS512("RS512", "SHA512withRSA", 0, 0);

    final String name;
    final String jca;
    // Bytes of each of r and s in the JWS form; zero for RSA.
    final int ecSize;
    final int curveBits;

    Algorithm(String name, String jca, int ecSize, int curveBits) {
      this.name = name;
      this.jca = jca;
      this.ecSize = ecSize;
      this.curveBits = curveBits;
    }

    static Optional<Algorithm> find(String name) {
      for (Algorithm a : values()) if (a.name.equals(name)) return Optional.of(a);
      return Optional.empty();
    }

    static Algorithm of(String name) {
      return find(name)
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "Unsupported signing algorithm '"
                          + name
                          + "'; use ES256, ES384, RS256, RS384 or RS512"));
    }

    void checkKey(Key key) {
      if (ecSize > 0) {
        if (!(key instanceof ECKey ec) || ec.getParams().getOrder().bitLength() != curveBits) {
          throw new IllegalArgumentException(name + " needs an EC key on curve P-" + curveBits);
        }
      } else if (!(key instanceof RSAKey rsa) || rsa.getModulus().bitLength() < 2048) {
        throw new IllegalArgumentException(name + " needs an RSA key of at least 2048 bits");
      }
    }
  }

  // ECDSA signatures from the JDK are DER; JWS (RFC 7518, 3.4) uses r and s as fixed-width
  // integers.
  private static byte[] derToJose(byte[] der, int size) {
    int offset = (der[1] & 0xff) == 0x81 ? 3 : 2;
    byte[] result = new byte[size * 2];
    int rLength = der[offset + 1] & 0xff;
    copyInteger(der, offset + 2, rLength, result, 0, size);
    int sOffset = offset + 2 + rLength;
    int sLength = der[sOffset + 1] & 0xff;
    copyInteger(der, sOffset + 2, sLength, result, size, size);
    return result;
  }

  private static void copyInteger(
      byte[] from, int start, int length, byte[] to, int toStart, int size) {
    while (length > size && from[start] == 0) {
      start++;
      length--;
    }
    System.arraycopy(from, start, to, toStart + size - length, length);
  }

  private static byte[] joseToDer(byte[] jose, int size) throws SignatureFailure {
    if (jose.length != size * 2) {
      throw new SignatureFailure("The signature has the wrong length for its algorithm");
    }
    byte[] r = derInteger(jose, 0, size);
    byte[] s = derInteger(jose, size, size);
    int length = 2 + r.length + 2 + s.length;
    byte[] der = new byte[(length >= 128 ? 3 : 2) + length];
    int i = 0;
    der[i++] = 0x30;
    if (length >= 128) der[i++] = (byte) 0x81;
    der[i++] = (byte) length;
    der[i++] = 0x02;
    der[i++] = (byte) r.length;
    System.arraycopy(r, 0, der, i, r.length);
    i += r.length;
    der[i++] = 0x02;
    der[i++] = (byte) s.length;
    System.arraycopy(s, 0, der, i, s.length);
    return der;
  }

  private static byte[] derInteger(byte[] bytes, int start, int size) {
    int from = start;
    int end = start + size;
    while (from < end - 1 && bytes[from] == 0) from++;
    boolean pad = (bytes[from] & 0x80) != 0;
    byte[] out = new byte[end - from + (pad ? 1 : 0)];
    System.arraycopy(bytes, from, out, pad ? 1 : 0, end - from);
    return out;
  }
}

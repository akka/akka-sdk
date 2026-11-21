/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * INTERNAL API
 *
 * <p>The agent session id of an A2A context. A context id comes from the client, so the session id
 * also depends on the caller: two callers that send the same context id get different sessions. A
 * request without an authenticated subject is in one anonymous namespace, so callers without a
 * subject that send the same context id share a session.
 */
@InternalApi
public final class A2aSessions {

  /** The prefix of every session id of an A2A context. */
  public static final String PREFIX = "a2a-";

  private A2aSessions() {}

  /**
   * @param issuer the issuer of the caller's token, or null
   * @param subject the subject of the caller's token, or null for an anonymous caller
   * @param contextId the A2A context id of the message
   */
  public static String sessionId(String issuer, String subject, String contextId) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    // Each value is length-prefixed, so no two different inputs give the same bytes
    if (subject == null) {
      update(digest, "anonymous");
    } else {
      update(digest, "subject");
      update(digest, issuer == null ? "" : issuer);
      update(digest, subject);
    }
    update(digest, contextId);
    return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
  }

  private static void update(MessageDigest digest, String value) {
    var bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
    digest.update(bytes);
  }
}

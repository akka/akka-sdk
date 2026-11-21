/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class A2aSessionsTest {

  @Test
  void theSameCallerAndContextGetTheSameSession() {
    assertEquals(
        A2aSessions.sessionId("issuer", "ada", "context-1"),
        A2aSessions.sessionId("issuer", "ada", "context-1"));
  }

  @Test
  void anotherSubjectOrIssuerGetsAnotherSession() {
    var session = A2aSessions.sessionId("issuer", "ada", "context-1");
    assertNotEquals(session, A2aSessions.sessionId("issuer", "bob", "context-1"));
    assertNotEquals(session, A2aSessions.sessionId("other-issuer", "ada", "context-1"));
    assertNotEquals(session, A2aSessions.sessionId("issuer", "ada", "context-2"));
  }

  @Test
  void anAnonymousCallerIsInItsOwnNamespace() {
    var anonymous = A2aSessions.sessionId(null, null, "context-1");
    assertEquals(anonymous, A2aSessions.sessionId("ignored", null, "context-1"));
    assertNotEquals(anonymous, A2aSessions.sessionId(null, "anonymous", "context-1"));
    assertNotEquals(anonymous, A2aSessions.sessionId("", "", "context-1"));
  }

  @Test
  void valuesAreSeparatedSoTheirBoundaryCannotMove() {
    assertNotEquals(
        A2aSessions.sessionId("ab", "c", "context"), A2aSessions.sessionId("a", "bc", "context"));
  }

  @Test
  void theSessionIdNeverIsTheContextId() {
    var session = A2aSessions.sessionId(null, null, "context-1");
    assertTrue(session.startsWith(A2aSessions.PREFIX));
    assertTrue(session.matches("a2a-[A-Za-z0-9_-]{43}"));
  }
}

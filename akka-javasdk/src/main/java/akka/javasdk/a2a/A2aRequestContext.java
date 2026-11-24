/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.SpiffeContext;
import akka.javasdk.Tracing;
import java.util.List;
import java.util.Optional;

/**
 * Context information available while an A2A endpoint handles a message.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface A2aRequestContext {

  /**
   * @return The principals associated with this request.
   */
  Principals getPrincipals();

  /**
   * @return The JWT claims associated with this request. Fails if the endpoint has no {@link
   *     akka.javasdk.annotations.JWT} annotation.
   */
  JwtClaims getJwtClaims();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();

  /**
   * @return A header with the given name (case ignored) if present in the current request.
   */
  Optional<HttpHeader> requestHeader(String headerName);

  /**
   * @return A list with all the headers of the current request
   */
  List<HttpHeader> allRequestHeaders();

  /**
   * The SPIFFE context for this endpoint, including the caller's identity if the request came from
   * another Akka service in the same project. Empty when SPIFFE is disabled.
   */
  Optional<SpiffeContext> getSpiffeContext();

  /**
   * The identity of the caller. This is the issuer and the subject of the JWT if the request has a
   * JWT with a subject, or else the name of the calling service. Empty for an anonymous caller.
   */
  Optional<String> callerId();

  /**
   * The context id of the conversation. This is the context id of the message. The SDK generates
   * one for the first message of a conversation, and sends it to the caller in the reply.
   */
  String contextId();

  /**
   * An id for a session of an agent, to keep the history of the conversation. The id is scoped by
   * the caller and the context id, so a caller cannot reach the session of another caller by
   * sending the same context id. An anonymous caller has no identity to scope by, so each request
   * of an anonymous caller gets a new session id.
   */
  String sessionId();
}

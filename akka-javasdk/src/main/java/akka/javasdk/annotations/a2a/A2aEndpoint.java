/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.a2a;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an Agent2Agent (A2A) protocol endpoint. The class must extend {@link
 * akka.javasdk.a2a.AbstractA2aEndpoint}.
 *
 * <p>The endpoint publishes agents of the service to A2A clients. It serves the A2A methods over
 * the JSON-RPC and HTTP+JSON bindings at {@link #path()}, and the Agent Card at {@code
 * /.well-known/agent-card.json}.
 *
 * <p>A service has at most one A2A endpoint, because it has one Agent Card.
 *
 * <p>{@link akka.javasdk.annotations.JWT} and {@link akka.javasdk.annotations.Acl} on the class
 * apply to every A2A route. The Agent Card route applies the {@code @Acl} rules and needs no token.
 *
 * <p>The class can accept the same constructor parameters as an HTTP endpoint.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface A2aEndpoint {

  /** The path of the A2A routes. */
  String path() default "/a2a";

  /** The name of the agent in the Agent Card. */
  String name();

  /** The description of the agent in the Agent Card. Defaults to the name. */
  String description() default "";

  /** The version of the agent in the Agent Card, for example {@code 1.0.0}. */
  String version();

  /**
   * The public base URL of the service, for example {@code https://claims.example.com}. The Agent
   * Card uses it to build the URLs of the A2A routes. When empty, the setting {@code
   * akka.javasdk.a2a.endpoint.public-url} applies, and when that is empty too, the scheme and host
   * of the request for the Agent Card.
   */
  String publicUrl() default "";
}

/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.a2a;

import java.lang.annotation.*;

/**
 * Annotation to mark a class as an A2A (Agent2Agent protocol) agent that other agents can call.
 *
 * <p>The annotated class must extend {@link akka.javasdk.a2a.AbstractA2aEndpoint}. The class is the
 * A2A agent. It translates between A2A messages and the components of the service. Components are
 * not exposed to A2A callers directly.
 *
 * <p>The runtime serves the Agent Card at {@code <path>/.well-known/agent-card.json}, and the A2A
 * JSON-RPC interface at {@code path}. The endpoint at the root path also serves the card at {@code
 * /.well-known/agent-card.json}. A service can have several A2A endpoints, each with its own path.
 * Only one can be at the root, and no path can be used by another endpoint.
 *
 * <p>Access is controlled with {@link akka.javasdk.annotations.Acl} and {@link
 * akka.javasdk.annotations.JWT} on the class. They apply to the card and to all calls.
 *
 * <p><strong>Constructor Injection:</strong> Annotated classes can accept the same types as an
 * {@link akka.javasdk.annotations.mcp.McpEndpoint}, and {@link akka.javasdk.a2a.A2aRequestContext}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface A2aEndpoint {
  /**
   * @return The path to publish the endpoint at, defaults to the root path
   */
  String path() default "/";

  /**
   * @return Name of the agent in the Agent Card
   */
  String name();

  /**
   * @return Description of the agent in the Agent Card
   */
  String description();

  /**
   * @return Version of the agent in the Agent Card
   */
  String version();

  /**
   * @return Name of the organization that provides the agent, empty for none
   */
  String providerOrganization() default "";

  /**
   * @return Web site of the organization that provides the agent, empty for none
   */
  String providerUrl() default "";

  /**
   * @return The skills to list in the Agent Card
   */
  A2aSkill[] skills() default {};
}

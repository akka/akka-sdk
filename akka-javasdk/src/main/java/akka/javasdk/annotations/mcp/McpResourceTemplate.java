/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

import java.lang.annotation.*;

/**
 * A template description for resources available on the server.
 * <p>
 * Annotated on the MCP endpoint class to declare a template for finding resources.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResourceTemplate {
  /**
   * @return A URI template (according to RFC 6570) that can be used to construct resource URIs.
   */
  String uriTemplate();
  /**
   * @return A human-readable name for the type of resource this template refers to.\n\nThis can be used by clients to populate UI elements.
   */
  String name();
  /**
   * @return A description of what this template is for. Clients can use this to improve the LLM's understanding of available resources. It can be thought of as a "hint" to the model.
   */
  String description();

  /**
   * @return The MIME type for all resources that match this template. This should only be included if all resources matching this template have the same type.
   */
  String mimeType();
}

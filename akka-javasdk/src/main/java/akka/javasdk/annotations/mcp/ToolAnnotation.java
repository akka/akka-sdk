/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

/**
 * Behavioral annotations that describe MCP tool characteristics to clients.
 *
 * <p>These annotations provide hints about tool behavior to help AI models make informed decisions
 * about when and how to use tools. They describe properties like whether a tool modifies data, can
 * be called repeatedly safely, or interacts with external systems.
 *
 * <p><strong>Usage:</strong> Apply these annotations to {@link McpTool} methods via the {@link
 * McpTool#annotations()} attribute to help clients understand tool behavior patterns.
 *
 * <p><strong>Security Note:</strong> All values are <strong>hints only</strong> and are not
 * guaranteed to provide a faithful description of actual tool behavior. Clients should never make
 * security-critical tool use decisions based on ToolAnnotations received from untrusted servers.
 *
 * <p><strong>Annotation Pairs:</strong> Annotations are defined as opposites to allow clear
 * specification in Java annotations:
 *
 * <ul>
 *   <li>{@link #Destructive} vs {@link #NonDestructive}
 *   <li>{@link #Idempotent} vs {@link #NonIdempotent}
 *   <li>{@link #OpenWorld} vs {@link #ClosedWorld}
 *   <li>{@link #ReadOnly} vs {@link #Mutating}
 * </ul>
 */
public enum ToolAnnotation {
  // Note: defined as enum with opposites to allow usage in Java annotations

  /** The tool may perform destructive updates to its environment. Opposite of "NonDestructive". */
  Destructive,
  /** If false, the tool performs only additive updates. Opposite of "Destructive". */
  NonDestructive,
  /**
   * Calling the tool repeatedly with the same arguments will have no additional effect on the
   * environment. Opposite of "NonIdempotent".
   */
  Idempotent,
  /**
   * Calling the tool repeatedly with the same arguments will affect the environment each time.
   * Opposite of "Idempotent".
   */
  NonIdempotent,
  /**
   * This tool may interact with an \"open world\" of external entities. Opposite of "ClosedWorld".
   *
   * <p>For example, the world of a web search tool is open.
   */
  OpenWorld,
  /**
   * The tool's domain of interaction is closed. Opposite of "OpenWorld".
   *
   * <p>For example, a memory tool is non-open-world
   */
  ClosedWorld,
  /** The tool does not modify its environment. Opposite of "Mutating" */
  ReadOnly,
  /** The tool does modify its environment. Opposite of "ReadOnly". */
  Mutating
}

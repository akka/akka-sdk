/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

/**
 * Additional annotations describing a Tool to clients.
 *
 * <p>NOTE: all values are **hints**. They are not guaranteed to provide a faithful description of
 * tool behavior.
 *
 * <p>Clients should never make tool use decisions based on ToolAnnotations received from untrusted
 * servers.
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

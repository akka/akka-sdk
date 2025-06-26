/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

/** Context that provides access to metadata. */
public interface MetadataContext extends Context {
  /** Get the metadata associated with this context. */
  Metadata metadata();
}

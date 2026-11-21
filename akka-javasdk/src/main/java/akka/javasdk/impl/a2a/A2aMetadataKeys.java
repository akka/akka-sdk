/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;

/**
 * INTERNAL API
 *
 * <p>The keys of the metadata that the SDK writes into the A2A records it creates.
 */
@InternalApi
public final class A2aMetadataKeys {

  private A2aMetadataKeys() {}

  /** Marks the update that the SDK sends when following a remote task ends without an outcome. */
  public static final String FOLLOW_TIMED_OUT = "akka.followTimedOut";
}

/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.objectstorage;

import akka.util.ByteString;

/** An object retrieved from storage, including its metadata and full in-memory content. */
public final class StorageObject {
  /** Metadata for the object. */
  public final ObjectMetadata metadata;

  /** Full content of the object. */
  public final ByteString data;

  public StorageObject(ObjectMetadata metadata, ByteString data) {
    this.metadata = metadata;
    this.data = data;
  }
}

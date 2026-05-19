/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.objectstorage;

import akka.http.javadsl.model.ContentType;
import java.time.Instant;
import java.util.Optional;

/** Metadata for an object stored in a named bucket. */
public final class ObjectMetadata {
  /** The object key within the bucket. */
  public final String key;

  /** Size of the object in bytes. */
  public final long size;

  /** Content type, if set when the object was stored. */
  public final Optional<ContentType> contentType;

  /** Entity tag (opaque identifier for the object version), if provided by the backend. */
  public final Optional<String> eTag;

  /** Time at which the object was last modified. */
  public final Instant lastModified;

  public ObjectMetadata(
      String key,
      long size,
      Optional<ContentType> contentType,
      Optional<String> eTag,
      Instant lastModified) {
    this.key = key;
    this.size = size;
    this.contentType = contentType;
    this.eTag = eTag;
    this.lastModified = lastModified;
  }
}

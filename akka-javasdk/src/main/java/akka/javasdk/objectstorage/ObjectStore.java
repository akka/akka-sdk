/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.objectstorage;

import akka.Done;
import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.ContentType;
import akka.javasdk.agent.MessageContent.LoadableMessageContent;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Client for a single named bucket. Obtained via {@link ObjectStorage#forBucket(String)}.
 *
 * <p>The primary API is blocking and designed for use on Loom virtual threads. For reactive /
 * non-blocking use cases each operation also has an {@code Async} variant that returns a {@link
 * CompletionStage}.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface ObjectStore {

  // ── Primary (blocking / Loom-friendly) API ──────────────────────────────

  /**
   * Retrieve an object, returning both its metadata and full content, or {@link Optional#empty()}
   * if no object exists for the given key.
   *
   * <p>Blocks the calling thread until the operation completes. Safe to call on a Loom virtual
   * thread. For large objects prefer {@link #getStreamAsync(String)}.
   */
  Optional<StoreObject> get(String key);

  /**
   * Store an object without an explicit content type.
   *
   * <p>Blocks the calling thread until the operation completes. Use {@link #getMetadata(String)} to
   * retrieve metadata after the write if needed.
   *
   * @param key object key within the bucket
   * @param data content to store
   */
  Done put(String key, ByteString data);

  /**
   * Store an object with an explicit content type.
   *
   * <p>Blocks the calling thread until the operation completes. Use {@link #getMetadata(String)} to
   * retrieve metadata after the write if needed.
   *
   * @param key object key within the bucket
   * @param data content to store
   * @param contentType MIME type of the content
   */
  Done put(String key, ByteString data, ContentType contentType);

  /**
   * Delete the object with the given key. Succeeds silently if the key does not exist.
   *
   * <p>Blocks the calling thread until the operation completes.
   */
  Done delete(String key);

  /**
   * Retrieve only the metadata for an object without downloading its content, or {@link
   * Optional#empty()} if no object exists for the given key.
   *
   * <p>Blocks the calling thread until the operation completes.
   */
  Optional<ObjectMetadata> getMetadata(String key);

  // ── Streaming ───────────────────────────────────────────────────────────

  /**
   * List all objects whose keys start with {@code prefix}. Pass an empty string to list all objects
   * in the bucket.
   */
  Source<ObjectMetadata, NotUsed> list(String prefix);

  /** List all objects in the bucket. */
  default Source<ObjectMetadata, NotUsed> list() {
    return list("");
  }

  /**
   * Retrieve an object as a streaming source, or {@link Optional#empty()} if no object exists for
   * the given key. Prefer this over {@link #get(String)} for large objects that may not fit in JVM
   * heap. Use {@link #getMetadataAsync(String)} to retrieve metadata separately if needed.
   */
  CompletionStage<Optional<Source<ByteString, NotUsed>>> getStreamAsync(String key);

  /**
   * Store an object from a streaming source without an explicit content type. Use {@link
   * #getMetadataAsync(String)} to retrieve metadata after the write if needed.
   *
   * @param key object key within the bucket
   * @param data stream of content chunks
   */
  CompletionStage<Done> putStreamAsync(String key, Source<ByteString, ?> data);

  /**
   * Store an object from a streaming source with an explicit content type. Use {@link
   * #getMetadataAsync(String)} to retrieve metadata after the write if needed.
   *
   * @param key object key within the bucket
   * @param data stream of content chunks
   * @param contentType MIME type of the content
   */
  CompletionStage<Done> putStreamAsync(
      String key, Source<ByteString, ?> data, ContentType contentType);

  // ── Async variants ───────────────────────────────────────────────────────

  /**
   * Async variant of {@link #get(String)}. Returns a {@link CompletionStage} that completes with
   * the object, or an empty {@link Optional} if no object exists for the given key.
   */
  CompletionStage<Optional<StoreObject>> getAsync(String key);

  /**
   * Async variant of {@link #put(String, ByteString)}. Returns a {@link CompletionStage} that
   * completes when the object has been stored.
   */
  CompletionStage<Done> putAsync(String key, ByteString data);

  /**
   * Async variant of {@link #put(String, ByteString, ContentType)}. Returns a {@link
   * CompletionStage} that completes when the object has been stored.
   */
  CompletionStage<Done> putAsync(String key, ByteString data, ContentType contentType);

  /**
   * Async variant of {@link #delete(String)}. Returns a {@link CompletionStage} that completes when
   * the object has been deleted.
   */
  CompletionStage<Done> deleteAsync(String key);

  /**
   * Async variant of {@link #getMetadata(String)}. Returns a {@link CompletionStage} that completes
   * with the metadata, or an empty {@link Optional} if no object exists for the given key.
   */
  CompletionStage<Optional<ObjectMetadata>> getMetadataAsync(String key);

  // ── MessageContent helpers ────────────────────────────────────────────────

  /**
   * Creates an {@link akka.javasdk.agent.MessageContent.ImageUrlMessageContent} referencing this
   * object via the {@code object://} scheme understood by the Akka runtime. The URL has the form
   * {@code object://[bucket]/[key]}. The resulting content can be passed directly to an AI agent
   * message without fetching the bytes locally first.
   *
   * @param key object key within the bucket
   * @return image message content pointing at this object
   */
  LoadableMessageContent asImageContent(String key);

  /**
   * Creates a {@link akka.javasdk.agent.MessageContent.PdfUrlMessageContent} referencing this
   * object via the {@code object://} scheme understood by the Akka runtime. The URL has the form
   * {@code object://[bucket]/[key]}. The resulting content can be passed directly to an AI agent
   * message without fetching the bytes locally first.
   *
   * @param key object key within the bucket
   * @return PDF message content pointing at this object
   */
  LoadableMessageContent asPdfContent(String key);
}

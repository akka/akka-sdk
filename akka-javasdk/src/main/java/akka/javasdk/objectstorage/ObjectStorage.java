/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.objectstorage;

import akka.annotation.DoNotInherit;

/**
 * Entry point for object storage. Provides {@link ObjectStore} instances for named buckets.
 *
 * <p>Inject this service into side-effecting components (endpoints, workflows, agents, consumers,
 * timed actions) and call {@link #forBucket(String)} to get a client for a specific bucket.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface ObjectStorage {

  /**
   * Returns a client for the named bucket. The bucket must be configured in the application
   * settings (or, in dev/test mode, any name is accepted when using the default filesystem or
   * in-memory backend).
   *
   * @param bucketName logical bucket name as declared in configuration
   */
  ObjectStore forBucket(String bucketName);
}

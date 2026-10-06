/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import java.util.Optional;

/**
 * Configuration for a named object-storage bucket used in integration tests / dev mode.
 *
 * <p>Use the static factory methods to create configurations for the different backend types:
 *
 * <ul>
 *   <li>{@link #filesystem(String)} / {@link #filesystem(String, String)} — local filesystem,
 *       always available without any cloud credentials.
 *   <li>{@link #s3(String, String, String, S3Credentials)} — Amazon S3, using the region to find
 *       the endpoint.
 *   <li>{@link #s3(String, String, String, S3Credentials, String)} — an S3-compatible service at a
 *       given endpoint (e.g. MinIO started via Testcontainers).
 *   <li>{@link #gcs(String, String, GcsCredentials)} — Google Cloud Storage.
 * </ul>
 *
 * <p>Pass instances to {@link TestKit.Settings#withObjectStorageBucket(ObjectStorageBucketConfig)}.
 */
public interface ObjectStorageBucketConfig {

  /** The logical bucket name as used in SDK calls. */
  String name();

  // ── Factory methods ─────────────────────────────────────────────────────────

  /**
   * Filesystem-backed bucket. Objects are stored under {@code <dev-base-directory>/<name>}.
   *
   * @param name logical bucket name
   */
  static ObjectStorageBucketConfig filesystem(String name) {
    return new Impl.Filesystem(name, Optional.empty());
  }

  /**
   * Filesystem-backed bucket with an explicit storage directory.
   *
   * @param name logical bucket name
   * @param directory absolute path to the directory where objects are stored
   */
  static ObjectStorageBucketConfig filesystem(String name, String directory) {
    return new Impl.Filesystem(name, Optional.of(directory));
  }

  /**
   * S3-backed bucket on Amazon S3. The endpoint comes from the region.
   *
   * @param name logical bucket name
   * @param bucket actual S3 bucket name in the cloud / local service
   * @param region AWS region, e.g. {@code "us-east-1"}
   * @param credentials credentials to authenticate with S3
   */
  static ObjectStorageBucketConfig s3(
      String name, String bucket, String region, S3Credentials credentials) {
    return new Impl.S3(name, bucket, region, credentials, Optional.empty(), Optional.empty());
  }

  /**
   * S3-backed bucket on an S3-compatible service, e.g. MinIO started via Testcontainers.
   *
   * <p>The bucket is addressed as a path. Subdomain addressing needs wildcard DNS, so an
   * S3-compatible service usually requires path access style. Use {@link #s3(String, String,
   * String, S3Credentials, String, S3AccessStyle)} to choose the access style.
   *
   * @param name logical bucket name
   * @param bucket actual S3 bucket name in the cloud / local service
   * @param region AWS region, e.g. {@code "us-east-1"}
   * @param credentials credentials to authenticate with S3
   * @param endpointUrl address of the service, e.g. {@code "http://localhost:9000"}
   */
  static ObjectStorageBucketConfig s3(
      String name, String bucket, String region, S3Credentials credentials, String endpointUrl) {
    return new Impl.S3(
        name, bucket, region, credentials, Optional.of(endpointUrl), Optional.empty());
  }

  /**
   * S3-backed bucket on an S3-compatible service, choosing how the bucket is addressed.
   *
   * @param name logical bucket name
   * @param bucket actual S3 bucket name in the cloud / local service
   * @param region AWS region, e.g. {@code "us-east-1"}
   * @param credentials credentials to authenticate with S3
   * @param endpointUrl address of the service, e.g. {@code "http://localhost:9000"}
   * @param accessStyle how the bucket is addressed at the endpoint
   */
  static ObjectStorageBucketConfig s3(
      String name,
      String bucket,
      String region,
      S3Credentials credentials,
      String endpointUrl,
      S3AccessStyle accessStyle) {
    return new Impl.S3(
        name, bucket, region, credentials, Optional.of(endpointUrl), Optional.of(accessStyle));
  }

  /**
   * GCS-backed bucket (Google Cloud Storage).
   *
   * @param name logical bucket name
   * @param bucket actual GCS bucket name
   * @param credentials credentials to authenticate with GCS
   */
  static ObjectStorageBucketConfig gcs(String name, String bucket, GcsCredentials credentials) {
    return new Impl.Gcs(name, bucket, credentials);
  }

  // ── S3 access style ─────────────────────────────────────────────────────────

  /** How the bucket is addressed at the endpoint. */
  enum S3AccessStyle {
    /** Address the bucket as a path at the endpoint. */
    PATH,
    /**
     * Address the bucket as a subdomain of the endpoint. Needs wildcard DNS. Substituted for a
     * {@code {bucket}} placeholder in the endpoint url when it has one.
     */
    VIRTUAL_HOST
  }

  // ── S3 credentials ──────────────────────────────────────────────────────────

  /** Credentials for an S3-backed bucket. */
  interface S3Credentials {}

  /**
   * Static AWS credentials (access key + secret). Suitable for local development with MinIO or a
   * real S3 bucket accessed with API keys.
   */
  final class S3StaticCredentials implements S3Credentials {
    public final String accessKeyId;
    public final String secretAccessKey;

    public S3StaticCredentials(String accessKeyId, String secretAccessKey) {
      this.accessKeyId = accessKeyId;
      this.secretAccessKey = secretAccessKey;
    }
  }

  /** AWS named-profile credentials (reads from {@code ~/.aws/credentials}). */
  final class S3ProfileCredentials implements S3Credentials {
    public final String profileName;

    public S3ProfileCredentials(String profileName) {
      this.profileName = profileName;
    }
  }

  /**
   * AWS workload-identity / instance-profile credentials.
   *
   * <p><b>Note:</b> this requires a cloud environment with proper IAM setup and is unlikely to work
   * in a local development environment.
   */
  enum S3WorkloadIdentity implements S3Credentials {
    INSTANCE
  }

  // ── GCS credentials ─────────────────────────────────────────────────────────

  /** Credentials for a GCS-backed bucket. */
  interface GcsCredentials {}

  /**
   * GCS service-account key file credentials. Suitable for local development with a downloaded JSON
   * key file.
   *
   * @param path absolute path to the service-account JSON key file
   */
  final class GcsServiceAccountKeyCredentials implements GcsCredentials {
    public final String path;

    public GcsServiceAccountKeyCredentials(String path) {
      this.path = path;
    }
  }

  /**
   * GCP Application Default Credentials (workload identity / ADC).
   *
   * <p><b>Note:</b> requires a GCP environment or {@code gcloud auth application-default login};
   * may not work in all local setups.
   */
  enum GcsWorkloadIdentity implements GcsCredentials {
    INSTANCE
  }

  // ── Package-private implementations ─────────────────────────────────────────

  /** Not part of the public API. */
  final class Impl {
    private Impl() {}

    static final class Filesystem implements ObjectStorageBucketConfig {
      public final String name;
      public final Optional<String> directory;

      Filesystem(String name, Optional<String> directory) {
        this.name = name;
        this.directory = directory;
      }

      @Override
      public String name() {
        return name;
      }
    }

    static final class S3 implements ObjectStorageBucketConfig {
      public final String name;
      public final String bucket;
      public final String region;
      public final S3Credentials credentials;
      public final Optional<String> endpointUrl;
      public final Optional<S3AccessStyle> accessStyle;

      S3(
          String name,
          String bucket,
          String region,
          S3Credentials credentials,
          Optional<String> endpointUrl,
          Optional<S3AccessStyle> accessStyle) {
        this.name = name;
        this.bucket = bucket;
        this.region = region;
        this.credentials = credentials;
        this.endpointUrl = endpointUrl;
        this.accessStyle = accessStyle;
      }

      @Override
      public String name() {
        return name;
      }
    }

    static final class Gcs implements ObjectStorageBucketConfig {
      public final String name;
      public final String bucket;
      public final GcsCredentials credentials;

      Gcs(String name, String bucket, GcsCredentials credentials) {
        this.name = name;
        this.bucket = bucket;
        this.credentials = credentials;
      }

      @Override
      public String name() {
        return name;
      }
    }
  }
}

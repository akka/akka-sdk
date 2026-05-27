<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Integrations](index.html)
- [Object storage](object-storage.html)

<!-- </nav> -->

# Object storage

Object storage lets you store and retrieve binary objects — images, PDFs, documents, or any other binary data — in named buckets. The SDK provides a built-in `ObjectStorageProvider` that you can inject into any side-effecting component (endpoints, workflows, agents, consumers, timed actions).

Objects stored in buckets are directly accessible to AI agents via `object://` URIs. The SDK resolves these automatically when sending multimodal content to an LLM, with no custom content-loading code required. See [Loading content from object storage](../agents/prompt.html#object-storage-content) for details.

## <a href="about:blank#_getting_an_objectstorage"></a> Getting an ObjectStorage

Inject `ObjectStorageProvider` into your component and call `forBucket` to obtain an `ObjectStorage` for a specific bucket:

[ImageUploadEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ImageUploadEndpoint.java)
```java
private final ObjectStorageProvider objectStorageProvider;

public ImageUploadEndpoint(
  ComponentClient componentClient,
  ObjectStorageProvider objectStorageProvider
) { // (1)
  this.componentClient = componentClient;
  this.objectStorageProvider = objectStorageProvider;
}

@Get("/{key}")
public HttpResponse get(String key) {
  var imageBucket = objectStorageProvider.forBucket("images"); // (2)

  Optional<StorageObject> maybeObject = imageBucket.get(key);
  if (maybeObject.isPresent()) {
    StorageObject object = maybeObject.get();
    return HttpResponses.of(
      StatusCodes.OK,
      object.metadata.contentType.orElse(ContentTypes.APPLICATION_OCTET_STREAM),
      object.data.toArray()
    );
  } else {
    return HttpResponses.notFound();
  }
}
```

| **1** | `ObjectStorageProvider` is injected by the SDK — no manual wiring needed. |
| **2** | `forBucket` returns an `ObjectStorage` scoped to the named bucket. |

## <a href="about:blank#_storing_and_retrieving_objects"></a> Storing and retrieving objects

`ObjectStorage` has a straightforward API designed for small to medium size binary payloads, plus streaming variants for large objects.

### <a href="about:blank#_regular_api"></a> Regular API

These operations all work with the entire object in the heap of the service:

| Method | Description |
| --- | --- |
| `put(key, data)` | Store an object. Optionally include a `ContentType`. |
| `get(key)` | Retrieve an object with its full content, or `Optional.empty()` if not found. |
| `getMetadata(key)` | Retrieve only the metadata without downloading the content. |
| `delete(key)` | Delete an object. Succeeds silently if the key does not exist. |
| `list()` | List all objects in the bucket as an in-memory `List`. Use `list(prefix)` to filter by key prefix. |

### <a href="about:blank#_streaming_api"></a> Streaming API

For large objects that may not fit comfortably in JVM heap, use the streaming variants instead. They will allow working with smaller chunks of the payload at a time, for example for streaming out to a HTTP client:

| Method | Description |
| --- | --- |
| `putStreamAsync(key, source)` | Store an object from an Akka Streams `Source<ByteString, ?>`. Optionally include a `ContentType`. |
| `getStreamAsync(key)` | Retrieve an object as a streaming `Source<ByteString, NotUsed>`, or an empty `Optional` if the key does not exist. |
| `streamList()` | Stream object metadata as a `Source<ObjectMetadata, NotUsed>` — prefer over `list()` for large buckets. Use `streamList(prefix)` to filter by key prefix. |

### <a href="about:blank#_example_upload_list_and_delete"></a> Example: Upload, list, and delete

The following endpoint stores an uploaded image, invokes an AI agent to describe it, and exposes list and delete operations:

[ImageUploadEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ImageUploadEndpoint.java)
```java
@Post("/describe")
public String describeImage(HttpEntity.Strict body) {
  var key = UUID.randomUUID().toString();
  var imageBucket = objectStorageProvider.forBucket("images"); // (2)
  imageBucket.put(key, body.getData(), body.getContentType()); // (1)
  var imageContent = MessageContent.ImageUrlMessageContent.create(imageBucket, key); // (2)

  return componentClient
    .forAgent()
    .inSession("image-" + key)
    .method(ImageDescriptionAgent::describe)
    .invoke(imageContent); // (3)
}
```

| **1** | Store the uploaded bytes under a generated key, preserving the original content type. |
| **2** | Create an `ImageUrlMessageContent` referencing the stored object via an `object://` URI — no download happens yet. |
| **3** | Pass the content reference to the agent; the SDK resolves and sends the image to the model. |
[ImageUploadEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ImageUploadEndpoint.java)
```java
@Get("")
public List<ObjectMetadata> list() {
  var imageBucket = objectStorageProvider.forBucket("images");
  return imageBucket.listObjects();
}

@Delete("/{key}")
public HttpResponse deleteImage(String key) {
  var imageBucket = objectStorageProvider.forBucket("images");
  imageBucket.delete(key);
  return HttpResponses.ok();
}
```

### <a href="about:blank#_object_metadata"></a> Object metadata

`ObjectMetadata` contains:

| Field | Description |
| --- | --- |
| `key` | The object key within the bucket. |
| `size` | Size in bytes. |
| `contentType` | MIME type, if set at write time. |
| `eTag` | Opaque version identifier, if provided by the backend. |
| `lastModified` | Timestamp of the last write. |

## <a href="about:blank#_object_storage_and_ai_agents"></a> Object storage and AI agents

Objects stored in buckets can be passed directly to AI agents as multimodal content. The factory methods `ImageUrlMessageContent.create(bucket, key)` and `PdfUrlMessageContent.create(bucket, key)` produce content references using an `object://` URI scheme. The SDK loads the data from the bucket automatically before sending the request to the model.

See [Loading content from object storage](../agents/prompt.html#object-storage-content) for a full example.

## <a href="about:blank#_development_mode"></a> Development mode

In dev mode (running locally with `mvn compile exec:java`), the SDK uses a local filesystem backend by default. No bucket configuration is required — any bucket name is accepted and objects are stored under a directory inside `target/`.

To test against a real backend in dev mode, or to configure specific bucket names with explicit providers, add entries to `application.conf`:

src/main/resources/application.conf
```hocon
akka.javasdk.dev-mode.object-storage.buckets = [
  # Filesystem backend (explicit directory)
  { name = "images", provider = "filesystem", directory = "target/dev-images" }

  # S3-compatible backend (e.g. MinIO or AWS S3 with static credentials)
  # { name = "documents", provider = "s3", bucket = "my-bucket", region = "us-east-1"
  #   credentials { type = "static", access-key-id = "AKID", secret-access-key = "secret" } }

  # Google Cloud Storage with a service-account key file
  # { name = "assets", provider = "gcs", bucket = "my-gcs-bucket"
  #   credentials { type = "service-account-key", path = "/secrets/sa-key.json" } }
]
```
S3 and GCS buckets without any explicit authentication configured will both rely on their respective default authentication options, see [S3](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html#credentials-default), [GCS](https://doc.akka.io/libraries/alpakka/current/google-common.html#configuration) for more details.

When the `buckets` list is non-empty, only the named buckets are accessible — matching production behavior. When it is empty (the default), all names are accepted.

## <a href="about:blank#_testing"></a> Testing

Integration tests that use `TestKitSupport` automatically get an in-memory object storage backend. No external infrastructure or configuration is needed. Any bucket name is accepted and objects are stored in memory for the duration of the test.

|  | Each test class starts its own runtime instance, so the in-memory store is isolated between test classes. Test methods within the same class share the same store. |

## <a href="about:blank#_see_also"></a> See also

- [Multimodal agents with object storage](../agents/prompt.html#object-storage-content)
- [Data & knowledge integrations](data-and-knowledge.html)

<!-- <footer> -->
<!-- <nav> -->
[Data & knowledge](data-and-knowledge.html) [Messaging & events](messaging-and-events.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
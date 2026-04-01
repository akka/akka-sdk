package com.example.api;

import akka.http.javadsl.model.*;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.objectstorage.ObjectMetadata;
import akka.javasdk.objectstorage.ObjectStore;
import akka.javasdk.objectstorage.ObjectStoreProvider;
import akka.javasdk.objectstorage.StoreObject;
import com.example.application.ImageDescriptionAgent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/image")
public class ImageUploadEndpoint {

  private final ComponentClient componentClient;

  // tag::inject-bucket[]
  private final ObjectStoreProvider objectStoreProvider;

  public ImageUploadEndpoint(
    ComponentClient componentClient,
    ObjectStoreProvider objectStoreProvider
  ) { // <1>
    this.componentClient = componentClient;
    this.objectStoreProvider = objectStoreProvider;
  }

  // end::inject-bucket[]

  // tag::put-describe[]
  @Post("/describe")
  public String describeImage(HttpEntity.Strict body) {
    var key = UUID.randomUUID().toString();
    var imageBucket = objectStoreProvider.forBucket("images"); // <2>
    imageBucket.put(key, body.getData(), body.getContentType()); // <1>
    var imageContent = MessageContent.ImageUrlMessageContent.create(imageBucket, key); // <2>

    return componentClient
      .forAgent()
      .inSession("image-" + key)
      .method(ImageDescriptionAgent::describe)
      .invoke(imageContent); // <3>
  }

  // end::put-describe[]

  // tag::inject-bucket[]
  @Get("/{key}")
  public HttpResponse get(String key) {
    var imageBucket = objectStoreProvider.forBucket("images"); // <2>

    Optional<StoreObject> maybeObject = imageBucket.get(key);
    if (maybeObject.isPresent()) {
      StoreObject object = maybeObject.get();
      return HttpResponses.of(
        StatusCodes.OK,
        object.metadata.contentType.orElse(ContentTypes.APPLICATION_OCTET_STREAM),
        object.data.toArray()
      );
    } else {
      return HttpResponses.notFound();
    }
  }

  // end::inject-bucket[]

  // tag::list-delete[]
  @Get("")
  public List<ObjectMetadata> list() {
    var imageBucket = objectStoreProvider.forBucket("images");
    return imageBucket.list();
  }

  @Delete("/{key}")
  public HttpResponse deleteImage(String key) {
    var imageBucket = objectStoreProvider.forBucket("images");
    imageBucket.delete(key);
    return HttpResponses.ok();
  }
  // end::list-delete[]
}

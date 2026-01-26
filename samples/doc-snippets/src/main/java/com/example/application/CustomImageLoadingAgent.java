package com.example.application;

import akka.http.javadsl.model.headers.HttpCredentials;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ImageLoader;
import akka.javasdk.agent.MessageContent.ImageMessageContent;
import akka.javasdk.agent.MessageContent.TextMessageContent;
import akka.javasdk.agent.UserMessage;
import akka.javasdk.annotations.Component;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.StrictResponse;
import akka.util.ByteString;
import java.net.URI;
import java.util.Optional;

// tag::custom-image-loader[]
@Component(id = "custom-image-loading-agent")
public class CustomImageLoadingAgent extends Agent {

  private final HttpClient httpClient;

  public CustomImageLoadingAgent(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public class MyImageLoader implements ImageLoader { // <1>

    private final String userToken;

    public MyImageLoader(String userToken) {
      this.userToken = userToken;
    }

    @Override
    public LoadedImage load(
      URI uri,
      ImageMessageContent.DetailLevel detailLevel,
      Optional<String> mimeType
    ) {
      StrictResponse<ByteString> response = httpClient // <2>
        .GET(uri.toString())
        .addCredentials(HttpCredentials.createOAuth2BearerToken(userToken))
        .invoke();

      byte[] imageData = response.body().toArray();
      String actualMimeType = response
        .httpResponse()
        .entity()
        .getContentType()
        .mediaType()
        .toString(); // <3>

      return new LoadedImage(imageData, actualMimeType); // <4>
    }
  }

  // end::custom-image-loader[]

  // tag::using-custom-loader[]
  public Effect<String> analyzeImage(String imageUri, String userToken) {
    return effects()
      .systemMessage("You are an image analysis assistant.")
      .imageLoader(new MyImageLoader(userToken)) // <1>
      .userMessage(
        UserMessage.from(
          TextMessageContent.from("Describe this image in detail"),
          ImageMessageContent.fromUrl(imageUri)
        )
      ) // <2>
      .thenReply();
  }
  // end::using-custom-loader[]
}

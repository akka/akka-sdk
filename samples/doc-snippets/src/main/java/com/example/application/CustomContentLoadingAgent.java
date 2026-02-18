package com.example.application;

import akka.http.javadsl.model.headers.HttpCredentials;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ContentLoader;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.MessageContent.ImageMessageContent;
import akka.javasdk.agent.MessageContent.PdfMessageContent;
import akka.javasdk.agent.MessageContent.TextMessageContent;
import akka.javasdk.agent.UserMessage;
import akka.javasdk.annotations.Component;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.StrictResponse;
import akka.util.ByteString;
import java.util.Optional;

// tag::custom-content-loader[]
@Component(id = "custom-content-loading-agent")
public class CustomContentLoadingAgent extends Agent {

  private final HttpClient httpClient;

  public CustomContentLoadingAgent(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public class MyContentLoader implements ContentLoader { // <1>

    private final String userToken;

    public MyContentLoader(String userToken) {
      this.userToken = userToken;
    }

    @Override
    public LoadedContent load(MessageContent.LoadableMessageContent content) {
      return switch (content) {
        case MessageContent.ImageUrlMessageContent image -> {
          StrictResponse<ByteString> response = httpClient // <2>
            .GET(image.url().toString())
            .addCredentials(HttpCredentials.createOAuth2BearerToken(userToken))
            .invoke();

          byte[] data = response.body().toArray();
          String actualMimeType = response
            .httpResponse()
            .entity()
            .getContentType()
            .mediaType()
            .toString(); // <3>

          yield new LoadedContent(data, Optional.of(actualMimeType)); // <4>
        }
        case MessageContent.PdfUrlMessageContent pdf -> throw new RuntimeException(
          "Not implemented"
        );
      };
    }
  }

  // end::custom-content-loader[]

  // tag::using-custom-loader[]
  public record AnalyzeRequest(String imageUri, String pdfUri, String userToken) {}

  public Effect<String> analyzeImage(AnalyzeRequest request) {
    return effects()
      .systemMessage("You are a document analysis assistant.")
      .contentLoader(new MyContentLoader(request.userToken())) // <1>
      .userMessage(
        UserMessage.from(
          TextMessageContent.from("Describe this image and summarize the PDF"),
          ImageMessageContent.fromUrl(request.imageUri), // <2>
          PdfMessageContent.fromUrl(request.pdfUri) // <3>
        )
      )
      .thenReply();
  }
  // end::using-custom-loader[]
}

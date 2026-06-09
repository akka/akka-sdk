/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import java.nio.charset.StandardCharsets;

@Component(id = "some-agent-with-image-tool")
public class SomeAgentWithImageTool extends Agent {

  public static final byte[] IMAGE_BYTES = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
  public static final String IMAGE_MIME_TYPE = "image/png";

  public static final byte[] PDF_BYTES = "fake-pdf-bytes".getBytes(StandardCharsets.UTF_8);

  public static class PhotoService {
    @FunctionTool(description = "Fetches a photo for the given subject")
    public MessageContent getPhoto(String subject) {
      // A real tool would fetch/produce the bytes (e.g. a Street View image). Returning inline
      // bytes lets the framework hand them to the model without the app pre-uploading anything.
      return MessageContent.ImageMessageContent.fromBytes(IMAGE_BYTES, IMAGE_MIME_TYPE);
    }
  }

  public static class DocumentService {
    @FunctionTool(description = "Fetches a PDF document for the given subject")
    public MessageContent getDocument(String subject) {
      return MessageContent.PdfMessageContent.fromBytes(PDF_BYTES);
    }
  }

  public record SomeResponse(String response) {}

  public Effect<SomeResponse> query(String question) {
    return effects()
        .systemMessage("You are a helpful assistant that can fetch photos and documents.")
        .tools(new PhotoService(), new DocumentService())
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }
}

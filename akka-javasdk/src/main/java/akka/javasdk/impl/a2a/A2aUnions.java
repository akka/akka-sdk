/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.A2aStreamResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskArtifactUpdate;
import akka.javasdk.a2a.A2aTaskStatusUpdate;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * INTERNAL API
 *
 * <p>Readers of the protocol's unions: JSON objects with exactly one content member. Members that
 * no variant knows are ignored, as everywhere else in the protocol types.
 */
@InternalApi
public final class A2aUnions {
  private A2aUnions() {}

  // The one member of the object that is set and known, or a mapping error
  private static String member(
      JsonParser p, DeserializationContext ctxt, JsonNode node, Class<?> type, List<String> names)
      throws IOException {
    if (node == null || !node.isObject()) {
      return ctxt.reportInputMismatch(type, "%s must be a JSON object", type.getSimpleName());
    }
    var present = new ArrayList<String>();
    for (String name : names) {
      var value = node.get(name);
      if (value != null && !value.isNull()) present.add(name);
    }
    if (present.size() != 1) {
      return ctxt.reportInputMismatch(
          type,
          "%s must have exactly one of %s set, but has %s",
          type.getSimpleName(),
          names,
          present);
    }
    return present.getFirst();
  }

  private static <T> T read(JsonParser p, JsonNode node, Class<T> type) throws IOException {
    return p.getCodec().treeToValue(node, type);
  }

  /** Reads an {@link A2aPart} from its flat form. */
  public static final class PartDeserializer extends StdDeserializer<A2aPart> {
    private static final List<String> MEMBERS = List.of("text", "raw", "url", "data");

    public PartDeserializer() {
      super(A2aPart.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public A2aPart deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      JsonNode node = p.readValueAsTree();
      var member = member(p, ctxt, node, A2aPart.class, MEMBERS);
      var mediaType = text(node, "mediaType");
      var filename = text(node, "filename");
      var metadataNode = node.get("metadata");
      Map<String, Object> metadata =
          metadataNode == null || metadataNode.isNull() ? null : read(p, metadataNode, Map.class);
      var content = node.get(member);
      try {
        return switch (member) {
          case "text" -> new A2aPart.Text(content.asText(), mediaType, filename, metadata);
          case "raw" -> new A2aPart.Raw(content.asText(), mediaType, filename, metadata);
          case "url" -> new A2aPart.Url(content.asText(), mediaType, filename, metadata);
          default ->
              new A2aPart.Data(read(p, content, Object.class), mediaType, filename, metadata);
        };
      } catch (IllegalArgumentException e) {
        return ctxt.reportInputMismatch(A2aPart.class, "%s", e.getMessage());
      }
    }

    private static String text(JsonNode node, String name) {
      var value = node.get(name);
      return value == null || value.isNull() ? null : value.asText();
    }
  }

  /** Reads an {@link A2aSendMessageResponse} from its one member. */
  public static final class SendMessageResponseDeserializer
      extends StdDeserializer<A2aSendMessageResponse> {
    private static final List<String> MEMBERS = List.of("task", "message");

    public SendMessageResponseDeserializer() {
      super(A2aSendMessageResponse.class);
    }

    @Override
    public A2aSendMessageResponse deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = p.readValueAsTree();
      var member = member(p, ctxt, node, A2aSendMessageResponse.class, MEMBERS);
      var content = node.get(member);
      return member.equals("task")
          ? new A2aSendMessageResponse.Task(read(p, content, A2aTask.class))
          : new A2aSendMessageResponse.Message(read(p, content, A2aMessage.class));
    }
  }

  /** Reads an {@link A2aStreamResponse} from its one member. */
  public static final class StreamResponseDeserializer extends StdDeserializer<A2aStreamResponse> {
    private static final List<String> MEMBERS =
        List.of("task", "message", "statusUpdate", "artifactUpdate");

    public StreamResponseDeserializer() {
      super(A2aStreamResponse.class);
    }

    @Override
    public A2aStreamResponse deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = p.readValueAsTree();
      var member = member(p, ctxt, node, A2aStreamResponse.class, MEMBERS);
      var content = node.get(member);
      return switch (member) {
        case "task" -> new A2aStreamResponse.Task(read(p, content, A2aTask.class));
        case "message" -> new A2aStreamResponse.Message(read(p, content, A2aMessage.class));
        case "statusUpdate" ->
            new A2aStreamResponse.StatusUpdate(read(p, content, A2aTaskStatusUpdate.class));
        default ->
            new A2aStreamResponse.ArtifactUpdate(read(p, content, A2aTaskArtifactUpdate.class));
      };
    }
  }
}

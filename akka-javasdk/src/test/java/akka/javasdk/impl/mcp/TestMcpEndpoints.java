/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.mcp;

import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpPrompt;
import akka.javasdk.annotations.mcp.McpResource;
import akka.javasdk.annotations.mcp.McpTool;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.protobuf.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TestMcpEndpoints {

  // Base class declaring tool/prompt/resource methods that a concrete @McpEndpoint subclass
  // inherits.
  public abstract static class BaseMcpEndpoint {

    @McpTool(name = "inherited-tool", description = "inherited tool")
    public String inheritedTool(String someParam) {
      return someParam;
    }

    @McpPrompt(name = "inherited-prompt", description = "inherited prompt")
    public String inheritedPrompt() {
      return "inherited prompt";
    }

    @McpResource(
        uri = "file:///inherited",
        name = "inherited-resource",
        description = "inherited resource")
    public String inheritedResource() {
      return "inherited resource";
    }

    @McpTool(name = "overridden-tool", description = "base")
    public String overriddenTool() {
      return "base";
    }

    @McpTool(name = "dropped-tool", description = "base")
    public String droppedTool() {
      return "base";
    }
  }

  @McpEndpoint(serverName = "inheriting-server", serverVersion = "1.0")
  public static class InheritingMcpEndpoint extends BaseMcpEndpoint {

    @McpTool(name = "own-tool", description = "own tool")
    public String ownTool() {
      return "own";
    }

    // Re-declares @McpTool — should keep the inherited tool with the override implementation.
    @Override
    @McpTool(name = "overridden-tool", description = "override")
    public String overriddenTool() {
      return "override";
    }

    // No annotation on the override — the tool from the base class is opted out.
    @Override
    public String droppedTool() {
      return "override";
    }
  }

  @McpEndpoint(serverName = "structured")
  public static class StructuredOutputMcpEndpoint {

    public record Weather(String city, int tempC) {}

    public record Page<T>(List<T> items, T first) {}

    public enum Unit {
      CELSIUS,
      FAHRENHEIT
    }

    @McpTool(name = "null-weather", description = "returns null instead of a structured result")
    public Weather nullWeather() {
      return null;
    }

    @McpTool(name = "null-text", description = "returns null instead of text")
    public String nullText() {
      return null;
    }

    @McpTool(name = "null-async", description = "returns null instead of a CompletionStage")
    public CompletionStage<Weather> nullAsync() {
      return null;
    }

    @McpTool(name = "weather-page", description = "returns a generic record")
    public Page<Weather> weatherPage() {
      return new Page<>(List.of(new Weather("Leuven", 20)), new Weather("Leuven", 20));
    }

    @McpTool(name = "weather-list", description = "returns a list")
    public List<Weather> weatherList() {
      return List.of(new Weather("Leuven", 20), new Weather("Uppsala", 10));
    }

    @McpTool(name = "weather-array", description = "returns an array")
    public Weather[] weatherArray() {
      return new Weather[] {new Weather("Leuven", 20)};
    }

    @McpTool(name = "async-weather", description = "returns a CompletionStage")
    public CompletionStage<Weather> asyncWeather() {
      return CompletableFuture.completedFuture(new Weather("Leuven", 20));
    }

    @McpTool(name = "void-tool", description = "returns nothing")
    public void voidTool() {}

    @McpTool(name = "async-void-tool", description = "returns nothing, async")
    public CompletionStage<Void> asyncVoidTool() {
      return CompletableFuture.completedFuture(null);
    }

    @McpTool(name = "optional-weather", description = "returns an optional")
    public Optional<Weather> optionalWeather() {
      return Optional.of(new Weather("Leuven", 20));
    }

    @McpTool(name = "unit", description = "returns an enum")
    public Unit unit() {
      return Unit.CELSIUS;
    }

    @McpTool(name = "map", description = "returns a map")
    public Map<String, Integer> map() {
      return Map.of("Leuven", 20);
    }

    @McpTool(name = "async-fail", description = "fails asynchronously")
    public CompletionStage<Weather> asyncFail() {
      return CompletableFuture.supplyAsync(
          () -> {
            throw new IllegalStateException("failed: async");
          });
    }

    public record Note(String title, Optional<String> extra) {}

    @McpTool(name = "note-without-extra", description = "returns a record with an empty optional")
    public Note noteWithoutExtra() {
      return new Note("t", Optional.empty());
    }

    @McpTool(
        name = "note-with-null-title",
        description = "returns a record with a null required field")
    public Note noteWithNullTitle() {
      return new Note(null, Optional.of("x"));
    }

    public record Notes(List<Note> notes) {}

    @McpTool(name = "notes-with-null-title", description = "returns nested null values")
    public Notes notesWithNullTitle() {
      return new Notes(Arrays.asList(new Note(null, Optional.empty()), null));
    }

    @McpTool(name = "note-list-with-null-title", description = "returns a list with a null field")
    public List<Note> noteListWithNullTitle() {
      return List.of(new Note(null, Optional.empty()));
    }

    public record AlwaysIncluded(@JsonInclude(JsonInclude.Include.ALWAYS) String title) {}

    @McpTool(
        name = "always-included-null-title",
        description = "returns a null field Jackson writes")
    public AlwaysIncluded alwaysIncludedNullTitle() {
      return new AlwaysIncluded(null);
    }
  }

  @McpEndpoint(serverName = "protobuf-list")
  public static class ProtobufListMcpEndpoint {
    @McpTool(description = "list of protobuf messages")
    public List<Timestamp> timestamps() {
      return List.of(Timestamp.getDefaultInstance());
    }
  }

  @McpEndpoint(serverName = "protobuf-field")
  public static class ProtobufFieldMcpEndpoint {
    public record WithTimestamp(Timestamp timestamp) {}

    @McpTool(description = "record with a protobuf field")
    public WithTimestamp withTimestamp() {
      return new WithTimestamp(Timestamp.getDefaultInstance());
    }
  }

  @McpEndpoint(serverName = "raw-collection")
  public static class RawCollectionMcpEndpoint {
    public static class Weathers extends ArrayList<String> {}

    @McpTool(description = "collection subclass without type parameters")
    public Weathers weathers() {
      return new Weathers();
    }
  }

  @McpEndpoint(serverName = "raw-completion-stage")
  public static class RawCompletionStageMcpEndpoint {
    @SuppressWarnings("rawtypes")
    @McpTool(description = "raw completion stage")
    public CompletionStage rawStage() {
      return CompletableFuture.completedFuture("text");
    }
  }

  @McpEndpoint(serverName = "protobuf")
  public static class ProtobufMcpEndpoint {
    @McpTool(description = "protobuf message")
    public Timestamp timestamp() {
      return Timestamp.getDefaultInstance();
    }
  }
}

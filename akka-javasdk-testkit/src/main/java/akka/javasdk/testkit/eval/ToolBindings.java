/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The one place the service's structure is named: which injected stub serves which tool.
 *
 * <p>A recorded interaction says a tool named {@code getLoan} returned something; it cannot know
 * that in this test {@code getLoan} is served by the loans stub. This map carries that knowledge,
 * once per service, a handful of lines:
 *
 * <pre>{@code
 * var bindings = ToolBindings.builder()
 *     .bind("getCustomer", call ->
 *         crm.prime((String) call.argument("customerId"), call.resultAs(Customer.class)))
 *     .build();
 * }</pre>
 *
 * <p>Loading a capture that names a tool with no binding fails at load time, before any model call
 * is paid for.
 */
public final class ToolBindings {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Map<String, ResultLoader> byTool;

  private ToolBindings(Map<String, ResultLoader> byTool) {
    this.byTool = Map.copyOf(byTool);
  }

  /**
   * One recorded tool invocation, as a loader receives it: the arguments the tool was called with
   * and the result it returned, still as the JSON the capture recorded.
   */
  public record RecordedCall(String tool, Map<String, Object> arguments, String resultJson) {

    public RecordedCall {
      if (tool == null || tool.isBlank()) throw new IllegalArgumentException("tool required");
      arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
      resultJson = resultJson == null ? "null" : resultJson;
    }

    /** The recorded value of one argument, or null if the call did not carry it. */
    public Object argument(String name) {
      return arguments.get(name);
    }

    /** The recorded result, read into the stub's return type. */
    public <T> T resultAs(Class<T> type) {
      try {
        return MAPPER.readValue(resultJson, type);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "recorded result of "
                + tool
                + " does not read as "
                + type.getSimpleName()
                + ": "
                + resultJson,
            e);
      }
    }
  }

  /** Loads the recorded result of one call into its stub, so replaying the call finds it. */
  @FunctionalInterface
  public interface ResultLoader {
    void load(RecordedCall call);
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean binds(String tool) {
    return byTool.containsKey(tool);
  }

  public Set<String> toolNames() {
    return byTool.keySet();
  }

  /** The loader for a tool, or a refusal naming the tool and what is bound. */
  public ResultLoader loaderFor(String tool) {
    var loader = byTool.get(tool);
    if (loader == null) {
      throw new IllegalArgumentException("no binding for tool " + tool + "; bound: " + toolNames());
    }
    return loader;
  }

  public static final class Builder {

    private final Map<String, ResultLoader> byTool = new LinkedHashMap<>();

    private Builder() {}

    public Builder bind(String toolName, ResultLoader loader) {
      if (toolName == null || toolName.isBlank())
        throw new IllegalArgumentException("tool name required");
      if (loader == null) throw new IllegalArgumentException("loader required");
      if (byTool.put(toolName, loader) != null) {
        throw new IllegalArgumentException("tool " + toolName + " bound twice");
      }
      return this;
    }

    public ToolBindings build() {
      return new ToolBindings(byTool);
    }
  }
}

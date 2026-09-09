/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which injected stub serves which tool, for cases derived from recordings.
 *
 * <p>A recorded case carries each tool call production made, with its result. The binding loads
 * that result into the stub that serves the tool in the test:
 *
 * <pre>{@code
 * var bindings = ToolBindings.builder()
 *     .bind("getCustomer", call -> crm.add(call.resultAs(Customer.class)))
 *     .build();
 *
 * runner.cases(replayed).bindings(bindings).agent(SupportAgent::ask).run();
 * }</pre>
 *
 * <p>{@link ExperimentRunner} refuses to run a case that names a tool without a binding.
 */
public final class ToolBindings {

  private static final ToolBindings NONE = new ToolBindings(Map.of());

  private final Map<String, ResultLoader> byTool;

  private ToolBindings(Map<String, ResultLoader> byTool) {
    this.byTool = Map.copyOf(byTool);
  }

  /** Loads the recorded result of one call into its stub. */
  @FunctionalInterface
  public interface ResultLoader {
    void load(RecordedCall call);
  }

  /** Starts a set of bindings. */
  public static Builder builder() {
    return new Builder();
  }

  /** No bindings. */
  public static ToolBindings none() {
    return NONE;
  }

  /** Whether the tool has a binding. */
  public boolean binds(String tool) {
    return byTool.containsKey(tool);
  }

  /** The bound tool names. */
  public Set<String> toolNames() {
    return byTool.keySet();
  }

  /**
   * The loader for a tool.
   *
   * @throws IllegalArgumentException when the tool has no binding
   */
  public ResultLoader loaderFor(String tool) {
    var loader = byTool.get(tool);
    if (loader == null) {
      throw new IllegalArgumentException("no binding for tool " + tool + "; bound: " + toolNames());
    }
    return loader;
  }

  /** Loads each recorded call into the stub bound to its tool, in recorded order. */
  public void load(Iterable<RecordedCall> calls) {
    for (var call : calls) {
      loaderFor(call.tool()).load(call);
    }
  }

  public static final class Builder {

    private final Map<String, ResultLoader> byTool = new LinkedHashMap<>();

    private Builder() {}

    /**
     * Binds a tool to the loader that stores its recorded result in the stub.
     *
     * @throws IllegalArgumentException when the tool is already bound
     */
    public Builder bind(String toolName, ResultLoader loader) {
      if (toolName == null || toolName.isBlank())
        throw new IllegalArgumentException("tool name required");
      if (loader == null) throw new IllegalArgumentException("loader required");
      if (byTool.put(toolName, loader) != null) {
        throw new IllegalArgumentException("tool " + toolName + " bound twice");
      }
      return this;
    }

    /** The bindings declared so far. */
    public ToolBindings build() {
      return new ToolBindings(byTool);
    }
  }
}

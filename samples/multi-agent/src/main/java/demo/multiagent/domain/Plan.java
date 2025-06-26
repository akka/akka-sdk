
package demo.multiagent.domain;

// tag::all[]
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a plan consisting of multiple steps to be executed by different agents.
 */
public record Plan(List<PlanStep> steps) {

  /**
   * Creates an empty plan with no steps.
   */
  public Plan() {
    this(new ArrayList<>());
  }
}
// end::all[]

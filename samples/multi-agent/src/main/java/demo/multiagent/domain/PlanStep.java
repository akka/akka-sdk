package demo.multiagent.domain;

// tag::all[]
/**
 * Represents a single step within a Plan.
 * Each step is assigned to a specific agent and contains a command description.
 */
public record PlanStep(String agentId, String query) {
}
// end::all[]

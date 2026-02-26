/**
 * Research Brief — delegative coordination with parallel subtasks.
 *
 * <p>A coordinator delegates research and analysis to specialist agents, which run as isolated
 * subtasks. Results flow back to the coordinator for synthesis. Demonstrates the delegative
 * (fan-out/fan-in) coordination pattern.
 *
 * <p><b>Capabilities demonstrated:</b> Delegation (parallel subtasks with isolated contexts).
 *
 * <p><b>Agents:</b> BriefCoordinator (delegates) → Researcher, Analyst (delegation targets).
 *
 * <p><b>See:</b> {@code http/research.http} for example requests.
 */
package demo.research;

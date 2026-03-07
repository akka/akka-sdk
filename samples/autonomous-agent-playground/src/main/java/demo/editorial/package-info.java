/**
 * Editorial Workflow — team coordination + policy-driven approval.
 *
 * <p>A chief editor forms a team of writers, assigns article sections via a shared task list, and
 * monitors progress. Once the team's work is compiled, the {@link
 * demo.editorial.application.PublicationApprovalPolicy} requires editorial approval before the task
 * completes. Demonstrates composing team coordination with deterministic task policies.
 *
 * <p><b>Capabilities demonstrated:</b> Team formation, shared task list, task policies (completion
 * gate for editorial approval), team + policy composition.
 *
 * <p><b>Agents:</b> ChiefEditor (forms team, compiles output) → Writer (team member, writes
 * sections).
 *
 * <p><b>See:</b> {@code http/editorial.http} for example requests.
 */
package demo.editorial;

/**
 * Editorial Workflow — team coordination + decision points.
 *
 * <p>A chief editor forms a team of writers, assigns article sections via a shared task list, and
 * monitors progress. Once the team's work is compiled, the editor requests human approval before
 * publishing. Demonstrates composing team coordination with human-in-the-loop decision points.
 *
 * <p><b>Capabilities demonstrated:</b> Team formation, shared task list, decision points
 * (requestDecision for publish approval), team + decision point composition.
 *
 * <p><b>Agents:</b> ChiefEditor (forms team, requests approval) → Writer (team member, writes
 * sections).
 *
 * <p><b>See:</b> {@code http/editorial.http} for example requests.
 */
package demo.editorial;

/**
 * Consulting Engagement — delegation + handoff composition.
 *
 * <p>A consulting coordinator delegates focused research subtasks to specialist researchers
 * (parallel delegation) and escalates complex problems to a senior consultant (handoff).
 * Demonstrates composing delegation and handoff on the same agent — the coordinator chooses at
 * runtime whether to fan out research or escalate.
 *
 * <p><b>Capabilities demonstrated:</b> Delegation (parallel subtasks), handoff (escalation),
 * capability composition on a single agent.
 *
 * <p><b>Agents:</b> ConsultingCoordinator (delegates + escalates) → Researcher (delegation target),
 * SeniorConsultant (handoff target).
 *
 * <p><b>See:</b> {@code http/consulting.http} for example requests.
 */
package demo.consulting;

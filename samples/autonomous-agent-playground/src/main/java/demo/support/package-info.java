/**
 * Customer Support — sequential handoff between specialist agents.
 *
 * <p>A triage agent classifies incoming support requests and hands them off to the appropriate
 * specialist (billing or technical). Demonstrates the sequential/handoff coordination pattern where
 * control transfers between agents — the triage agent is done once it hands off.
 *
 * <p><b>Capabilities demonstrated:</b> Handoff (sequential coordination), domain tools.
 *
 * <p><b>Agents:</b> TriageAgent (coordinator) → BillingSpecialist or TechnicalSpecialist (handoff
 * targets).
 *
 * <p><b>See:</b> {@code http/support.http} for example requests.
 */
package demo.support;

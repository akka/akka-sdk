/**
 * Compliance Review — handoff + decision points composition.
 *
 * <p>A triage agent classifies incoming compliance requests by risk level. Low-risk items are
 * handled directly; high-risk items are handed off to a specialist risk assessor. The risk assessor
 * performs a detailed assessment and requests human sign-off from a compliance officer before
 * completing. Demonstrates composing sequential handoff with human-in-the-loop decision points.
 *
 * <p><b>Capabilities demonstrated:</b> Handoff (risk-based routing), decision points
 * (requestDecision for officer approval), handoff + decision point composition.
 *
 * <p><b>Agents:</b> ComplianceTriageAgent (classifies, hands off) → RiskAssessor (deep analysis,
 * requests approval).
 *
 * <p><b>See:</b> {@code http/compliance.http} for example requests.
 */
package demo.compliance;

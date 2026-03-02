/**
 * Compliance Review — handoff + policy-driven approval.
 *
 * <p>A triage agent classifies incoming compliance requests by risk level. Low-risk items are
 * handled directly; high-risk items are handed off to a specialist risk assessor. The {@link
 * demo.compliance.application.ComplianceApprovalPolicy} requires officer sign-off for high-risk
 * reports. Demonstrates composing sequential handoff with deterministic task policies.
 *
 * <p><b>Capabilities demonstrated:</b> Handoff (risk-based routing), task policies (conditional
 * approval based on risk level), handoff + policy composition.
 *
 * <p><b>Agents:</b> ComplianceTriageAgent (classifies, hands off) → RiskAssessor (deep analysis,
 * policy-gated completion).
 *
 * <p><b>See:</b> {@code http/compliance.http} for example requests.
 */
package demo.compliance;

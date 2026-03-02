/**
 * Content Publishing — policy-driven editorial approval.
 *
 * <p>An agent writes an article and completes the task. The {@link
 * demo.publishing.application.ArticleApprovalPolicy} intercepts completion and requires human
 * approval before the task finalises. Demonstrates task policies for deterministic lifecycle
 * guardrails.
 *
 * <p><b>Capabilities demonstrated:</b> Task policies (completion gate), approve/reject flow.
 *
 * <p><b>Agents:</b> ContentAgent (single agent with policy-driven approval).
 *
 * <p><b>See:</b> {@code http/publishing.http} for example requests.
 */
package demo.publishing;

/**
 * Content Publishing — human-in-the-loop decision points.
 *
 * <p>An agent writes an article and requests human approval before publishing. If rejected, it
 * revises based on feedback. Demonstrates decision points (requestDecision) for human-in-the-loop
 * workflows.
 *
 * <p><b>Capabilities demonstrated:</b> Decision points (task suspends awaiting external input),
 * approve/reject flow.
 *
 * <p><b>Agents:</b> ContentAgent (single agent with decision points).
 *
 * <p><b>See:</b> {@code http/publishing.http} for example requests.
 */
package demo.publishing;

/**
 * Report Pipeline — task dependencies with explicit ordering.
 *
 * <p>Three tasks are created with a dependency chain (collect → analyse → report). A single agent
 * is assigned all three. Tasks with unmet dependencies are automatically re-queued until their
 * dependencies complete.
 *
 * <p><b>Capabilities demonstrated:</b> Task dependencies, explicit multi-task creation, dependency
 * checking and re-queuing.
 *
 * <p><b>Agents:</b> ReportAgent (processes all pipeline phases).
 *
 * <p><b>See:</b> {@code http/pipeline.http} for example requests.
 */
package demo.pipeline;

/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * Policy interface for enforcing business rules at task lifecycle boundaries. Policies are
 * evaluated deterministically by the framework — not by the LLM — before assignment and completion
 * transitions.
 *
 * <p>Attach policies to task definitions:
 *
 * <pre>{@code
 * public static final Task<PurchaseOrder> PROCESS_ORDER =
 *     Task.of("Process a purchase order", PurchaseOrder.class)
 *         .policy(PurchaseLimitPolicy.class);
 *
 * public class PurchaseLimitPolicy implements TaskPolicy<PurchaseOrder> {
 *   @Override
 *   public PolicyResult onCompletion(TaskCompletionContext<PurchaseOrder> context) {
 *     if (context.result().amount() > 10_000) {
 *       return PolicyResult.requireApproval("Orders over $10,000 require manager approval");
 *     }
 *     return PolicyResult.allow();
 *   }
 * }
 * }</pre>
 *
 * <p>Policy classes are instantiated by the framework using reflection. If the policy class has a
 * constructor accepting {@link akka.javasdk.client.ComponentClient}, it will be used (allowing
 * policies to query other components). Otherwise, a no-arg constructor is used.
 *
 * @param <R> The result type of the task this policy applies to.
 */
public interface TaskPolicy<R> {

  /**
   * Evaluate whether a task assignment should proceed.
   *
   * @return {@link PolicyResult#allow()} to proceed, {@link PolicyResult#deny(String)} to block
   */
  default PolicyResult onAssignment(TaskAssignmentContext context) {
    return PolicyResult.allow();
  }

  /**
   * Evaluate whether a task completion should proceed.
   *
   * @return {@link PolicyResult#allow()} to proceed, {@link PolicyResult#deny(String)} to reject
   *     the result (agent retries), or {@link PolicyResult#requireApproval(String)} to pause for
   *     external approval
   */
  default PolicyResult onCompletion(TaskCompletionContext<R> context) {
    return PolicyResult.allow();
  }
}

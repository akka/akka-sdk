/**
 * Evaluation of an agent inside a {@code TestKitSupport} test. Cases run against the running
 * service and are judged by the {@link akka.javasdk.evaluation.Evaluator} components bound to the
 * agent.
 *
 * <p>An {@link akka.javasdk.testkit.eval.EvalCase} is the command sent to the agent, as the user
 * message or as the command handler's own type. The evaluators and their bindings come from the
 * configuration the TestKit runs with, under {@code akka.javasdk.evaluation.evaluators}: a test
 * binds its own evaluators, kept in the test sources, and disables production ones with {@code
 * TestKit.Settings.withAdditionalConfig}. The test prepares the mocked tools before the run.
 *
 * <p>{@link akka.javasdk.testkit.eval.ExperimentRunner} calls the agent in a fresh session per
 * case, reads the interaction and the guardrail decisions from the ledger, and waits for the
 * evaluation every bound evaluator records about the interaction. Without a {@link
 * akka.javasdk.testkit.eval.Gate} every case must pass, which suits a mocked model. With a real
 * model gate the batch on rates.
 */
package akka.javasdk.testkit.eval;

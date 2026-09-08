/**
 * Evaluation of an agent inside a {@code TestKitSupport} test. Cases run against the running
 * service and are scored on the reply and on the tool calls the runtime traced.
 *
 * <p>An {@link akka.javasdk.testkit.eval.EvalCase} has three parts: the user message, a setup that
 * prepares the mocked tools, and the evaluators the reply and the tool calls are checked with. The
 * built-in evaluators are in {@link akka.javasdk.testkit.eval.Evaluators}. Cases are written in
 * Java, or derived from recorded production interactions by {@link
 * akka.javasdk.testkit.eval.EvalCaseParser}. A derived case loads the recorded tool results into
 * the test's stubs through {@link akka.javasdk.testkit.eval.ToolBindings} and expects the recorded
 * behavior as a baseline.
 *
 * <p>{@link akka.javasdk.testkit.eval.ExperimentRunner} calls the agent in a fresh session per
 * case. Without a {@link akka.javasdk.testkit.eval.Gate} every case must pass, which suits a mocked
 * model. With a real model gate the batch on rates. Tool and model evidence is read from the
 * runtime trace through {@link akka.javasdk.testkit.TelemetryReader}.
 *
 * <p>Criteria the built-in expectations cannot express, such as tone or completeness, go to a model
 * through {@link akka.javasdk.testkit.eval.Judge}.
 *
 * <p>{@code SupportAgentEvalTest} in the {@code akka-javasdk-tests} module is a complete example.
 */
package akka.javasdk.testkit.eval;

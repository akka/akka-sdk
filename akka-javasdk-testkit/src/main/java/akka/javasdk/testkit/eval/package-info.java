/**
 * Evaluation of an agent inside a {@code TestKitSupport} test. Cases run against the running
 * service and are scored on the reply and on the tool calls the runtime traced.
 *
 * <p>An {@link akka.javasdk.testkit.eval.EvalCase} has three parts: the user message, a setup that
 * prepares the mocked tools, and the {@link akka.javasdk.testkit.eval.Expectations} to assert.
 * Cases are written in Java, or derived from recorded production interactions by {@link
 * akka.javasdk.testkit.eval.EvalCaseParser}. A derived case primes the test's stubs with the
 * recorded tool results through {@link akka.javasdk.testkit.eval.ToolBindings} and expects the
 * recorded behaviour as a baseline.
 *
 * <p>{@link akka.javasdk.testkit.eval.ExperimentRunner} calls the agent in a fresh session per
 * case. Run one case at a time with a mocked model, or a whole batch judged by a {@link
 * akka.javasdk.testkit.eval.Gate} with a real model. Tool and model evidence is read from the
 * runtime trace through {@link akka.javasdk.testkit.eval.TracedTurns}.
 *
 * <p>Criteria the built-in expectations cannot express, such as tone or completeness, go to a model
 * through {@link akka.javasdk.testkit.eval.Judge}.
 *
 * <p>{@code SupportAgentEvalTest} in the {@code akka-javasdk-tests} module is a complete example.
 */
package akka.javasdk.testkit.eval;

/**
 * In-test evaluation of an Akka SDK service: cases run through the running service inside a {@code
 * TestKitSupport} test, scored over what the service replied and which tools it called.
 *
 * <p>The model: an evaluation case has three parts, each with its own source of truth.
 *
 * <ul>
 *   <li><b>Stimulus</b> — the user message ({@link
 *       akka.javasdk.testkit.eval.EvalCase#userMessage}).
 *   <li><b>World</b> — what the mocked tools return, as plain Java code ({@link
 *       akka.javasdk.testkit.eval.EvalCase#setup}).
 *   <li><b>Expectations</b> — what to assert ({@link akka.javasdk.testkit.eval.Expectations}).
 * </ul>
 *
 * <p>Cases come from two sources producing the same shape:
 *
 * <ul>
 *   <li><b>Curated</b> — authored in Java, all three parts hand-written. Expectations are ground
 *       truth.
 *   <li><b>Replayed</b> — derived from a recorded production interaction by {@link
 *       akka.javasdk.testkit.eval.EvalCaseParser}: the stimulus is the recorded input, the world is
 *       the recorded tool results primed into the consumer's stubs through {@link
 *       akka.javasdk.testkit.eval.ToolBindings}, and the expectations are the recorded behavior, as
 *       a baseline rather than as correctness.
 * </ul>
 *
 * <p>The test names the agent under test, {@code ExperimentRunner.forAgent(testKit,
 * SupportAgent::ask)}, and runs either per case (strict, for a mocked model) or as one gated batch
 * (statistical, for a real model) through {@link akka.javasdk.testkit.eval.ExperimentRunner}. The
 * runner makes the call, in a fresh session per case.
 *
 * <p>Tool evidence comes from the trace the runtime records for the agent call, read through {@link
 * akka.javasdk.testkit.eval.TracedTurns}: every tool's name, arguments, result and error, found by
 * the session the runner gave the case. The consumer still decides what the world knows, with stubs
 * injected through the TestKit's {@code DependencyProvider}, but writes nothing down. {@code
 * SupportAgentEvalTest} in this module's tests is a worked example, over a support agent whose
 * {@code CrmClient} is mocked.
 *
 * <p>What the built-in expectations cannot state — that an answer explains itself, or keeps a tone
 * — goes to a model through {@link akka.javasdk.testkit.eval.Judge}, declared as a criterion in
 * words and turned into a finding by a threshold. {@code Judge.agent(testKit)} asks a model through
 * the {@link akka.javasdk.testkit.eval.JudgeAgent}, whose model is the consumer's; a test returns a
 * verdict directly, or mocks that agent's model.
 *
 * <p>Compared with evalkit's {@code Scenario}/{@code SystemUnderTest}: the fixture indirection
 * ({@code Precursor.Fixture} by name) is replaced by a plain setup lambda, because in-process there
 * is no data/code gap to bridge; the name-based indirection survives only where data genuinely
 * cannot carry code — the per-service {@code ToolBindings} map for replayed cases.
 */
package akka.javasdk.testkit.eval;

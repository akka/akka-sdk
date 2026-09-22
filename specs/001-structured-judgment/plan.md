# Implementation Plan: Structured judgment effect on the agent effects builder

**Branch**: `001-structured-judgment` | **Date**: 2026-09-22 | **Issue**: lightbend/kalix#16830 | **Epic**: lightbend/kalix#16829
**Input**: Issue 16830 proposes `effects().judgment()` with `state(...)`, keyed `question(...)` calls, and a `Judgment` reply.

## Summary

Add a structured judgment request to the `Agent` effects builder. The developer calls
`effects().judgment()`, supplies a state value and a keyed set of questions (choice, score,
yes/no), and returns `Effect<Judgment>`. The runtime sends the request to a SystemOne endpoint
(TypeSafe, `POST /v1/systemone`), maps errors, records token usage, and returns fixed answer
shapes over the SPI. The SDK converts them into the `Judgment` record. The reply type is fixed
by the API, so the judgment path has no `responseAs`, no JSON schema, and no user-defined output
types. A judgment call does not use session memory, tools, MCP, or streaming.

The work spans two repositories. The runtime owns the SPI and the HTTP call. The SDK owns the
public API, the effect conversion, the testkit, and the documentation. The SDK part cannot ship
before a runtime release that understands the new SPI effect.

## Technical Context

**Language/Version**: Java 21 public API, Scala 2.13 implementation, sbt build (`build.sbt`,
`project/Dependencies.scala`). Samples and doc snippets use Maven.
**Runtime coupling**: The SDK compiles against `io.akka:akka-sdk-spi_2.13` at
`AkkaRuntimeVersion` (`project/Dependencies.scala:11`, currently `1.6.17`). The SPI source is
`protocols/akka-sdk-spi/src/main/scala/akka/runtime/sdk/spi/SpiAgent.scala` in
`lightbend/akka-runtime`. `updateRuntimeVersions.sh <version>` bumps the pin.
**SDK to runtime**: In-JVM SPI. The runtime loads the SDK with `ServiceLoader` and calls
`SpiAgent.handleCommand`; effects carry Scala function values, so there is no wire protocol to
extend. Only an older SDK with a newer runtime has to keep working.
**Model calls today**: The SDK never calls a model endpoint. `AgentImpl.handleCommand`
(`akka-javasdk/src/main/scala/akka/javasdk/impl/agent/AgentImpl.scala:641-770`) converts the
builder state into `SpiAgent.RequestModelEffect`, and the runtime performs the call, applies the
`responseMapping` and `failureMapping` functions, and produces the reply.
**External API (TypeSafe SystemOne, from docs.typesafe.ai)**:

| Item | Value |
|---|---|
| Endpoint | `POST https://api.typesafe.ai/v1/systemone`, `Authorization: Bearer <key>`, JSON |
| Request | `state` (string, object, or array; numbers and booleans are rejected with 422), `model` (`jev-latest` default alias), `questions` (map key to question) |
| Question `choice` | `instructions`, `criteria` map option key to description or null, at most 255 options |
| Question `score` | `instructions`, `criteria` list of 2 to 10 level descriptions |
| Question `noul` (yes/no) | `instructions`, optional `criteria` `{true: ..., false: ...}` |
| Answer `choice` | `choice`, `probabilities` (map), `confidence` (0 to 1) |
| Answer `score` | `score` (double, may fall between levels), `legend` (index to level), `probabilities` (list), `confidence` |
| Answer `noul` | `noul` (probability of yes), no confidence |
| Response envelope | `model`, `answers` (map), `usage {input_tokens, output_tokens}` |
| Errors | 401 bad key, 422 validation, 429 rate limit, 529 overloaded |
| Limits | 64k tokens per request, 32k for state plus the longest question; text only; no streaming |

**Testing**: ScalaTest unit tests in `akka-javasdk`, JUnit integration tests in
`akka-javasdk-tests` through `TestKitSupport`, testkit tests in `akka-javasdk-testkit`, runtime
tests in `akka-runtime`.
**Constraints**: No new SDK exception types. No new `Agent.Effect` subtype, so the annotation
processor and `AgentDescriptorFactory` stay unchanged. SPI additions are additive. Docs code
lives in compiled snippets under `samples/doc-snippets`.

## Repository conventions check

The SDK repository has no spec kit constitution. The plan follows the checked-in conventions
instead.

- **Writing style** (`CLAUDE.md`, `docs/AGENTS.md`): short sentences, no contractions, no
  em-dash, second person, active voice, no marketing words, no development history in docs.
- **Docs structure**: new pages sit under `docs/src/modules/sdk/pages/agents/` and register in
  `docs/src/modules/sdk/nav.adoc`. Every code example is a tagged region in a compiled sample.
  External URLs go through `docs/src/modules/ROOT/partials/external-links.adoc`. Vale runs on
  every docs PR (`make vale`).
- **Javadoc**: contract level only. Maintainer notes go in `//` comments or the PR.
- **Git**: no co-author or generated-with trailers.

## Decisions

**D1. The runtime performs the HTTP call.** Every model interaction goes through the runtime
today. The runtime holds the retry and timeout logic, the interaction log, tracing, token
accounting, and the per-agent provider override that the testkit uses. Putting the SystemOne
call in the SDK would duplicate all of that and would make judgment calls invisible to the
console. The cost is a two-repository change and a runtime release before the SDK release.

**D2. The reply type is the fixed record `Judgment`.** SystemOne returns three fixed answer
shapes keyed by question. The builder ends in `thenReply()` returning `Effect<Judgment>`. The
existing `map(Function<Judgment, T>)` and `onFailure(...)` steps stay available. Neither side
uses `SpiAgent.deserialize` or JSON schema generation on this path.

**D3. A separate `JudgmentModelProvider` type, not a new `ModelProvider` variant.**
`ModelProvider` is sealed, carries chat settings such as temperature, and the runtime turns each
variant into a langchain4j chat model. A SystemOne model is not a chat model. A separate sealed
type keeps `model-provider = system-one` from being accepted on a chat agent and keeps
`ModelProvider.openAi()` from being accepted on a judgment builder. The provider block and the
record are named `system-one` and `SystemOne` after the API family, not after a vendor.
"Typesafe" was the former name of the company behind Akka and still appears in Java package
names such as `com.typesafe.config`, and more than one model implements the SystemOne API. The
default `base-url` is the TypeSafe Jev endpoint. Other SystemOne endpoints override it.

**D4. `judgment()` must be the first call on the builder.** `BaseAgentEffectBuilder` is
mutable and accumulates a `RequestModel`. `judgment()` throws `IllegalStateException` when a
model request or a reply has already been started on the same builder. The judgment builder has
no `systemMessage`, `userMessage`, `memory`, `tools`, or `mcpTools` methods, so the compiler
rejects the rest.

**D5. The SDK serializes the state to JSON text before it crosses the SPI.** `state(Object)`
accepts a `String`, or any object or collection the SDK JSON serializer can handle. The SDK
rejects `null`, numbers, and booleans at build time because the API returns 422 for them. The
SPI carries the state as a JSON string, so the SPI does not depend on Jackson types.

**D6. Request guardrails apply to the state text. Response guardrails do not apply.** The state
leaves the service for an external provider, so guardrails configured with
`use-for = ["model-request"]` run on the serialized state as text content. SystemOne returns no
generated text, so `model-response` guardrails have nothing to check.

**D7. Errors map to existing SDK exceptions.** 401, 403, and 422 map to `ModelException`,
with the HTTP status and the response body in the message. 429 maps to `RateLimitException`.
5xx, including 529 overloaded, maps to `InternalServerException`, which is how the runtime maps
5xx from chat providers today. Connection and response timeouts map to `ModelTimeoutException`.
No new `FailureReason` and no new SDK exception type. The runtime retries 429 and 529 with
backoff up to `max-retries`, and does not retry 401 or 422.

**D8. The builder validates the request before the call.** At least one question. Question keys
are non-blank and unique. Instructions are non-blank. A choice has 1 to 255 options with unique
keys. A score has 2 to 10 levels. Violations throw `IllegalArgumentException` from the builder
method that receives the bad input, so the stack trace points at the developer's code.

**D9. Naming.** The user-facing term is "structured judgment". Types are `Judgment`,
`Question`, and `JudgmentModelProvider`, all in `akka.javasdk.agent`. Answer records nest in
`Judgment` as `Judgment.ChoiceAnswer`, `Judgment.ScoreAnswer`, and `Judgment.YesNoAnswer`, so
they do not clash with `Question.Choice`, `Question.Score`, and `Question.YesNo`. The API term
`noul` appears only in the wire format and in the Jackson type name; the SDK says yes/no. The
testkit evaluation package already has `Judge`, `Judge.Verdict`, and `JudgeAgent`. Those decide
how well a reply meets a criterion during evaluation. The docs page for structured judgment
states the difference in its overview.

**D10. A judgment is recorded in the interaction log as a model interaction.** The runtime
stores the request JSON (state and questions) as the user message and the answers JSON as the
response content, with token counts, the provider name `system-one`, and the model name. The
console shows it with no proto or console change. A dedicated interaction type is a compatible
later addition if the console wants to render questions and probabilities.

## Phase 1: Public API design

### Builder entry point (`akka-javasdk/src/main/java/akka/javasdk/agent/Agent.java`)

```java
public interface Builder {
  // existing methods unchanged

  /**
   * Start a structured judgment request. Must be the first call on the builder.
   */
  JudgmentBuilder judgment();
}

/** Builder for a structured judgment request. */
public interface JudgmentBuilder extends MappingResponseBuilder<Judgment> {
  /** Override the judgment model provider for this request. */
  JudgmentBuilder model(JudgmentModelProvider provider);

  /**
   * The content to judge. A String, or an object or collection that the SDK can serialize to
   * JSON. Numbers, booleans and null are rejected.
   */
  JudgmentBuilder state(Object state);

  /** Add a question under a key. Keys must be unique. */
  JudgmentBuilder question(String key, Question question);
}
```

`JudgmentBuilder` extends the existing `Effect.MappingResponseBuilder<Judgment>`, so
`thenReply()`, `thenReply(Metadata)`, `map(Function<Judgment, T>)`, and
`onFailure(Function<Throwable, Judgment>)` keep the same shape and semantics as on the model
request path. No new mapping or failure builder interfaces are needed.

Usage, as proposed in the issue:

```java
public Effect<Judgment> triage(String ticket) {
  return effects()
    .judgment()
    .state(ticket)
    .question("route", Question.choice("Which team should handle this?")
        .option("billing", "Payments, invoicing, refunds")
        .option("technical", "Bugs, outages, integrations"))
    .question("severity", Question.score("How severe is this?",
        "Low", "Medium", "High", "Critical"))
    .question("urgent", Question.yesNo("Does this need a reply today?",
        "Time-sensitive", "Can wait"))
    .thenReply();
}
```

### `Question` (new file `akka/javasdk/agent/Question.java`)

```java
public sealed interface Question permits Question.Choice, Question.Score, Question.YesNo {
  String instructions();

  static Choice choice(String instructions);
  static Score score(String instructions, String... levels);
  static YesNo yesNo(String instructions);
  static YesNo yesNo(String instructions, String whenYes, String whenNo);

  /** One option per key, in insertion order. The description may be empty. */
  record Choice(String instructions, List<Option> options) implements Question {
    public record Option(String key, Optional<String> description) {}
    public Choice option(String key, String description);
    public Choice option(String key);
  }

  record Score(String instructions, List<String> levels) implements Question {}

  record YesNo(String instructions, Optional<String> whenYes, Optional<String> whenNo)
      implements Question {}
}
```

Records validate their arguments in the compact constructor (D8). `Choice.option(...)` returns
a new record; the type is immutable.

### `Judgment` (new file `akka/javasdk/agent/Judgment.java`)

```java
public record Judgment(Map<String, Answer> answers, String model, Agent.TokenUsage tokenUsage) {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ChoiceAnswer.class, name = "choice"),
    @JsonSubTypes.Type(value = ScoreAnswer.class, name = "score"),
    @JsonSubTypes.Type(value = YesNoAnswer.class, name = "noul")
  })
  public sealed interface Answer permits ChoiceAnswer, ScoreAnswer, YesNoAnswer {}

  public record ChoiceAnswer(String selected, Map<String, Double> probabilities, double confidence)
      implements Answer {}

  public record ScoreAnswer(
      double score, List<String> legend, List<Double> probabilities, double confidence)
      implements Answer {}

  public record YesNoAnswer(double probability) implements Answer {
    public boolean isYes();                 // probability >= 0.5
    public boolean isYes(double threshold);
  }

  /** Throws IllegalArgumentException when the key is unknown or the answer has another type. */
  public ChoiceAnswer choice(String key);
  public ScoreAnswer score(String key);
  public YesNoAnswer yesNo(String key);
}
```

`Judgment` is a reply value. A caller that invokes the agent through the component client
receives it serialized, so the sealed `Answer` type carries Jackson type information in the same
way `SessionMessage` does (`akka/javasdk/agent/SessionMessage.java:22-28`). Phase 1 verifies the
round trip in `JsonSerializationSpec`.

### `JudgmentModelProvider` (new file `akka/javasdk/agent/JudgmentModelProvider.java`)

```java
public sealed interface JudgmentModelProvider
    permits JudgmentModelProvider.FromConfig, JudgmentModelProvider.SystemOne,
            JudgmentModelProvider.Custom {

  /** The provider named by akka.javasdk.agent.judgment-model-provider. */
  static JudgmentModelProvider fromConfig();
  /** The provider at a config path, resolved like ModelProvider.fromConfig(String). */
  static JudgmentModelProvider fromConfig(String configPath);
  /** SystemOne with defaults from akka.javasdk.agent.system-one. */
  static SystemOne systemOne();

  record FromConfig(String configPath) implements JudgmentModelProvider {}

  record SystemOne(
      String apiKey, String modelName, String baseUrl,
      Duration connectionTimeout, Duration responseTimeout, int maxRetries)
      implements JudgmentModelProvider {
    static SystemOne fromConfig(Config config);
    // withApiKey, withModelName, withBaseUrl, withConnectionTimeout, withResponseTimeout, withMaxRetries
  }

  /** In-process provider. The testkit implements it; a service may implement it for a proxy. */
  non-sealed interface Custom extends JudgmentModelProvider {
    Judgment judge(JudgmentRequest request);
    default String modelName() { return "custom"; }
  }
}

/** The request a Custom provider receives. */
public record JudgmentRequest(String stateJson, Map<String, Question> questions) {
  /** The state as plain text when it was given as a String, otherwise the JSON text. */
  public String stateAsText();
}
```

The `fromConfig` resolution mirrors `AgentImpl.modelProviderFromConfig`
(`AgentImpl.scala:111-156`): an empty path reads `akka.javasdk.agent.judgment-model-provider`,
a bare name resolves under `akka.javasdk.agent.`, `provider = "system-one"` selects the record,
and a fully qualified class name instantiates a `Custom` implementation.

### Configuration (`akka-javasdk/src/main/resources/reference.conf`)

Add under `akka.javasdk.agent`:

```hocon
# The default judgment model provider used by effects().judgment() when the agent does not
# name one. References a config section for the provider, such as system-one.
judgment-model-provider = ""
```

Add a provider block with an AsciiDoc tag, next to the other provider blocks:

```hocon
// tag::system-one[]
# Configuration for SystemOne structured judgment models. The default endpoint is the TypeSafe Jev API.
akka.javasdk.agent.system-one {
  # The provider name, must be "system-one"
  provider = "system-one"
  # The API key for the SystemOne endpoint
  api-key = ""
  # Environment variable override for the API key
  api-key = ${?SYSTEM_ONE_API_KEY}
  # The model to use. "jev-latest" is the most recent stable release.
  model-name = "jev-latest"
  # Base URL of the SystemOne API. Override for a compatible endpoint.
  base-url = "https://api.typesafe.ai"
  # Fail the request if connecting to the API takes longer than this
  connection-timeout = 15s
  # Fail the request if the answer takes longer than this
  response-timeout = 30s
  # Retry this many times on rate limit (429) or overload (529) responses
  max-retries = 2
}
// end::system-one[]
```

### What does not change

- `Agent.StreamEffect` and `streamEffects()`. SystemOne does not stream.
- `akka-javasdk-validations/.../AgentValidations.java` and
  `AgentDescriptorFactory.scala`. The handler still returns `Agent.Effect<T>`.
- `Reflect.isCommandHandlerCandidate`, `SdkRunner` descriptor construction.
- `ModelProvider` and all chat provider config blocks.
- Session memory. A judgment call neither reads nor writes the session.

## Phase 1: SPI additions (akka-runtime, `protocols/akka-sdk-spi/.../SpiAgent.scala`)

All additions are new classes inside `object SpiAgent`. Nothing existing changes.

```scala
sealed trait JudgmentQuestion { def key: String; def instructions: String }
final class ChoiceQuestion(val key: String, val instructions: String,
    val options: Seq[(String, Option[String])]) extends JudgmentQuestion
final class ScoreQuestion(val key: String, val instructions: String,
    val levels: Seq[String]) extends JudgmentQuestion
final class NoulQuestion(val key: String, val instructions: String,
    val whenTrue: Option[String], val whenFalse: Option[String]) extends JudgmentQuestion

sealed trait JudgmentAnswer
final class ChoiceAnswer(val choice: String, val probabilities: Map[String, Double],
    val confidence: Double) extends JudgmentAnswer
final class ScoreAnswer(val score: Double, val legend: Seq[String],
    val probabilities: Seq[Double], val confidence: Double) extends JudgmentAnswer
final class NoulAnswer(val probability: Double) extends JudgmentAnswer

final class JudgmentRequest(val stateJson: String, val questions: Seq[JudgmentQuestion])
final class JudgmentResponse(val model: String, val answers: Map[String, JudgmentAnswer],
    val inputTokenCount: Int, val outputTokenCount: Int, val timestamp: Instant)

sealed trait JudgmentModelProvider {
  def providerName: String
  def modelName: String
  def modelSettings: ModelSettings
}
object JudgmentModelProvider {
  final class SystemOne(val apiKey: String, val modelName: String, val baseUrl: String,
      val modelSettings: ModelSettings) extends JudgmentModelProvider {
    override def providerName: String = "system-one"
  }
  final class Custom(val providerName: String, val modelName: String,
      val judge: JudgmentRequest => Future[JudgmentResponse]) extends JudgmentModelProvider {
    override def modelSettings: ModelSettings = DefaultModelSettings
  }
}

final class RequestJudgmentEffect(
    val provider: JudgmentModelProvider,
    val request: JudgmentRequest,
    val responseMapping: JudgmentResponse => Any,
    val failureMapping: Option[Throwable => Any],
    val replyMetadata: SpiMetadata,
    val requestGuardrails: Seq[Guardrail]) extends Effect
```

Notes for the SPI change:

- `responseMapping` is mandatory. The SDK always supplies it: convert the SPI answers to
  `Judgment`, then apply the developer's `map` function when present. The runtime serializes the
  result with the existing `SpiAgent.serialize`.
- `failureMapping` follows `RequestModelEffect`. The SDK wraps the developer's `onFailure` in
  `mapSpiAgentException` (`AgentImpl.scala:907-948`), which translates `FailureReason` values
  into SDK exceptions.
- `ModelSettings` is reused for timeouts and retries. `additionalModelRequestHeaders` is
  honored on the SystemOne request as well.
- Existing `FailureReason` values cover every error case (D7). No new reason.
- Adding a subtype to the sealed `Effect` trait makes every exhaustive match in the runtime fail
  to compile until it handles `RequestJudgmentEffect`. That is the intended way to find the
  places to change.
- The SDK's `mapSpiAgentException` already guards against unknown reasons with a `MatchError`
  catch, so an older SDK on a newer runtime keeps working.

## Phase 1: Runtime execution (akka-runtime)

Line numbers below come from the local checkout at commit `3210ef27c` (2026-04-10), which lags
the `1.6.17` release. Re-read the files on current `main` before editing; the structure is the
same, the numbers may shift.

Facts that shape the runtime work:

- **In-JVM SPI.** The runtime loads the SDK through `ServiceLoader`
  (`protocols/akka-sdk-spi/.../Runner.scala:74-82`). Effects are Scala objects that carry function
  values. There is no wire protocol for agent effects.
- **Dispatch point.** `runtime/core/src/main/scala/kalix/runtime/agent/Agent.scala:212-254`
  `handleCommand` matches `ReplyEffect`, `ErrorEffect`, and `RequestModelEffect`. The streaming
  twin is at `:635-656`.
- **Model call.** `requestModel` (`Agent.scala:260-417`) builds a langchain4j chat model through
  `ChatModelFactory` (`ChatModelFactory.scala:36-69`) and runs `LlmLoop`. HTTP goes through
  `AkkaLangChain4jHttpClient` (Akka HTTP, pool sized from `AgentSettings`, header redaction list
  at `:61-107`). langchain4j is not involved in a SystemOne call.
- **Error path.** langchain4j exceptions become `FailureReason` in
  `AgentUtilities.convertLangchainExceptions` (`:250-262`). The runtime hands the
  `AgentException` to `req.failureMapping` when present (`Agent.scala:594-618`), otherwise it
  reports the error with a code from `UserFunctionErrors.Agent` (`Agent.scala:426-499`).
- **Telemetry.** `AgentInstrumentation.scala` is typed on `RequestModelEffect` and langchain4j
  `ChatRequest` and `ChatResponse`. `metrics/AgentMetrics.scala:109-186` records
  `gen_ai.client.operation.duration` and `gen_ai.client.token.usage`. The token metric is marked
  `billing: true` in `weaver/model/agent/metrics.yaml:19-32`. `tracing/AgentTracing.scala:664-698`
  opens the `LLM call: <model>` span.
- **Interaction log.** `AgentInteractionLogClient.Interaction` (`:64-100`) is typed on
  `RequestModelEffect` and writes the proto `ModelResponse` from `kalix/proxy/agent.proto`.
- **Token usage to the SDK.** `Agent.toMetadata` (`:549-562`) sets `AgentInputTokensKey` and
  `AgentOutputTokensKey` on the reply metadata.
- **Config.** All provider configuration is SDK-side and travels in the SPI. The runtime
  `reference.conf:366-413` `agent` block holds `max-model-response-bytes`,
  `max-model-connections`, and `max-queued-model-requests`, which the SystemOne client reuses.
- **SPI compatibility rule** (runtime `CLAUDE.md:35`): only an older SDK with a newer runtime must
  work. SPI classes are `final class` with `val` parameters, annotated `@InternalApi`, never case
  classes. `sbt akka-sdk-spi/mimaReportBinaryIssues` checks the change; the sealed `Effect`
  addition is an intended incompatibility to whitelist.

Runtime tasks:

| Area | File | Change |
|---|---|---|
| SPI | `protocols/akka-sdk-spi/src/main/scala/akka/runtime/sdk/spi/SpiAgent.scala` | Add the classes from the SPI section. Annotate `@InternalApi`. Run MiMa and whitelist the sealed `Effect` addition. |
| HTTP client | New `runtime/core/src/main/scala/kalix/runtime/agent/SystemOneClient.scala` | Akka HTTP `Http().singleRequest` with `Authorization: Bearer`, the provider's `additionalModelRequestHeaders`, connection pool settings as in `AkkaLangChain4jHttpClient.scala:128-135`, `toStrict(responseTimeout, maxModelResponseBytes)`. JSON through Jackson (`JacksonObjectMapperProvider`, as `Agent.scala:210` does); parse the pre-serialized state JSON into a tree and place it under `state`. Codecs for the three question and answer shapes, `model`, and `usage`. Reuse the header redaction list for request logging. Retry 429 and 529 with exponential backoff up to `maxModelRetries`; do not retry other statuses. Structure after `CallHome.scala`; per-call timeouts after `McpClient.scala`. |
| Status mapping | `SystemOneClient.scala` | The client owns the mapping because langchain4j is not in the path: 429 to `RateLimitFailure`; 5xx including 529 to `InternalFailure`; 401, 403, 422 and other 4xx to `ModelFailure` with the status and body in the message; connect or read timeout to `TimeoutFailure`; unparseable response body to `ModelFailure`. |
| Factory | New `runtime/core/src/main/scala/kalix/runtime/agent/JudgmentClientFactory.scala` | Match `JudgmentModelProvider.SystemOne` to a `SystemOneClient` and `Custom` to an adapter that calls `judge`. Mirrors `ChatModelFactory.scala:36-69`. One instance per `Agent`, created next to `langchainHttpClient` (`Agent.scala:208`). |
| Dispatch | `Agent.scala:234` and `:646` | Add `case req: SpiAgent.RequestJudgmentEffect => requestJudgment(...)` in `handleCommand`. In the streaming match, answer with an `ErrorEffect` that says structured judgment does not support streaming. |
| Execution | `Agent.scala`, new `requestJudgment` | Modeled on `requestModel` without tools, memory, or response format. Run `req.requestGuardrails` against a `TextContent` of the state JSON with the same helper `requestModel` uses (`:302-305`). Call the client. Apply `req.responseMapping`, then `spiAgent.serialize`. Reply with `toMetadata(tokenUsage)`. On `AgentException`, use `tryUserFailureMapping` (`:594-618`), otherwise `reportableError` with the existing model error code `AK-01202`; mapping exceptions use `AK-01203`. |
| Telemetry | `telemetry/instrumentation/AgentInstrumentation.scala` (`:57-70`, `:210-230`, `:424-455`, `:614-632`, `:827-933`) | Add `judgmentCallStarted(effect)` returning an instrumentation with `judgmentCallCompleted(response)` and `judgmentCallFailed(error)`. Active, no-op, and streaming implementations. |
| Metrics | `telemetry/metrics/AgentMetrics.scala:109-186` and the legacy mirror | Record `gen_ai.client.operation.duration` with `gen_ai.provider.name = system-one`, `gen_ai.request.model = <model>`, and a judgment-specific `gen_ai.operation.name`. Record `gen_ai.client.token.usage` for input and output so the call is billed like a model call. |
| Tracing | `telemetry/tracing/AgentTracing.scala:664-698` | Open a `Judgment call: <model>` span with the GenAI token attributes (`:165-166`). Debug tracing dumps the request and answers JSON. |
| Interaction log | `AgentInteractionLogClient.scala:64-100`, `kalix/proxy/agent.proto` | Generalize `Interaction` so it accepts either effect. Record the request JSON as the user message and the answers JSON as `ModelResponse.content` with the token counts. No proto change in this iteration (D10); a dedicated message is a compatible later addition. |
| Error codes | `Agent.scala:426-499`, `AgentInteractionLogClient.scala:78-96` | Both matches on `FailureReason` are exhaustive. No new reason is added, so they compile unchanged; add judgment cases to the tests that cover them. |
| Tests | `runtime/core/src/test/scala/kalix/runtime/agent/` | `SystemOneClientSpec` against a stub HTTP server: golden request JSON for all question shapes, response parsing, status mapping, retry counts and backoff. `JudgmentClientFactorySpec`. `AgentSpec` cases: `Custom` path, guardrail rejection, failure mapping, token metadata, streaming rejection. Additions to `AgentMetricsSpec` and `AgentTracingSpec`. |
| Release notes | `docs/runtime-release-notes-template.adoc` flow in the SDK repo | Entry for the runtime release under `docs/src/modules/reference/pages/release-notes/runtime/`. |

No runtime `reference.conf` change is needed. Timeouts, retries, and headers travel in
`ModelSettings` from the SDK configuration.

### Phase A status (2026-09-22)

Implemented in the local `akka-runtime` checkout, uncommitted, on `main` at `23f195e83` plus
these changes. `runtime-core/Test/compile`, `akka-sdk-spi/mimaReportBinaryIssues`, scalafmt and
header checks pass. The affected suites pass: `SystemOneClientSpec` (new), `AgentSpec`,
`AgentInteractionLogClientSpec`, `AgentInteractionLogSpec`, `AgentMetricsSpec`,
`AgentTracingSpec`.

Deviations from the table above:

- No MiMa exclusion was needed. Adding classes to the sealed `Effect` trait reports no binary
  issue against 1.6.17.
- The client, the JSON codec and the factory live in one file,
  `runtime/core/src/main/scala/kalix/runtime/agent/JudgmentClient.scala`.
- `Interaction.modelProvider` became `Interaction.modelConfig: Map[String, String]`, with the
  provider to config conversion moved to `AgentInteractionLogClient.modelConfig` and a new
  `judgmentModelConfig`. The autonomous `ModelInteraction` and one spec were updated for the
  rename.
- The state is sent to guardrails and the interaction log as plain text: a JSON string state is
  unquoted, an object or array state is passed as its JSON text.
- Debug tracing does not yet attach the request and answers JSON to the judgment span. The span
  carries operation name, provider, model, conversation id, response model and token usage.
- The legacy Prometheus instrumenter records judgment calls on the model call counters keyed by
  model name.
- Runtime release notes are not written yet; that happens with the release.

## Phase 1: SDK implementation by file

| File | Change |
|---|---|
| `akka-javasdk/src/main/java/akka/javasdk/agent/Agent.java` | Add `Effect.Builder#judgment()` and nested `Effect.JudgmentBuilder`. Javadoc on `Effect.Builder` states that `judgment()` starts a separate request kind. |
| `akka-javasdk/src/main/java/akka/javasdk/agent/Question.java` | New sealed interface and records (Phase 1 design). |
| `akka-javasdk/src/main/java/akka/javasdk/agent/Judgment.java` | New record with nested `Answer` types and typed accessors. |
| `akka-javasdk/src/main/java/akka/javasdk/agent/JudgmentModelProvider.java` | New sealed interface, `SystemOne` record with `fromConfig(Config)` and withers, `Custom`. |
| `akka-javasdk/src/main/java/akka/javasdk/agent/JudgmentRequest.java` | New record given to `Custom` providers. |
| `akka-javasdk/src/main/scala/akka/javasdk/impl/agent/AgentEffectImpl.scala` | Add `RequestJudgment(provider, state, questions, responseMapping, failureMapping, replyMetadata)` to the `PrimaryEffectImpl` ADT next to `RequestModel` (line 65). Add `JudgmentEffectBuilder[Reply]` that implements `JudgmentBuilder`, `MappingFailureBuilder`, `FailureBuilder`, `Effect[Reply]`, and `AgentEffectImpl`, following the mutable pattern of `BaseAgentEffectBuilder` (line 117). Implement `BaseAgentEffectBuilder.judgment()` with the first-call check (D4). |
| `akka-javasdk/src/main/scala/akka/javasdk/impl/agent/AgentImpl.scala` | Add `judgmentModelProviderFromConfig` next to `modelProviderFromConfig` (line 111). Add `toSpiJudgmentModelProvider` next to `toSpiModelProvider` (line 184); for `Custom` wrap `judge` into `JudgmentRequest => Future[JudgmentResponse]` with the SPI to SDK conversions. Add `case req: RequestJudgment =>` in `handleCommand` next to the `RequestModel` case (line 687): serialize the state with the SDK serializer, convert questions, build `responseMapping` as SPI answers to `Judgment` then the developer's mapper, wrap `failureMapping` with `mapSpiAgentException`, pass `guardrails.modelRequestGuardrails`. Honor the per-agent override (line 688). |
| `akka-javasdk/src/main/scala/akka/javasdk/impl/agent/OverrideModelProvider.scala` | Add a second map for `JudgmentModelProvider` keyed by component id, or a sibling class, so the testkit can override per agent. |
| `akka-javasdk/src/main/resources/reference.conf` | Add `judgment-model-provider` and the tagged `system-one` block. |
| `project/Dependencies.scala:11` | Bump `AkkaRuntimeVersion` to the runtime release that ships the SPI, through `updateRuntimeVersions.sh`. |

Conversion rules in `AgentImpl`:

- State: a `String` becomes a JSON string literal. Any other object goes through the same
  serializer as reply payloads and produces a JSON object or array. Numbers, booleans, and null
  never reach this point because the builder rejects them.
- `Question.Choice` becomes `ChoiceQuestion` with `(key, description)` pairs in insertion order.
  An empty description becomes `None`, which the runtime sends as JSON `null`.
- `Judgment.tokenUsage` comes from `JudgmentResponse.inputTokenCount` and `outputTokenCount`.
  The runtime also sets `SpiAgent.AgentInputTokensKey` and `AgentOutputTokensKey` on the reply
  metadata, so `AgentReply.tokenUsage` works for component client callers without SDK changes
  (`AgentInvokeReplyOnlyMethodRefImpl.scala:49-52`).

### Phase B status (2026-09-22)

Committed on the `structured-judgment-effect` branch of `akka-sdk`, built against the locally
published runtime `1.6.17-18-bd82910a-SNAPSHOT` from the Phase A branch with
`-Dakka-runtime.version`. `AkkaRuntimeVersion` in `project/Dependencies.scala` stays at `1.6.17`
until the runtime release exists. The review fixes of both branches are included: the SPI effect
field is `modelProvider` and choice options are `SpiAgent.ChoiceOption` values.

Verified:

| Check | Result |
|---|---|
| `JudgmentEffectSpec`, `JudgmentModelProviderSpec`, `JudgmentSerializationSpec`, `ModelProviderSpec` | 48 tests pass |
| `TestJudgmentModelProviderTest` | 8 tests pass |
| `JudgmentAgentIntegrationTest` through the dev runtime | 6 tests pass |
| `CompileTimeAgentValidationSpec`, `RuntimeAgentValidationSpec` with the new fixture | 24 tests pass |
| `AgentIntegrationTest` regression | passes, see the run log of 2026-09-22 |
| Header and format checks on `akka-javasdk`, `akka-javasdk-testkit`, `akka-javasdk-tests` | pass |

Deviations from the tables above:

- `JudgmentBuilder` is implemented by two classes, `JudgmentEffectBuilder` for the first stage
  and `JudgmentMappingEffectBuilder` for the stage after `map` or `onFailure`, because the
  existing `MappingResponseEffectBuilder` is typed on the model request.
- The two existing `updateRequestModel` matches in `BaseAgentEffectBuilder` and
  `AgentStreamEffectImpl` gained a `RequestJudgment` case that throws, to keep the match
  exhaustive.
- `JudgmentModelProvider.SystemOne` carries `additionalModelRequestHeaders`, read from
  `additional-model-request-headers` in the `system-one` block, for parity with the chat providers.
- Test agents in `akka-javasdk-tests` are registered in
  `src/test/resources/META-INF/akka-javasdk-components_akka-javasdk-tests.conf`, not by the
  annotation processor.
- The testkit answer helpers live as static methods on `TestJudgmentModelProvider`
  (`choice`, `score`, `yes`, `no`), not in a separate class.

Not done, by decision on 2026-09-22:

- The documentation page, navigation entry, testing and failure handling sections, provider
  configuration table, external link, Vale vocabulary and doc-snippets were written and then
  reverted. They wait for a separate go-ahead.
- No live call against the Jev API. No `SYSTEM_ONE_API_KEY` is available on the build machine.
- Neither branch is pushed.

## Phase 1: Testkit

| File | Change |
|---|---|
| `akka-javasdk-testkit/src/main/java/akka/javasdk/testkit/TestJudgmentModelProvider.java` | New class implementing `JudgmentModelProvider.Custom`. API mirrors `TestModelProvider`: `fixedAnswers(Map<String, Judgment.Answer>)`, `whenState(String)`, `whenState(Predicate<JudgmentRequest>)`, `WhenClause.reply(Map<String, Judgment.Answer>)`, `WhenClause.reply(Function<JudgmentRequest, Map<String, Judgment.Answer>>)`, `WhenClause.failWith(RuntimeException)`, `reset()`. First match wins, newest clause first, unmatched requests throw `MissingJudgmentResponseException`. |
| `akka-javasdk-testkit/src/main/java/akka/javasdk/testkit/TestKit.java` | Add `Settings.withJudgmentModelProvider(Class<?> agentClass, JudgmentModelProvider provider)` next to `withModelProvider` (line 753) and the runner wiring next to line 1102. |
| `akka-javasdk-testkit/src/main/java/akka/javasdk/testkit/Answers.java` (or static factories on the answer records) | Helpers to build plausible answers in tests: a choice answer from a winner with probability one and confidence one, a score answer from a level index, a yes/no answer from a probability. |

`TestJudgmentModelProvider` runs in process through the SPI `Custom` provider, in the same way
`TestModelProvider` reaches the runtime through `ModelProvider.Custom`. No HTTP emulation.

## Phase 1: Documentation

| File | Change |
|---|---|
| `docs/src/modules/sdk/pages/agents/judgment.adoc` | New page, title "Structured judgment". Sections: what a structured judgment is and when to use it instead of a model request; the three question types; reading answers and confidence; configuring the provider; failure handling; testing; See also. States the difference from the evaluation `Judge` in the overview. Follows `agents/structured.adoc` in layout. |
| `docs/src/modules/sdk/nav.adoc` | Add the page after `agents/structured.adoc` (line 13). |
| `docs/src/modules/sdk/pages/agents.adoc` | Mention `judgment()` in "Agent's effect API" (line 50) with an xref. |
| `docs/src/modules/sdk/pages/agents/testing.adoc` | Add "Mocking judgment responses" with a snippet using `TestJudgmentModelProvider`. |
| `docs/src/modules/sdk/pages/agents/failures.adoc` | One paragraph on which exceptions a judgment call raises (D7). |
| `docs/src/modules/sdk/pages/model-provider-details.adoc` | Add a "SystemOne" section under "Model configuration" and a reference configuration include for the `system-one` tag (line 788 onwards). |
| `docs/src/modules/ROOT/partials/external-links.adoc` | Add `url-typesafe-docs` and `url-typesafe-api`. |
| `docs/styles/config/vocabularies/Akka/accept.txt` | Add `TypeSafe`, `SystemOne`, `Jev`; re-sort with `docs/bin/sort-vale-vocab.sh`. |
| `samples/doc-snippets/src/main/java/com/example/application/TriageAgent.java` | New snippet class with tags `judgment` (the builder), `judgment-answers` (reading `Judgment` in a caller), `judgment-model` (provider override). |
| `samples/doc-snippets/src/main/resources/application.conf` | Tag `agent-judgment-model-config` showing `judgment-model-provider = system-one`. |
| `samples/doc-snippets/src/test/java/...` | Test-scope snippet for `TestJudgmentModelProvider`. |
| `docs/src/modules/reference/pages/release-notes.adoc` | Bullet under the SDK release that ships the feature. |

Run `docs/bin/verify-include-paths.sh` and `make vale` before the docs PR. The doc-snippets
`pom.xml` pins a released SDK version; compile the snippets against the local SDK by bumping the
version temporarily and reverting, as `docs/AGENTS.md` describes.

## Phase 2: Tests

**SDK unit tests (ScalaTest, `akka-javasdk/src/test/scala/akka/javasdk/impl/agent/`)**

- `JudgmentEffectSpec`: builder produces `RequestJudgment` with ordered questions; each D8
  rule throws `IllegalArgumentException`; `judgment()` after `systemMessage(...)` throws
  `IllegalStateException`; state serialization for `String`, record, list, and rejected types.
- `JudgmentModelProviderSpec`: `fromConfig()` resolves the default key, a bare name, a full
  path, and a custom class name; `SystemOne.fromConfig` reads every key including the environment
  override; missing `provider` fails with a clear message.
- `AgentImplJudgmentSpec`: the SPI effect built from `RequestJudgment` has the expected
  provider, state JSON, question conversion, and `responseMapping` that yields a `Judgment`
  with `tokenUsage`; `failureMapping` translates `RateLimitFailure` into `RateLimitException`.
- `JsonSerializationSpec`: `Judgment` with all three answer types round-trips through the SDK
  serializer.

**Integration tests (`akka-javasdk-tests/src/test/java/akkajavasdk/components/agent/`)**

- `TriageJudgmentAgent` with the three question types, called through the component client
  with a `TestJudgmentModelProvider` registered in `TestKit.Settings`. Asserts the typed
  accessors, `AgentReply.tokenUsage`, and that no session memory entry was written.
- Failure path: the test provider throws; `onFailure` maps it to a fallback `Judgment`.
- Mapping path: `map(j -> j.choice("route").selected()).thenReply()` returns `Effect<String>`.

**Testkit tests (`akka-javasdk-testkit/src/test/java/akka/javasdk/testkit/`)**

- `TestJudgmentModelProviderTest`: clause ordering, fixed answers, predicate matching on
  `stateAsText()`, missing response exception, `reset()`.

**Annotation processor tests**

- Add `ValidAgentWithJudgmentEffect.java` under
  `akka-javasdk-annotation-processor-tests/component-validation-descriptors/.../test-sources/valid/`
  and a case in `AgentValidationSpec` to lock in that `Effect<Judgment>` handlers pass without
  processor changes.

**Runtime tests (akka-runtime)**

- Request JSON golden test for all three question types, including null criteria for a bare
  option and the `{true, false}` form for yes/no.
- Response parsing for all three answer shapes, `usage`, and `model`.
- Error mapping for 401, 422, 429, 529, connect timeout, and response timeout against a stub
  HTTP server; retry count and backoff for 429 and 529; no retry for 401 and 422.
- Guardrail invocation on the state text.
- The `Custom` provider path returns the SDK-supplied response without an HTTP call.

**Manual verification**

- One real call against `jev-latest` with `SYSTEM_ONE_API_KEY` set, using the doc-snippets
  `TriageAgent`, recorded in the PR description. Not part of CI.

## Delivery phases

**Phase A, akka-runtime PR.** SPI additions, effect execution, HTTP client, error mapping,
guardrails, interaction log, tracing, token usage, runtime tests, and the MiMa whitelist entry
for the sealed `Effect` addition. No dev-runtime work: `akka-runtime-dev` is runtime core plus
H2, and the testkit path is the in-process `Custom` provider. Ships in a runtime release, for
example `1.6.18`.

**Phase B, akka-sdk PR.** Public API, effect implementation, SPI conversion, config, testkit,
tests, docs, snippets, release note, and the `AkkaRuntimeVersion` bump to the Phase A release.

**Working in parallel.** Develop Phase B against a locally published runtime. In `akka-runtime`
run `sbt publishLocal` with a distinct version, then run the SDK build with
`sbt -Dakka-runtime.version=<that version>`. The local `akka-runtime` checkout at
`/media/kevin/ExtraDrive1/code/akka/akka-runtime` is at a commit from 2026-04-10 and lags the
`1.6.17` release; update it before starting Phase A.

**Merge order.** Phase A merges and releases first. Phase B merges after the runtime release
exists in the Akka repository, because `RuntimeDependencyCheck` and the parent POM resolve the
pinned version.

## Out of scope and follow-ups

- Gateway router decisions with structured judgment (lightbend/kalix#16831).
- Structured judgment as a guardrail or as an evaluator agent. The epic lists these as later
  integration points.
- An autonomous agent capability that uses structured judgment.
- Structured instructions and criteria (JSON objects with data references into the state). The
  SPI classes take strings in this iteration. Widening them to JSON values later is additive.
- Enum-typed choice options, for example `Question.choice(instructions, MyEnum.class)`.
- A client-side token estimate against the 32k state limit. The API rejects oversize requests
  with 422, which surfaces as `ModelException`.
- Streaming. SystemOne does not stream.

## Complexity Tracking

| Added complexity | Why it is needed | Simpler alternative rejected because |
|---|---|---|
| Second provider hierarchy, `JudgmentModelProvider` | A SystemOne model is not a chat model; the sealed `ModelProvider` contract requires chat settings and a langchain4j factory | Adding a `ModelProvider.SystemOne` variant would let a chat agent select it and fail at call time |
| Two-repository delivery with a runtime release in between | The runtime owns all model calls, credentials, retries, the interaction log, and the testkit override path | An SDK-side HTTP client would work without a runtime change but would be invisible to the console and would duplicate retry, tracing, and token accounting |
| Jackson type annotations on `Judgment.Answer` | The reply crosses the component client as JSON and holds three answer shapes in one map | Three separate maps on `Judgment` avoid polymorphism but make the typed accessors and the question keys harder to read |

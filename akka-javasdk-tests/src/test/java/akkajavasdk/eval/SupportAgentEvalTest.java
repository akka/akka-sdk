/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.eval;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.DependencyProvider;
import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akka.javasdk.testkit.eval.EvalCase;
import akka.javasdk.testkit.eval.EvalCaseParser;
import akka.javasdk.testkit.eval.Evaluators;
import akka.javasdk.testkit.eval.ExperimentRunner;
import akka.javasdk.testkit.eval.Gate;
import akka.javasdk.testkit.eval.Judge;
import akka.javasdk.testkit.eval.JudgeAgent;
import akka.javasdk.testkit.eval.ToolBindings;
import akkajavasdk.components.agent.eval.AccountSupportAgent;
import akkajavasdk.components.agent.eval.CrmClient;
import akkajavasdk.components.agent.eval.Customer;
import akkajavasdk.components.agent.eval.SupportAgent;
import akkajavasdk.components.agent.eval.Ticket;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How a consumer's eval suite reads, against a service that is really running.
 *
 * <p>The world under test is one mocked dependency: {@link CannedCrmClient} takes the place of the
 * {@link CrmClient} the {@link SupportAgent} would call in production, so the test decides what the
 * CRM knows before the cases run. It records nothing: the runner calls the agent and reads the tool
 * evidence from the trace the runtime wrote for the call.
 *
 * <p>{@link AccountSupportAgent} is the same assistant with a command handler that takes a record.
 * Its cases carry that record, so the customer asking is part of the case rather than of the
 * question text.
 *
 * <p>The model is mocked too, by a {@link TestModelProvider} that behaves like a competent one: it
 * looks a customer up when the question names one, reads their tickets when the question asks for
 * them, and answers without tools when there is nothing to look up. That is what makes the wiring
 * mode deterministic. Point the same cases at a real model by dropping the {@code
 * withModelProvider} line and running the gated batch instead.
 */
public class SupportAgentEvalTest extends TestKitSupport {

  // The model sees an agent-local tool prefixed with the agent's simple name. The trace reader
  // strips it, so expectations are written against the plain method names.
  private static final String GET_CUSTOMER = "SupportAgent_getCustomer";
  private static final String OPEN_TICKETS = "SupportAgent_openTickets";
  private static final String ACCOUNT_GET_CUSTOMER = "AccountSupportAgent_getCustomer";
  private static final String ACCOUNT_OPEN_TICKETS = "AccountSupportAgent_openTickets";

  private static final Pattern CUSTOMER_ID = Pattern.compile("cust_\\d+");

  private final TestModelProvider supportModel = new TestModelProvider();
  private final TestModelProvider accountModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();
  private final CannedCrmClient crm = new CannedCrmClient();

  private ExperimentRunner experimentRunner;

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withDependencyProvider(dependencies())
        .withModelProvider(SupportAgent.class, supportModel)
        .withModelProvider(AccountSupportAgent.class, accountModel)
        .withModelProvider(JudgeAgent.class, judgeModel);
  }

  /** The stand-in the agent is constructed with, in place of the real CRM client. */
  private DependencyProvider dependencies() {
    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> type) {
        if (type == CrmClient.class) return (T) crm;
        throw new IllegalArgumentException("no such dependency: " + type);
      }
    };
  }

  // ---- the mocked model: deterministic, and plausible enough that the cases mean something --

  @BeforeEach
  public void createTheRunner() {
    experimentRunner = new ExperimentRunner(testKit);
  }

  /** What the CRM knows. Every case reads these records, keyed by customer id. */
  @BeforeEach
  public void fillTheCrm() {
    crm.reset();
    crm.add(new Customer("cust_1", "Ada Lovelace", "gold"));
    crm.add(new Customer("cust_7", "Grace Hopper", "silver"));
    crm.addTickets("cust_7", new Ticket("t_9", "card declined at checkout", "open"));
  }

  /** One case with the mocked model. There is no gate, so the case itself must pass. */
  private ExperimentRunner.CaseResult runOne(EvalCase<String> evalCase) {
    return experimentRunner.cases(evalCase).agent(SupportAgent::ask).run().results().getFirst();
  }

  @BeforeEach
  public void mockTheModel() {
    supportModel.reset();
    scriptTheModel(supportModel, GET_CUSTOMER, OPEN_TICKETS);

    // The same script for the agent whose command handler takes a record. Its tools carry that
    // agent's name, and the customer id reaches the model in the user message the agent builds
    // from the command.
    accountModel.reset();
    scriptTheModel(accountModel, ACCOUNT_GET_CUSTOMER, ACCOUNT_OPEN_TICKETS);
  }

  private static void scriptTheModel(
      TestModelProvider model, String getCustomer, String openTickets) {

    model
        .whenUserMessage(message -> customerId(message.content()).isPresent())
        .reply(
            message ->
                new AiResponse(
                    new ToolInvocationRequest(getCustomer, arguments(message.content()))));

    model
        .whenUserMessage(message -> customerId(message.content()).isEmpty())
        .reply("Happy to help. Give me a customer id and I will look them up.");

    // A tool result comes back without the question that prompted it, so this stand-in decides
    // the follow-up from the record it just read: the ticket fixtures are cust_7. A real model
    // still has the question in its context and needs no such trick.
    model
        .whenToolResult(
            result -> result.name().equals(getCustomer) && result.content().contains("cust_7"))
        .thenReply(
            result ->
                new AiResponse(
                    new ToolInvocationRequest(openTickets, "{\"customerId\":\"cust_7\"}")));

    model
        .whenToolResult(
            result -> result.name().equals(getCustomer) && !result.content().contains("cust_7"))
        .thenReply(result -> new AiResponse("Customer record: " + result.content()));

    model
        .whenToolResult(result -> result.name().equals(openTickets))
        .thenReply(result -> new AiResponse("Open tickets: " + result.content()));
  }

  private static Optional<String> customerId(String message) {
    var match = CUSTOMER_ID.matcher(message);
    return match.find() ? Optional.of(match.group()) : Optional.empty();
  }

  private static String arguments(String message) {
    return "{\"customerId\":\"" + customerId(message).orElseThrow() + "\"}";
  }

  Stream<EvalCase<String>> curatedCases() {
    return Stream.of(
        EvalCase.of(
            "customer-lookup",
            "Is cust_1 still one of our customers, and under what name?",
            Evaluators.shouldCallTool("getCustomer"),
            Evaluators.shouldCallToolWith("getCustomer", "customerId", "cust_1"),
            Evaluators.shouldNotCallTool("openTickets"),
            Evaluators.replyShouldContain("Ada Lovelace")),
        EvalCase.of(
            "open-tickets",
            "What is cust_7 waiting on? List their open tickets.",
            Evaluators.shouldCallTools("getCustomer", "openTickets"),
            Evaluators.shouldCallToolsInOrder("getCustomer", "openTickets"),
            Evaluators.shouldCallToolWith("openTickets", "customerId", "cust_7"),
            Evaluators.replyShouldContain("card declined")),
        EvalCase.of(
            "no-tools-for-smalltalk",
            "hi there!",
            Evaluators.shouldNotCallTools("getCustomer", "openTickets"),
            Evaluators.replyShouldContain("customer id")));
  }

  @Test
  public void traceCarriesWhatTheToolReturned() {
    var lookup =
        EvalCase.of(
            "customer-lookup-result",
            "Who is cust_1?",
            Evaluators.shouldCallToolWith("getCustomer", "customerId", "cust_1"),
            Evaluators.toolResultShouldContain("getCustomer", "Ada Lovelace"),
            Evaluators.toolResultShouldContain("getCustomer", "\"tier\":\"gold\""));

    var result = runOne(lookup);

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    assertThat(result.interaction().toolCalls()).hasSize(1);
    assertThat(result.interaction().toolCalls().getFirst().result()).isPresent();
  }

  @Test
  public void traceCarriesTheModelCallsAndTheLatency() {
    var tickets =
        EvalCase.of(
            "open-tickets-evidence",
            "What is cust_7 waiting on? List their open tickets.",
            Evaluators.shouldCallToolsInOrder("getCustomer", "openTickets"));

    var result = runOne(tickets);

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    var interaction = result.interaction();
    // One model call to ask for the customer, one for the tickets, one to answer.
    assertThat(interaction.modelCalls()).hasSize(3);
    assertThat(interaction.modelCalls().getFirst().finishReasons()).contains("TOOL_EXECUTION");
    assertThat(interaction.modelCalls().getLast().finishReasons()).contains("STOP");
    assertThat(interaction.modelCalls()).allSatisfy(call -> assertThat(call.model()).isNotEmpty());
    assertThat(interaction.latency()).isPositive();
    assertThat(interaction.finalModelText()).isEqualTo(interaction.reply());
    assertThat(interaction.guardrails()).isEmpty();
    assertThat(interaction.blocked()).isFalse();
    assertThat(result.describe()).contains("model: 3 calls");
  }

  @Test
  public void budgetsReadTheTraceAndTokensAreInconclusiveUnderAScriptedModel() {
    var tickets =
        EvalCase.of(
            "open-tickets-budget",
            "What is cust_7 waiting on? List their open tickets.",
            Evaluators.shouldMakeAtMostToolCalls(2),
            Evaluators.shouldMakeAtMostModelCalls(2),
            Evaluators.shouldUseAtMostTokens(1_000),
            Evaluators.shouldReplyWithin(ofSeconds(30)));

    var result = runOne(tickets);

    // Three model calls against a budget of two is the one failure; the test model reports no
    // tokens, so that budget is inconclusive rather than passing on nothing.
    assertThat(result.passed()).isFalse();
    assertThat(result.describe())
        .contains("PASS tool-call-budget")
        .contains("FAIL model-call-budget: made 3 model calls, allowed 2")
        .contains("INCONCLUSIVE token-budget")
        .contains("PASS latency-budget");
  }

  @Test
  public void traceKeepsTheFailedToolCall() {
    var unknown =
        EvalCase.of(
            "unknown-customer", "Who is cust_404?", Evaluators.shouldCallTool("getCustomer"));

    var result = runOne(unknown);

    // The tool threw, so the agent call failed and the target reported it.
    assertThat(result.passed()).isFalse();
    assertThat(result.describe()).contains("FAIL target");

    // The trace still has the call, with the error the tool raised.
    var calls = result.interaction().toolCalls();
    assertThat(calls).hasSize(1);
    assertThat(calls.getFirst().name()).isEqualTo("getCustomer");
    assertThat(calls.getFirst().arguments()).containsEntry("customerId", "cust_404");
    assertThat(calls.getFirst().error())
        .hasValueSatisfying(e -> assertThat(e).contains("cust_404"));
  }

  // ---- a command handler that takes its own type ----

  @Test
  public void aCommandOfItsOwnTypeIsSentToTheAgent() {
    // The caller is part of the command, not of the question, so this case cannot be written as
    // one string.
    var ticketsOfTheCaller =
        EvalCase.of(
            "tickets-of-the-caller",
            new AccountSupportAgent.Question("cust_7", "What am I waiting on?"),
            Evaluators.shouldCallToolsInOrder("getCustomer", "openTickets"),
            Evaluators.shouldCallToolWith("getCustomer", "customerId", "cust_7"),
            Evaluators.replyShouldContain("card declined"));

    var result =
        experimentRunner
            .cases(ticketsOfTheCaller)
            .agent(AccountSupportAgent::ask)
            .run()
            .results()
            .getFirst();

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    // The case's command as text, which is its JSON for a command that is not a String.
    assertThat(result.interaction().input())
        .isEqualTo("{\"customerId\":\"cust_7\",\"text\":\"What am I waiting on?\"}");
    // What the model saw: the user message the handler built from the command. A judge is asked
    // about this rather than about the command.
    assertThat(result.interaction().userMessage())
        .isEqualTo("Customer cust_7 asks: What am I waiting on?");
    assertThat(result.interaction().asked()).isEqualTo(result.interaction().userMessage());
    assertThat(result.describe()).contains("PASS tool-order").contains("PASS tool-arguments");
  }

  @Test
  public void typedCasesRunAsABatchBehindAGate() {
    List<EvalCase<AccountSupportAgent.Question>> cases =
        List.of(
            EvalCase.of(
                "who-am-i",
                new AccountSupportAgent.Question("cust_1", "What name is my account under?"),
                Evaluators.shouldCallToolWith("getCustomer", "customerId", "cust_1"),
                Evaluators.shouldNotCallTools("openTickets"),
                Evaluators.replyShouldContain("Ada Lovelace")),
            EvalCase.of(
                "my-open-tickets",
                new AccountSupportAgent.Question("cust_7", "Anything still open on my account?"),
                Evaluators.shouldCallTools("getCustomer", "openTickets"),
                Evaluators.replyShouldContain("card declined")));

    var report =
        experimentRunner
            .cases(cases)
            .agent(AccountSupportAgent::ask)
            .gate(Gate.passRateShouldBeAtLeast(1.0).and(Gate.targetShouldNotFail()))
            .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
  }

  // ---- mode 2: quality, one gated batch. Tag it and point it at a real model. ----

  @Test
  public void qualityGate() {
    var report =
        experimentRunner
            .cases(curatedCases().toList())
            .agent(SupportAgent::ask)
            .gate(
                Gate.passRateShouldBeAtLeast(0.9)
                    .and(Gate.evaluatorPassRateShouldBeAtLeast(Evaluators.TOOL_ARGUMENTS, 1.0))
                    .and(Gate.targetShouldNotFail()))
            .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
    assertThat(report.passRate()).isEqualTo(1.0);
  }

  // ---- mode 3: replay captured traffic. Baseline expectations, same runner. ----

  @Test
  public void replayBaseline() {
    // The captures carry production's spend too, so each case is held to its model call count
    // and to its recorded latency times the tolerance; the token budget is inconclusive under the
    // scripted model.
    var replayed = EvalCaseParser.parse(captures());
    var bindings =
        ToolBindings.builder()
            .bind("getCustomer", crm::loadCustomer)
            .bind("openTickets", crm::loadTickets)
            .build();

    var report =
        experimentRunner
            .cases(replayed)
            .bindings(bindings)
            .agent(SupportAgent::ask)
            .gate(Gate.passRateShouldBeAtLeast(0.85))
            .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
    assertThat(report.render())
        .contains("model-call-budget 3/3")
        .contains("latency-budget 3/3")
        .contains("token-budget 0/0 (3 inconclusive)")
        .contains("spend: ")
        .contains("over 6/6 cases with evidence");
  }

  /** The captures, from the test classpath rather than a path relative to the working dir. */
  private static Path captures() {
    try {
      return Path.of(SupportAgentEvalTest.class.getResource("/eval/captures.jsonl").toURI());
    } catch (java.net.URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  // ---- a criterion the built-ins cannot state, scored by a model ----

  @Test
  public void judgedAgainstACriterion() {
    // The testkit's judge agent, with its model mocked the same way the support agent's is.
    judgeModel
        .whenUserMessage(message -> message.content().contains("Ada Lovelace"))
        .reply(
            JsonSupport.encodeToString(
                new Judge.Verdict(0.9, "it names the customer and their tier")));
    var judge = Judge.modelBased(testKit);

    var evalCase =
        EvalCase.of(
            "judged-lookup",
            "Who is cust_1?",
            Evaluators.shouldCallTool("getCustomer"),
            judge.shouldSatisfy(
                "the reply states the customer's name and tier and invents nothing"));

    var result = runOne(evalCase);

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    assertThat(result.describe()).contains("PASS judge").contains("scored 0.90, needed 0.50");
  }

  @Test
  public void aJudgeRendersTheInputAsToldAndKeepsItsModelUnderTheTestKitOverride() {
    // The rendering reaches the judge model as the user message. The model provider registered
    // for JudgeAgent in the TestKit settings answers whatever path withModel names.
    judgeModel
        .whenUserMessage(message -> message.content().startsWith("Kryterium:"))
        .reply(JsonSupport.encodeToString(new Judge.Verdict(1, "spelnia")));
    var judge =
        Judge.modelBased(testKit)
            .withUserMessage(
                (criterion, interaction) ->
                    "Kryterium:\n" + criterion + "\n\nOdpowiedz:\n" + interaction.reply())
            .withModel("eval.judge-model");

    var result =
        runOne(
            EvalCase.of(
                "judged-in-polish",
                "Who is cust_1?",
                judge.shouldSatisfy("odpowiedz podaje nazwisko klienta")));

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    assertThat(result.describe()).contains("PASS judge").contains("spelnia");
  }

  @Test
  public void aJudgeThatWillNotScoreIsInconclusiveInsteadOfFailingTheCase() {
    judgeModel.fixedResponse(
        JsonSupport.encodeToString(new Judge.Verdict(-1, "I cannot judge this")));
    var judge = Judge.modelBased(testKit);

    var result =
        runOne(
            EvalCase.of(
                "unjudgeable", "Who is cust_1?", judge.shouldSatisfy("the reply is helpful")));

    assertThat(result.passed()).isTrue();
    assertThat(result.describe()).contains("INCONCLUSIVE judge");
  }

  @Test
  public void reportsWhatTheAgentDidWhenACaseFails() {
    var wrongExpectation =
        EvalCase.of(
            "wrong-customer",
            "Who is cust_1?",
            Evaluators.shouldCallToolWith("getCustomer", "customerId", "cust_2"));

    var result = runOne(wrongExpectation);

    assertThat(result.passed()).isFalse();
    assertThat(result.describe())
        .contains("case wrong-customer FAILED")
        .contains("getCustomer{customerId=cust_1}")
        .contains("expected cust_2");
  }
}

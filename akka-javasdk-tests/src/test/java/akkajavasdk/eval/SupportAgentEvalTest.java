/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akka.javasdk.testkit.eval.EvalCase;
import akka.javasdk.testkit.eval.ExperimentRunner;
import akka.javasdk.testkit.eval.Gate;
import akkajavasdk.components.agent.eval.AccountSupportAgent;
import akkajavasdk.components.agent.eval.CrmClient;
import akkajavasdk.components.agent.eval.Customer;
import akkajavasdk.components.agent.eval.SupportAgent;
import akkajavasdk.components.agent.eval.Ticket;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How a consumer's eval suite reads, against a service that is really running.
 *
 * <p>The cases are the questions. What the agent must do with them is stated by two evaluator
 * components in this package, {@link ToolUseEvaluator} and {@link ReplyGroundingEvaluator}, bound
 * to both support agents in the test configuration. The runtime runs them on every interaction, and
 * the runner reads their verdicts from the ledger.
 *
 * <p>The world under test is one mocked dependency: {@link CannedCrmClient} takes the place of the
 * {@link CrmClient} the {@link SupportAgent} would call in production, so the test decides what the
 * CRM knows before the cases run.
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

  // The model sees an agent-local tool prefixed with the agent's simple name.
  private static final String GET_CUSTOMER = "SupportAgent_getCustomer";
  private static final String OPEN_TICKETS = "SupportAgent_openTickets";
  private static final String ACCOUNT_GET_CUSTOMER = "AccountSupportAgent_getCustomer";
  private static final String ACCOUNT_OPEN_TICKETS = "AccountSupportAgent_openTickets";

  private static final Pattern CUSTOMER_ID = Pattern.compile("cust_\\d+");

  private final TestModelProvider supportModel = new TestModelProvider();
  private final TestModelProvider accountModel = new TestModelProvider();
  private final CannedCrmClient crm = new CannedCrmClient();

  private ExperimentRunner experimentRunner;

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withDependencyProvider(dependencies())
        .withModelProvider(SupportAgent.class, supportModel)
        .withModelProvider(AccountSupportAgent.class, accountModel)
        .withAdditionalConfig(
            """
            akka.javasdk.evaluation.evaluators {
              tool-use.agents {
                support-agent { trigger = interaction, trigger-on-failure = true }
                account-support-agent { trigger = interaction }
              }
              reply-grounding.agents {
                support-agent { trigger = interaction }
                account-support-agent { trigger = interaction }
              }
            }
            """);
  }

  /** The stand-in the agent is constructed with, in place of the real CRM client. */
  private DependencyProvider dependencies() {
    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> type) {
        if (type == CrmClient.class) return (T) crm;
        return null;
      }
    };
  }

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

  // ---- the mocked model: deterministic, and plausible enough that the cases mean something --

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
        .thenReply(
            result ->
                new AiResponse("Grace Hopper has one open ticket: card declined at checkout."));
  }

  private static Optional<String> customerId(String message) {
    var match = CUSTOMER_ID.matcher(message);
    return match.find() ? Optional.of(match.group()) : Optional.empty();
  }

  private static String arguments(String message) {
    return "{\"customerId\":\"" + customerId(message).orElseThrow() + "\"}";
  }

  // ---- the cases ----

  private static List<EvalCase<String>> curatedCases() {
    return List.of(
        EvalCase.of(
            "customer-lookup", "Is cust_1 still one of our customers, and under what name?"),
        EvalCase.of("open-tickets", "What is cust_7 waiting on? List their open tickets."),
        EvalCase.of("no-tools-for-smalltalk", "hi there!"));
  }

  // ---- mode 1: wiring, one case at a time under the mocked model ----

  @Test
  public void everyBoundEvaluatorReportsOnTheInteraction() {
    var result = runOne(EvalCase.of("customer-lookup-result", "Who is cust_1?"));

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    assertThat(result.evalResults())
        .extracting(r -> r.evaluator())
        .containsExactly("reply-grounding", "tool-use");
    assertThat(result.describe())
        .contains("PASS tool-use: looked the customer up")
        .contains("PASS reply-grounding: names the customer and their tickets");
  }

  @Test
  public void theEvidenceComesFromTheLedger() {
    var result =
        runOne(
            EvalCase.of(
                "open-tickets-evidence", "What is cust_7 waiting on? List their open tickets."));

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    var evidence = result.evidence();
    var interaction = evidence.interaction().orElseThrow();
    assertThat(interaction.agentComponentId()).isEqualTo("support-agent");
    assertThat(interaction.inputText()).isEqualTo(evidence.input());
    // One model response to ask for the customer, one for the tickets, one to answer.
    assertThat(evidence.modelResponses()).isEqualTo(3);
    assertThat(evidence.toolCalls())
        .extracting(c -> c.name())
        .containsExactly(GET_CUSTOMER, OPEN_TICKETS);
    assertThat(evidence.toolCalls().getFirst().response()).contains("Grace Hopper");
    assertThat(evidence.reply())
        .isEqualTo("Grace Hopper has one open ticket: card declined at checkout.");
    assertThat(evidence.guardrails()).isEmpty();
    assertThat(evidence.denied()).isFalse();
    assertThat(result.describe()).contains("model: 3 responses");
  }

  @Test
  public void aWrongReplyFailsTheCaseAndTheReportSaysWhy() {
    supportModel.reset();
    supportModel
        .whenUserMessage(message -> true)
        .reply("I am sure that is Charles Babbage, our best customer.");

    var result = runOne(EvalCase.of("guessed-name", "Who is cust_1?"));

    assertThat(result.failed()).isTrue();
    assertThat(result.describe())
        .contains("FAIL tool-use: expected one getCustomer call, called []")
        .contains("NOT_APPLICABLE reply-grounding: no customer was looked up");
  }

  @Test
  public void aFailedToolCallFailsTheTargetAndTheLedgerKeepsTheCall() {
    var result = runOne(EvalCase.of("unknown-customer", "Who is cust_404?"));

    // The tool threw, so the agent call failed and the runner reported it.
    assertThat(result.failed()).isTrue();
    assertThat(result.describe()).contains("FAIL target");

    // The ledger still has the interaction, with the call and the failure.
    var evidence = result.evidence();
    assertThat(evidence.failed()).isTrue();
    assertThat(evidence.failure()).hasValueSatisfying(f -> assertThat(f).contains("cust_404"));
    assertThat(evidence.toolCalls()).hasSize(1);
    assertThat(evidence.toolCalls().getFirst().name()).isEqualTo(GET_CUSTOMER);
    assertThat(evidence.toolCalls().getFirst().arguments()).contains("cust_404");

    // Only the evaluator bound with trigger-on-failure evaluates a failed interaction.
    assertThat(result.evalResults())
        .extracting(r -> r.evaluator())
        .containsExactly("target", "tool-use");
  }

  // ---- a command handler that takes its own type ----

  @Test
  public void aCommandOfItsOwnTypeIsSentToTheAgent() {
    // The caller is part of the command, not of the question, so this case cannot be written as
    // one string.
    var ticketsOfTheCaller =
        EvalCase.of(
            "tickets-of-the-caller",
            new AccountSupportAgent.Question("cust_7", "What am I waiting on?"));

    var result =
        experimentRunner
            .cases(ticketsOfTheCaller)
            .agent(AccountSupportAgent::ask)
            .run()
            .results()
            .getFirst();

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    // The case's command as text, which is its JSON for a command that is not a String.
    assertThat(result.evidence().input())
        .isEqualTo("{\"customerId\":\"cust_7\",\"text\":\"What am I waiting on?\"}");
    // What the model saw: the user message the handler built from the command, which is what the
    // evaluators read.
    assertThat(result.evidence().interaction().orElseThrow().inputText())
        .isEqualTo("Customer cust_7 asks: What am I waiting on?");
    assertThat(result.describe())
        .contains("PASS tool-use: looked the customer up and read their tickets");
  }

  @Test
  public void typedCasesRunAsABatchBehindAGate() {
    List<EvalCase<AccountSupportAgent.Question>> cases =
        List.of(
            EvalCase.of(
                "who-am-i",
                new AccountSupportAgent.Question("cust_1", "What name is my account under?")),
            EvalCase.of(
                "my-open-tickets",
                new AccountSupportAgent.Question("cust_7", "Anything still open on my account?")));

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
            .cases(curatedCases())
            .agent(SupportAgent::ask)
            .gate(
                Gate.passRateShouldBeAtLeast(0.9)
                    .and(Gate.passRateShouldBeAtLeast(ToolUseEvaluator.class, 1.0))
                    .and(Gate.targetShouldNotFail()))
            .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
    assertThat(report.passRate()).isEqualTo(1.0);
    assertThat(report.render()).contains("tool-use 3/3").contains("reply-grounding 3/3");
  }
}

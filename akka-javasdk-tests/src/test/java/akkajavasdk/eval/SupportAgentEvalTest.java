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
import akka.javasdk.testkit.eval.Expectations;
import akka.javasdk.testkit.eval.ExperimentRunner;
import akka.javasdk.testkit.eval.Gate;
import akka.javasdk.testkit.eval.Judge;
import akka.javasdk.testkit.eval.JudgeAgent;
import akka.javasdk.testkit.eval.ToolBindings;
import akkajavasdk.components.agent.eval.CrmClient;
import akkajavasdk.components.agent.eval.Customer;
import akkajavasdk.components.agent.eval.SupportAgent;
import akkajavasdk.components.agent.eval.Ticket;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How a consumer's eval suite reads, against a service that is really running.
 *
 * <p>The world under test is one mocked dependency: {@link CannedCrmClient} takes the place of the
 * {@link CrmClient} the {@link SupportAgent} would call in production, so a case can decide what
 * the CRM knows. It records nothing: the runner calls the agent and reads the tool evidence from
 * the trace the runtime wrote for the call.
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

  private static final Pattern CUSTOMER_ID = Pattern.compile("cust_\\d+");

  private final TestModelProvider supportModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();
  private final CannedCrmClient crm = new CannedCrmClient();

  private ExperimentRunner experimentRunner;

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withDependencyProvider(dependencies())
        .withModelProvider(SupportAgent.class, supportModel)
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

  @BeforeEach
  public void mockTheModel() {
    supportModel.reset();

    supportModel
        .whenUserMessage(message -> customerId(message.content()).isPresent())
        .reply(
            message ->
                new AiResponse(
                    new ToolInvocationRequest(GET_CUSTOMER, arguments(message.content()))));

    supportModel
        .whenUserMessage(message -> customerId(message.content()).isEmpty())
        .reply("Happy to help. Give me a customer id and I will look them up.");

    // A tool result comes back without the question that prompted it, so this stand-in decides
    // the follow-up from the record it just read: the ticket fixtures are cust_7. A real model
    // still has the question in its context and needs no such trick.
    supportModel
        .whenToolResult(
            result -> result.name().equals(GET_CUSTOMER) && result.content().contains("cust_7"))
        .thenReply(
            result ->
                new AiResponse(
                    new ToolInvocationRequest(OPEN_TICKETS, "{\"customerId\":\"cust_7\"}")));

    supportModel
        .whenToolResult(
            result -> result.name().equals(GET_CUSTOMER) && !result.content().contains("cust_7"))
        .thenReply(result -> new AiResponse("Customer record: " + result.content()));

    supportModel
        .whenToolResult(result -> result.name().equals(OPEN_TICKETS))
        .thenReply(result -> new AiResponse("Open tickets: " + result.content()));
  }

  private static Optional<String> customerId(String message) {
    var match = CUSTOMER_ID.matcher(message);
    return match.find() ? Optional.of(match.group()) : Optional.empty();
  }

  private static String arguments(String message) {
    return "{\"customerId\":\"" + customerId(message).orElseThrow() + "\"}";
  }

  Stream<EvalCase> curatedCases() {
    return Stream.of(
        new EvalCase(
            "customer-lookup",
            "Is cust_1 still one of our customers, and under what name?",
            () -> {
              crm.reset();
              crm.prime(new Customer("cust_1", "Ada Lovelace", "gold"));
            },
            Expectations.expect()
                .tools("getCustomer")
                .toolArgument("getCustomer", "customerId", "cust_1")
                .forbiddenTools("openTickets")
                .answerContains("Ada Lovelace")),
        new EvalCase(
            "open-tickets",
            "What is cust_7 waiting on? List their open tickets.",
            () -> {
              crm.reset();
              crm.prime(new Customer("cust_7", "Grace Hopper", "silver"));
              crm.primeTickets("cust_7", new Ticket("t_9", "card declined at checkout", "open"));
            },
            Expectations.expect()
                .tools("getCustomer", "openTickets")
                .toolOrder("getCustomer", "openTickets")
                .toolArgument("openTickets", "customerId", "cust_7")
                .answerContains("card declined")),
        new EvalCase(
            "no-tools-for-smalltalk",
            "hi there!",
            crm::reset,
            Expectations.expect()
                .forbiddenTools("getCustomer", "openTickets")
                .answerContains("customer id")));
  }

  @Test
  public void traceCarriesWhatTheToolReturned() {
    var lookup =
        new EvalCase(
            "customer-lookup-result",
            "Who is cust_1?",
            () -> {
              crm.reset();
              crm.prime(new Customer("cust_1", "Ada Lovelace", "gold"));
            },
            Expectations.expect()
                .toolArgument("getCustomer", "customerId", "cust_1")
                .toolResult("getCustomer", "Ada Lovelace")
                .toolResult("getCustomer", "\"tier\":\"gold\""));

    var result = experimentRunner.agent(SupportAgent::ask).runSingle(lookup);

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    assertThat(result.interaction().toolCalls()).hasSize(1);
    assertThat(result.interaction().toolCalls().getFirst().result()).isPresent();
  }

  @Test
  public void traceCarriesTheModelCallsAndTheLatency() {
    var tickets =
        new EvalCase(
            "open-tickets-evidence",
            "What is cust_7 waiting on? List their open tickets.",
            () -> {
              crm.reset();
              crm.prime(new Customer("cust_7", "Grace Hopper", "silver"));
              crm.primeTickets("cust_7", new Ticket("t_9", "card declined at checkout", "open"));
            },
            Expectations.expect().toolOrder("getCustomer", "openTickets"));

    var result = experimentRunner.agent(SupportAgent::ask).runSingle(tickets);

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    var interaction = result.interaction();
    // One model call to ask for the customer, one for the tickets, one to answer.
    assertThat(interaction.modelCalls()).hasSize(3);
    assertThat(interaction.modelCalls().getFirst().finishReasons()).contains("TOOL_EXECUTION");
    assertThat(interaction.modelCalls().getLast().finishReasons()).contains("STOP");
    assertThat(interaction.modelCalls()).allSatisfy(call -> assertThat(call.model()).isNotEmpty());
    assertThat(interaction.latency()).isPositive();
    assertThat(interaction.finalModelText()).isEqualTo(interaction.text());
    assertThat(interaction.guardrails()).isEmpty();
    assertThat(interaction.blocked()).isFalse();
    assertThat(result.describe()).contains("model: 3 calls");
  }

  @Test
  public void budgetsReadTheTraceAndTokensAbstainUnderAScriptedModel() {
    var tickets =
        new EvalCase(
            "open-tickets-budget",
            "What is cust_7 waiting on? List their open tickets.",
            () -> {
              crm.reset();
              crm.prime(new Customer("cust_7", "Grace Hopper", "silver"));
              crm.primeTickets("cust_7", new Ticket("t_9", "card declined at checkout", "open"));
            },
            Expectations.expect()
                .toolCallsAtMost(2)
                .modelCallsAtMost(2)
                .tokensAtMost(1_000)
                .latencyAtMost(ofSeconds(30)));

    var result = experimentRunner.agent(SupportAgent::ask).runSingle(tickets);

    // Three model calls against a budget of two is the one failure; the test model reports no
    // tokens, so that budget abstains rather than passing on nothing.
    assertThat(result.passed()).isFalse();
    assertThat(result.describe())
        .contains("PASS tool-call-budget")
        .contains("FAIL model-call-budget: made 3 model calls, allowed 2")
        .contains("ABSTAIN token-budget")
        .contains("PASS latency-budget");
  }

  @Test
  public void traceKeepsTheFailedToolCall() {
    var unknown =
        new EvalCase(
            "unknown-customer",
            "Who is cust_404?",
            crm::reset,
            Expectations.expect().tools("getCustomer"));

    var result = experimentRunner.agent(SupportAgent::ask).runSingle(unknown);

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

  // ---- mode 2: quality, one gated batch. Tag it and point it at a real model. ----

  @Test
  public void qualityGate() {
    var report =
        experimentRunner
            .cases(curatedCases().toList())
            .agent(SupportAgent::ask)
            .gate(
                Gate.passRateAtLeast(0.9)
                    .and(Gate.evaluatorRateAtLeast(Evaluators.TOOL_ARGUMENTS, 1.0))
                    .and(Gate.noTargetFailures()))
            .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
    assertThat(report.passRate()).isEqualTo(1.0);
  }

  // ---- mode 3: replay captured traffic. Baseline expectations, same runner. ----

  @Test
  public void replayBaseline() {
    var bindings =
        ToolBindings.builder()
            .bind("getCustomer", crm::loadCustomer)
            .bind("openTickets", crm::loadTickets)
            .build();
    // The captures carry production's spend too, so each case is held to its model call count
    // and to its latency with slack; the token budget abstains under the scripted model.
    var replayed = EvalCaseParser.parse(captures(), bindings);

    var report =
        experimentRunner
            .cases(replayed)
            .agent(SupportAgent::ask)
            .gate(Gate.passRateAtLeast(0.85))
            .run();

    assertThat(report.passed()).withFailMessage(report::render).isTrue();
    assertThat(report.render())
        .contains("model-call-budget 3/3")
        .contains("latency-budget 3/3")
        .contains("token-budget 0/0 (3 abstained)")
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
    // evalkit's judge agent, with its model mocked the same way the support agent's is.
    judgeModel
        .whenUserMessage(message -> message.content().contains("Ada Lovelace"))
        .reply(
            JsonSupport.encodeToString(
                new Judge.Verdict(0.9, "it names the customer and their tier")));
    var judge = Judge.agent(testKit);

    var evalCase =
        new EvalCase(
            "judged-lookup",
            "Who is cust_1?",
            () -> {
              crm.reset();
              crm.prime(new Customer("cust_1", "Ada Lovelace", "gold"));
            },
            Expectations.expect()
                .tools("getCustomer")
                .satisfies(
                    judge.mustSatisfy(
                        "the reply states the customer's name and tier and invents nothing")));

    var result = experimentRunner.agent(SupportAgent::ask).runSingle(evalCase);

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    assertThat(result.describe()).contains("PASS judge").contains("scored 0.90, needed 0.50");
  }

  @Test
  public void aJudgeThatWillNotScoreAbstainsInsteadOfFailingTheCase() {
    judgeModel.fixedResponse(
        JsonSupport.encodeToString(new Judge.Verdict(-1, "I cannot judge this")));
    var judge = Judge.agent(testKit);

    var result =
        experimentRunner
            .agent(SupportAgent::ask)
            .runSingle(
                new EvalCase(
                    "unjudgeable",
                    "Who is cust_1?",
                    () -> {
                      crm.reset();
                      crm.prime(new Customer("cust_1", "Ada Lovelace", "gold"));
                    },
                    Expectations.expect().satisfies(judge.mustSatisfy("the reply is helpful"))));

    assertThat(result.passed()).isTrue();
    assertThat(result.describe()).contains("ABSTAIN judge");
  }

  @Test
  public void reportsWhatTheAgentDidWhenACaseFails() {
    var wrongExpectation =
        new EvalCase(
            "wrong-customer",
            "Who is cust_1?",
            () -> {
              crm.reset();
              crm.prime(new Customer("cust_1", "Ada Lovelace", "gold"));
            },
            Expectations.expect().toolArgument("getCustomer", "customerId", "cust_2"));

    var result = experimentRunner.agent(SupportAgent::ask).runSingle(wrongExpectation);

    assertThat(result.passed()).isFalse();
    assertThat(result.describe())
        .contains("case wrong-customer FAILED")
        .contains("getCustomer{customerId=cust_1}")
        .contains("expected cust_2");
  }

  /**
   * The mocked dependency: canned answers, nothing written down. A curated case primes it with
   * plain Java; a replayed case primes it through {@link ToolBindings} with what production
   * recorded.
   */
  static final class CannedCrmClient implements CrmClient {

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final Map<String, List<Ticket>> tickets = new ConcurrentHashMap<>();

    @Override
    public Customer getCustomer(String customerId) {
      var customer = customers.get(customerId);
      if (customer == null) throw new NoSuchElementException("no customer " + customerId);
      return customer;
    }

    @Override
    public List<Ticket> openTickets(String customerId) {
      return tickets.getOrDefault(customerId, List.of());
    }

    void reset() {
      customers.clear();
      tickets.clear();
    }

    void prime(Customer... primed) {
      for (var customer : primed) customers.put(customer.id(), customer);
    }

    void primeTickets(String customerId, Ticket... primed) {
      tickets.put(customerId, List.of(primed));
    }

    /** {@link ToolBindings.ResultLoader} for getCustomer. */
    void loadCustomer(ToolBindings.RecordedCall call) {
      customers.put((String) call.argument("customerId"), call.resultAs(Customer.class));
    }

    /**
     * {@link ToolBindings.ResultLoader} for openTickets; the recorded result is an array of them.
     */
    void loadTickets(ToolBindings.RecordedCall call) {
      tickets.put((String) call.argument("customerId"), List.of(call.resultAs(Ticket[].class)));
    }
  }
}

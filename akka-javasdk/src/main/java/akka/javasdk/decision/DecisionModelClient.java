/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.decision;

import akka.annotation.DoNotInherit;
import java.util.Collection;
import java.util.concurrent.CompletionStage;

/**
 * Asks a decision model a set of questions about an input. A decision model answers each question
 * with probabilities instead of generated text.
 *
 * <p>Inject this client into endpoints, agents, workflows, consumers, timed actions and the service
 * setup.
 *
 * <pre>{@code
 * Decision decision =
 *     decisionModelClient.input(ticket).questions(ROUTE, SEVERITY).invoke();
 * Answer.Choice route = decision.answer(ROUTE);
 * }</pre>
 *
 * <p>The client uses the provider configured in {@code akka.javasdk.decision.model-provider} unless
 * you choose one with {@link #model(DecisionModelProvider)}.
 *
 * <p>The client sends the input and the questions to the model API as they are. Guardrails and
 * sanitizers do not apply to a decision request. To mask sensitive text before the call, use the
 * {@link akka.javasdk.SanitizerClient}.
 *
 * <p>A call fails with:
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} for an invalid request or configuration: an unsupported
 *       input, no questions, duplicate question keys, more options or levels than the provider
 *       supports, no provider configured, an unknown provider, a provider configuration section
 *       without a required key, or a provider without a model name or API key. Configuration
 *       failures surface on the first call.
 *   <li>{@link akka.javasdk.agent.RateLimitException} when the model API answers with a rate limit
 *       status (429) after the configured retries.
 *   <li>{@link akka.javasdk.agent.ModelTimeoutException} when a request to the model API times out.
 *   <li>{@link akka.javasdk.agent.InternalServerException} when the model API answers with a server
 *       error status (5xx) after the configured retries.
 *   <li>{@link akka.javasdk.agent.ModelException} for other failures, such as a rejected request,
 *       an unusable response, or a model that refuses to answer a question.
 *   <li>The unchanged exception of a {@link DecisionModelProvider.Custom} provider, when one is
 *       used.
 * </ul>
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface DecisionModelClient {

  /** A client that sends its requests to the given provider. */
  DecisionModelClient model(DecisionModelProvider provider);

  /**
   * Start a request about the given input. A {@code String} is sent as text. Any other object is
   * serialized to JSON.
   *
   * @throws IllegalArgumentException when the input is an empty string, or of a type that does not
   *     serialize to JSON text, an object or an array, such as a number, a boolean or a byte array
   * @throws NullPointerException when the input is null
   */
  InputRequest input(Object input);

  /** A request with an input. Add the questions. */
  @DoNotInherit
  interface InputRequest {

    /**
     * The questions to ask about the input.
     *
     * @throws IllegalArgumentException when there are no questions or two questions have the same
     *     key
     */
    Request questions(Question<?>... questions);

    /**
     * The questions to ask about the input, for questions collected at run time.
     *
     * @throws IllegalArgumentException when there are no questions or two questions have the same
     *     key
     */
    Request questions(Collection<Question<?>> questions);
  }

  /** A complete request. */
  @DoNotInherit
  interface Request {

    /** Send the request and wait for the decision. */
    Decision invoke();

    /** Send the request. */
    CompletionStage<Decision> invokeAsync();
  }
}

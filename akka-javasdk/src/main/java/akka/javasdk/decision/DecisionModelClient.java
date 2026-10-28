/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.decision;

import akka.annotation.DoNotInherit;
import java.util.Collection;
import java.util.concurrent.CompletionStage;

/**
 * Asks a decision model a set of questions about a state. A decision model answers each question
 * with probabilities instead of generated text.
 *
 * <p>Inject this client into endpoints, agents, workflows, consumers, timed actions and the service
 * setup.
 *
 * <pre>{@code
 * Decision decision =
 *     decisionModelClient.state(ticket).questions(ROUTE, SEVERITY).invoke();
 * Answer.Choice route = decision.answer(ROUTE);
 * }</pre>
 *
 * <p>The client uses the provider configured in {@code akka.javasdk.decision.model-provider} unless
 * you choose one with {@link #model(DecisionModelProvider)}.
 *
 * <p>A call fails with:
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} for an invalid request or configuration: an unsupported
 *       state, no questions, duplicate question keys, more options or levels than the provider
 *       supports, no provider configured, an unknown provider, or a provider without a model name
 *       or API key. Configuration failures surface on the first call.
 *   <li>{@link akka.javasdk.agent.RateLimitException} when the model API answers with a rate limit
 *       status (429) after the configured retries.
 *   <li>{@link akka.javasdk.agent.ModelTimeoutException} when a request to the model API times out.
 *   <li>{@link akka.javasdk.agent.InternalServerException} when the model API answers with a server
 *       error status (5xx) after the configured retries.
 *   <li>{@link akka.javasdk.agent.ModelException} for other failures, such as a rejected request or
 *       an unusable response.
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
   * Start a request about the given state. A {@code String} is sent as text. Any other object is
   * serialized to JSON.
   *
   * @throws IllegalArgumentException when the state is an empty string, or of a type that does not
   *     serialize to JSON text, an object or an array, such as a number, a boolean or a byte array
   * @throws NullPointerException when the state is null
   */
  StateRequest state(Object state);

  /** A request with a state. Add the questions. */
  @DoNotInherit
  interface StateRequest {

    /**
     * The questions to ask about the state.
     *
     * @throws IllegalArgumentException when there are no questions or two questions have the same
     *     key
     */
    Request questions(Question<?>... questions);

    /**
     * The questions to ask about the state, for questions collected at run time.
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

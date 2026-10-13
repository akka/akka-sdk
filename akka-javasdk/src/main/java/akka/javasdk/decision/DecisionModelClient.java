/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.decision;

import akka.annotation.DoNotInherit;
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
 * ChoiceAnswer route = decision.answer(ROUTE);
 * }</pre>
 *
 * <p>The client uses the provider configured in {@code akka.javasdk.decision.model-provider} unless
 * you choose one with {@link #model(DecisionModelProvider)}.
 *
 * <p>A failed call fails with {@link akka.javasdk.agent.ModelException}, {@link
 * akka.javasdk.agent.RateLimitException}, {@link akka.javasdk.agent.ModelTimeoutException} or
 * {@link akka.javasdk.agent.InternalServerException}.
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

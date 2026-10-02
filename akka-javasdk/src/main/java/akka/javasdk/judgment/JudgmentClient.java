/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.judgment;

import akka.annotation.DoNotInherit;
import java.util.concurrent.CompletionStage;

/**
 * Asks a judgment model a set of questions about a state. A judgment model answers each question
 * with probabilities instead of generated text.
 *
 * <p>Inject this client into endpoints, agents, workflows, consumers, timed actions and the service
 * setup.
 *
 * <pre>{@code
 * Judgment judgment =
 *     judgmentClient.state(ticket).questions(ROUTE, SEVERITY).invoke();
 * ChoiceAnswer route = judgment.answer(ROUTE);
 * }</pre>
 *
 * <p>The client uses the provider configured in {@code akka.javasdk.judgment.model-provider} unless
 * you choose one with {@link #model(JudgmentModelProvider)}.
 *
 * <p>A failed call fails with {@link akka.javasdk.agent.ModelException}, {@link
 * akka.javasdk.agent.RateLimitException}, {@link akka.javasdk.agent.ModelTimeoutException} or
 * {@link akka.javasdk.agent.InternalServerException}.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface JudgmentClient {

  /** A client that sends its requests to the given provider. */
  JudgmentClient model(JudgmentModelProvider provider);

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

    /** Send the request and wait for the judgment. */
    Judgment invoke();

    /** Send the request. */
    CompletionStage<Judgment> invokeAsync();
  }
}

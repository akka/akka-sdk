/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import java.util.List;

/**
 * Client for reading the content behind a {@link Subject.Flow}, a collection too large to resolve
 * eagerly onto {@link EvaluationContext} the way a single {@link Subject.Interaction} is. An
 * evaluator bound to a flow subject reads it through this client instead.
 *
 * <p>{@link Subject.Session} content is not supported yet.
 *
 * <p>Not for user extension.
 */
public interface SubjectClient {

  /**
   * Stream a flow's interactions in flow order, from the beginning. A large flow streams rather
   * than loading, for an evaluator that does not want everything materialized at once.
   *
   * @param flowId the id of the flow to read
   * @return a source of the flow's interactions, oldest first
   */
  Source<Interaction, NotUsed> flowInteractions(String flowId);

  /**
   * Read up to {@code limit} of a flow's interactions in flow order, from the beginning.
   *
   * <p>Blocks the calling thread until they are fetched. Safe to call on a Loom virtual thread. Use
   * {@link #flowInteractions(String)} for an evaluator that does not want a materializer.
   *
   * @param flowId the id of the flow to read
   * @param limit the maximum number of interactions to return
   * @return the flow's interactions, oldest first, capped at {@code limit}
   */
  List<Interaction> flowInteractions(String flowId, int limit);
}

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.view;

import akka.annotation.DoNotInherit;

/**
 * Kalix applications follow the Command Query Responsibility Segregation (CQRS) pattern (see
 * https://developer.lightbend.com/docs/akka-guide/concepts/cqrs.html).
 *
 * <p>Kalix' Entities represent the command side where you change the state of your model in a
 * strictly consistent and isolated manner. Kalix' Views represent the query side of your
 * application. Views are optimized for reads and allow you to query your model by fields other than
 * the entity identifier.
 *
 * <p>A View implementation consists of two parts:
 *
 * <ol>
 *   <li>One or more static public inner classes extending {@link TableUpdater}. Each concrete class
 *       is responsible for consuming events from one source, Event Sourced Entity events, Key Value
 *       Entity state changes or messages from a message broker Topic. and emitting a state that is
 *       stored in one table of the view.
 *   <li>One or more query methods annotated with queries for the tables of the view. Query methods
 *       define the type of query parameters, if any as their input, and the type of the returned
 *       result as the type parameter of <code>QueryEffect&lt;T&gt;</code>
 * </ol>
 *
 * <p>The query strings and table field types defines which fields should be indexed and how the
 * query will be executed.
 *
 * <p>The query is executed by the runtime when a request is made to the View.
 *
 * <p>
 *
 * <p>
 */
public abstract class View {

  protected final <T> QueryEffect<T> queryResult() {
    return null;
  }

  /**
   * @param <T> The type of result returned by this a query
   *     <p>Not for user extension, instances are returned through factory method {@link
   *     #queryResult()}
   */
  @DoNotInherit
  public interface QueryEffect<T> {}
}

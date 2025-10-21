/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.view;

import akka.annotation.DoNotInherit;

/**
 * A service that follows the Command Query Responsibility Segregation (CQRS) pattern.
 *
 * <p>Entities represent the command side where you change the state of your model in a strictly
 * consistent and isolated manner. Views represent the query side of your application. Views are
 * optimized for reads and allow you to query your model by fields other than the entity identifier.
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
 *       result as the type parameter of {@code QueryEffect&lt;T&gt;}
 * </ol>
 *
 * <p>The query strings and table field types define which fields should be indexed and how the
 * query will be executed.
 *
 * <p>The query is executed by the runtime when a request is made to the View.
 *
 * <p>Concrete class must be annotated with {@link akka.javasdk.annotations.Component}.
 */
public abstract class View {

  /**
   * @return query effect for a query result that is a single response in the form of a row, an
   *     Optional row or a collection of rows projected into a single result type.
   */
  protected final <T> QueryEffect<T> queryResult() {
    return null;
  }

  /**
   * @return query effect for a query result that can be streamed
   */
  protected final <T> QueryStreamEffect<T> queryStreamResult() {
    return null;
  }

  /**
   * Not for user extension, instances are returned through factory method {{@link #queryResult()}}
   *
   * @param <T> The type of result returned by this query
   */
  @DoNotInherit
  public interface QueryEffect<T> {}

  /**
   * Not for user extension, instances are returned through factory method {@link
   * #queryStreamResult()}
   *
   * @param <T> The type of result returned as a stream by this query
   */
  @DoNotInherit
  public interface QueryStreamEffect<T> {}
}

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.*;

/**
 * Annotation used in the scope of a view for providing the query that will be used to explore data
 * from that view.
 *
 * <p><b>Note: </b>the actual method implementation is never actually executed, but the return type must be either
 * {@link akka.javasdk.view.View.QueryEffect} or {@link akka.javasdk.view.View.QueryStreamEffect}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Query {
  /**
   * Assigns the actual query which makes use of the enclosing entity table name as source of data
   * for composition.
   */
  String value();

  /**
   * For a query that returns a {@link akka.javasdk.view.View.QueryStreamEffect}, instead of completing the
   * stream once the end of the result is reached, keep tailing the query and emit updates to the stream as the
   * view is updated.
   */
  boolean streamUpdates() default false;

}

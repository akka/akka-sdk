/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.annotations;

import java.lang.annotation.*;

/**
 * Annotation used in the scope of a view for providing the query that will be used to explore data
 * from that view.
 *
 * <p><b>Note: </b>the actual method implementation is irrelevant as the method itself is never
 * actually executed, only the query
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

}

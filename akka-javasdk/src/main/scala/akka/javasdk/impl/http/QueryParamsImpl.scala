/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import java.util.Optional

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption

import akka.annotation.InternalApi
import akka.http.scaladsl.model.Uri.Query
import akka.javasdk.http.QueryParams

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final case class QueryParamsImpl(query: Query) extends QueryParams {

  /**
   * Returns the value of the first parameter with the given key if it exists.
   */
  override def getString(key: String): Optional[String] = {
    query.get(key).toJava
  }

  /**
   * Returns the Integer value of the first parameter with the given key if it exists.
   */
  override def getInteger(key: String): Optional[Integer] = {
    query.get(key).map(java.lang.Integer.valueOf).toJava
  }

  /**
   * Returns the Long value of the first parameter with the given key if it exists.
   */
  override def getLong(key: String): Optional[java.lang.Long] = {
    query.get(key).map(java.lang.Long.valueOf).toJava
  }

  /**
   * Returns the Boolean value of the first parameter with the given key if it exists.
   */
  override def getBoolean(key: String): Optional[java.lang.Boolean] = {
    query.get(key).map(java.lang.Boolean.valueOf).toJava
  }

  /**
   * Returns the Double value of the first parameter with the given key if it exists.
   */
  override def getDouble(key: String): Optional[java.lang.Double] = {
    query.get(key).map(java.lang.Double.valueOf).toJava
  }

  /**
   * Returns the value of all parameters with the given key.
   */
  override def getAll(key: String): java.util.List[String] = {
    query.getAll(key).asJava
  }

  /**
   * Returns the value of all parameters with the given key using mapper function.
   */
  override def getAll[T](key: String, mapper: java.util.function.Function[String, T]): java.util.List[T] = {
    query.getAll(key).map(mapper.apply).asJava
  }

  /**
   * Returns a key/value map of the parameters. Use the `toMultiMap()` method to all parameters if keys may occur
   * multiple times.
   */
  override def toMap: java.util.Map[String, String] = {
    query.toMap.asJava
  }

  /**
   * Returns a `Map` of all parameters. Use the `toMap()` method to filter out entries with duplicated keys.
   */
  override def toMultiMap: java.util.Map[String, java.util.List[String]] = {
    query.toMultiMap.view.mapValues(_.asJava).toMap.asJava
  }
}

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class HandlerNotFoundException(
    handlerType: String,
    val name: String,
    componentClass: Class[_],
    availableHandlers: Set[String])
    extends RuntimeException(
      s"no matching [$handlerType] handler for [$name] on [${componentClass.getName}]. " +
      s"Available handlers are: [${availableHandlers.mkString(", ")}]")

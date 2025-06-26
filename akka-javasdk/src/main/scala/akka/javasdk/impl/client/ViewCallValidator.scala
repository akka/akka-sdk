/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import akka.annotation.InternalApi
import akka.javasdk.annotations.Query
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.workflow.Workflow
import java.lang.reflect.Method

import akka.javasdk.agent.Agent

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ViewCallValidator {

  def validate(method: Method): Unit = {
    val declaringClass = method.getDeclaringClass
    if (classOf[TimedAction].isAssignableFrom(declaringClass)
      || classOf[KeyValueEntity[_]].isAssignableFrom(declaringClass)
      || classOf[EventSourcedEntity[_, _]].isAssignableFrom(declaringClass)
      || classOf[Workflow[_]].isAssignableFrom(declaringClass)
      || classOf[Agent].isAssignableFrom(declaringClass)) {
      throw new IllegalStateException(
        "Use dedicated builder for calling " + declaringClass.getSuperclass.getSimpleName
        + " component method " + declaringClass.getSimpleName + "::" + method.getName + ". This builder is meant for View component calls.")
    }

    if (!method.getAnnotations.toSeq.exists(annotation =>
        classOf[Query].isAssignableFrom(annotation.annotationType()))) {
      throw new IllegalStateException(
        s"A View query method [${method.getName}] should be annotated with @Query annotation.")
    }
  }
}

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.client

import java.lang.reflect.Method
import akka.platform.javasdk.timedaction.TimedAction
import akka.platform.javasdk.annotations.Query
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity
import akka.platform.javasdk.keyvalueentity.KeyValueEntity
import akka.platform.javasdk.workflow.Workflow

object ViewCallValidator {

  def validate(method: Method): Unit = {
    val declaringClass = method.getDeclaringClass
    if (classOf[TimedAction].isAssignableFrom(declaringClass)
      || classOf[KeyValueEntity[_]].isAssignableFrom(declaringClass)
      || classOf[EventSourcedEntity[_, _]].isAssignableFrom(declaringClass)
      || classOf[Workflow[_]].isAssignableFrom(declaringClass)) {
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

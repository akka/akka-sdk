/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.serialization.JsonSerializer
import com.fasterxml.jackson.annotation.JsonSubTypes

/**
 * INTERNAL API
 */
@InternalApi
case class MethodInvokers(serializer: JsonSerializer, methodInvokers: Map[String, MethodInvoker]) {

  /**
   * This method will look up for a registered method that receives a super type of the incoming payload. It's only
   * called when a direct method is not found.
   *
   * The incoming typeUrl is for one of the existing sub types, but the method itself is defined to receive a super
   * type. Therefore we look up the method parameter to find out if one of its sub types matches the incoming typeUrl.
   */
  private def lookupMethodAcceptingSubType(inputTypeUrl: String): Option[MethodInvoker] = {
    methodInvokers.values.find { javaMethod =>
      //None could happen if the method is a delete handler
      val lastParam = javaMethod.method.getParameterTypes.lastOption
      if (lastParam.exists(_.getAnnotation(classOf[JsonSubTypes]) != null)) {
        lastParam.get.getAnnotation(classOf[JsonSubTypes]).value().exists { subType =>
          inputTypeUrl == serializer
            .contentTypeFor(subType.value()) //TODO requires more changes to be used with JsonMigration
        }
      } else false
    }
  }

//  def isSingleNameInvoker: Boolean = methodInvokers.size == 1

  def lookupInvoker(inputTypeUrl: String): Option[MethodInvoker] =
    methodInvokers
      .get(serializer.removeVersion(inputTypeUrl))
      .orElse(lookupMethodAcceptingSubType(inputTypeUrl))

  def getInvoker(inputTypeUrl: String): MethodInvoker =
    lookupInvoker(inputTypeUrl).getOrElse {
      throw new NoSuchElementException(
        s"Couldn't find any entry for typeUrl [$inputTypeUrl] in [${methodInvokers.view.mapValues(_.method.getName).mkString}].")
    }
}
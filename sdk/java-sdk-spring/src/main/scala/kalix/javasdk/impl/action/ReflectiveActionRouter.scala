/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.action

import akka.NotUsed
import akka.stream.javadsl.Source
import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.javasdk.JsonSupport
import kalix.javasdk.action.Action
import kalix.javasdk.action.MessageEnvelope
import kalix.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import kalix.javasdk.impl.CommandHandler
import kalix.javasdk.impl.InvocationContext
import kalix.javasdk.impl.reflection.Reflect

import scala.util.control.NonFatal

class ReflectiveActionRouter[A <: Action](
    action: A,
    commandHandlers: Map[String, CommandHandler],
    ignoreUnknown: Boolean)
    extends ActionRouter[A](action) {

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(
      commandName,
      throw new RuntimeException(
        s"no matching method for '$commandName' on [${action.getClass}], existing are [${commandHandlers.keySet
          .mkString(", ")}]"))

  override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Action.Effect[_] = {

    val commandHandler = commandHandlerLookup(commandName)

    val inputTypeUrl = message.payload().asInstanceOf[ScalaPbAny].typeUrl
    val scalaPbAnyCommand = message.payload().asInstanceOf[ScalaPbAny]
    if ((scalaPbAnyCommand.typeUrl.startsWith(
        JsonSupport.KALIX_JSON) || scalaPbAnyCommand.value.isEmpty) && commandHandler.isSingleNameInvoker) {
      // special cased component client calls, lets json commands trough all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val parameterTypes = methodInvoker.method.getParameterTypes
      val result =
        if (parameterTypes.isEmpty) methodInvoker.invoke(action)
        else if (parameterTypes.size > 1)
          throw new IllegalArgumentException(
            s"Handler for [${action.getClass}.$commandName] expects more than one parameter, not supported (parameter types: [${parameterTypes.mkString}]")
        else {
          // we used to dispatch based on the type, since that is how it works in protobuf for eventing
          // but here we have a concrete command name, and can pick up the expected serialized type from there
          val decodedParameter =
            try {
              JsonSupport.decodeJson(parameterTypes(0), scalaPbAnyCommand)
            } catch {
              case NonFatal(ex) =>
                throw new IllegalArgumentException(
                  s"Could not deserialize message for ${action.getClass}.${commandName}",
                  ex)
            }
          methodInvoker.invokeDirectly(action, decodedParameter.asInstanceOf[AnyRef])
        }
      result.asInstanceOf[Action.Effect[_]]
    } else {

      val invocationContext =
        InvocationContext(scalaPbAnyCommand, commandHandler.requestMessageDescriptor, message.metadata())

      // lookup ComponentClient
      val componentClients = Reflect.lookupComponentClientFields(action)

      componentClients.foreach(_.callMetadata = Some(message.metadata()))

      val methodInvoker = commandHandler.lookupInvoker(inputTypeUrl)
      methodInvoker match {
        case Some(invoker) =>
          inputTypeUrl match {
            case ProtobufEmptyTypeUrl =>
              invoker
                .invoke(action)
                .asInstanceOf[Action.Effect[_]]
            case _ =>
              invoker
                .invoke(action, invocationContext)
                .asInstanceOf[Action.Effect[_]]
          }
        case None if ignoreUnknown => ActionEffectImpl.Builder.ignore()
        case None =>
          throw new NoSuchElementException(
            s"Couldn't find any method with input type [$inputTypeUrl] in Action [$action].")
      }
    }
  }

  override def handleStreamedOut(
      commandName: String,
      message: MessageEnvelope[Any]): Source[Action.Effect[_], NotUsed] = {
    throw new UnsupportedOperationException("Stream out not supported")
  }

  override def handleStreamedIn(commandName: String, stream: Source[MessageEnvelope[Any], NotUsed]): Action.Effect[_] =
    throw new UnsupportedOperationException("Stream in calls are not supported")

  override def handleStreamed(
      commandName: String,
      stream: Source[MessageEnvelope[Any], NotUsed]): Source[Action.Effect[_], NotUsed] =
    throw new UnsupportedOperationException("Stream in calls are not supported")
}

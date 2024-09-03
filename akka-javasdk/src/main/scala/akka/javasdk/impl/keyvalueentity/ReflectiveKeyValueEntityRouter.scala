/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.InvocationContext
import akka.javasdk.keyvalueentity.CommandContext
import akka.javasdk.keyvalueentity.KeyValueEntity
import com.google.protobuf.any.{ Any => ScalaPbAny }

import java.lang.reflect.ParameterizedType
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ReflectiveKeyValueEntityRouter[S, E <: KeyValueEntity[S]](
    override protected val entity: E,
    commandHandlers: Map[String, CommandHandler])
    extends KeyValueEntityRouter[S, E](entity) {

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(commandName, throw new RuntimeException(s"no matching method for '$commandName'"))

  override protected def handleCommand(
      commandName: String,
      state: S,
      command: Any,
      commandContext: CommandContext): KeyValueEntity.Effect[_] = {

    _extractAndSetCurrentState(state)

    val commandHandler = commandHandlerLookup(commandName)
    val scalaPbAnyCommand = command.asInstanceOf[ScalaPbAny]

    if (scalaPbAnyCommand.typeUrl.startsWith(JsonSupport.JSON_TYPE_URL_PREFIX)) {
      // special cased component client calls, lets json commands trough all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val parameterTypes = methodInvoker.method.getParameterTypes
      val result =
        if (parameterTypes.isEmpty) methodInvoker.invoke(entity)
        else if (parameterTypes.size > 1)
          throw new IllegalArgumentException(
            s"Command handler for [${entity.getClass}.$commandName] expects more than one parameter, not supported (parameter types: [${parameterTypes.mkString}]")
        else {
          // we used to dispatch based on the type, since that is how it works in protobuf for eventing
          // but here we have a concrete command name, and can pick up the expected serialized type from there
          val decodedParameter =
            try {
              JsonSupport.decodeJson(parameterTypes(0), scalaPbAnyCommand)
            } catch {
              case NonFatal(ex) =>
                throw new IllegalArgumentException(
                  s"Could not deserialize message for ${entity.getClass}.${commandName}",
                  ex)
            }
          methodInvoker.invokeDirectly(entity, decodedParameter.asInstanceOf[AnyRef])
        }
      result.asInstanceOf[KeyValueEntity.Effect[_]]
    } else {
      val invocationContext =
        InvocationContext(scalaPbAnyCommand, commandHandler.requestMessageDescriptor, commandContext.metadata())

      val inputTypeUrl = command.asInstanceOf[ScalaPbAny].typeUrl

      commandHandler
        .getInvoker(inputTypeUrl)
        .invoke(entity, invocationContext)
        .asInstanceOf[KeyValueEntity.Effect[_]]
    }
  }

  private def _extractAndSetCurrentState(state: S): Unit = {
    val entityStateType: Class[S] =
      this.entity.getClass.getGenericSuperclass
        .asInstanceOf[ParameterizedType]
        .getActualTypeArguments
        .head
        .asInstanceOf[Class[S]]

    // the state: S received can either be of the entity "state" type (if coming from emptyState/memory)
    // or PB Any type (if coming from the proxy)
    state match {
      case s if s == null || state.getClass == entityStateType =>
        // note that we set the state even if null, this is needed in order to
        // be able to call currentState() later
        entity._internalSetCurrentState(s)
      case s =>
        val deserializedState =
          JsonSupport.decodeJson(entityStateType, ScalaPbAny.toJavaProto(s.asInstanceOf[ScalaPbAny]))
        entity._internalSetCurrentState(deserializedState)
    }
  }
}

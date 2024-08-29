/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.InvocationContext

import java.lang.reflect.ParameterizedType
import com.google.protobuf.any.{ Any => ScalaPbAny }
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.view.TableUpdater

/**
 * INTERNAL API
 */
@InternalApi
class ReflectiveViewRouter[S, V <: TableUpdater[S]](
    viewUpdater: V,
    commandHandlers: Map[String, CommandHandler],
    ignoreUnknown: Boolean)
    extends ViewRouter[S, V](viewUpdater) {

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(commandName, throw new RuntimeException(s"no matching method for '$commandName'"))

  override def handleUpdate(commandName: String, state: S, event: Any): TableUpdater.Effect[S] = {

    val viewStateType: Class[S] =
      updater.getClass.getGenericSuperclass
        .asInstanceOf[ParameterizedType]
        .getActualTypeArguments
        .head
        .asInstanceOf[Class[S]]

    // the state: S received can either be of the view "state" type (if coming from emptyState)
    // or PB Any type (if coming from the proxy)
    state match {
      case s if s == null || state.getClass == viewStateType =>
        // note that we set the state even if null, this is needed in order to
        // be able to call viewState() later
        viewUpdater._internalSetViewState(s)
      case s =>
        val deserializedState =
          JsonSupport.decodeJson(viewStateType, ScalaPbAny.toJavaProto(s.asInstanceOf[ScalaPbAny]))
        viewUpdater._internalSetViewState(deserializedState)
    }

    val commandHandler = commandHandlerLookup(commandName)

    val anyEvent = event.asInstanceOf[ScalaPbAny]
    val inputTypeUrl = anyEvent.typeUrl
    val methodInvoker = commandHandler.lookupInvoker(inputTypeUrl)

    methodInvoker match {
      case Some(invoker) =>
        inputTypeUrl match {
          case ProtobufEmptyTypeUrl =>
            invoker
              .invoke(viewUpdater)
              .asInstanceOf[TableUpdater.Effect[S]]
          case _ =>
            val context =
              InvocationContext(anyEvent, commandHandler.requestMessageDescriptor)
            invoker
              .invoke(viewUpdater, context)
              .asInstanceOf[TableUpdater.Effect[S]]
        }
      case None if ignoreUnknown => ViewEffectImpl.builder().ignore()
      case None =>
        throw new NoSuchElementException(
          s"Couldn't find any method with input type [$inputTypeUrl] in View [$updater].")
    }
  }

}

class ReflectiveViewMultiTableRouter(
    viewTables: Map[Class[TableUpdater[AnyRef]], TableUpdater[AnyRef]],
    commandHandlers: Map[String, CommandHandler])
    extends ViewMultiTableRouter {

  private val routers: Map[Class[_], ReflectiveViewRouter[Any, TableUpdater[Any]]] = viewTables.map {
    case (viewTableClass, viewTable) => viewTableClass -> createViewRouter(viewTableClass, viewTable)
  }

  private val commandRouters: Map[String, ReflectiveViewRouter[Any, TableUpdater[Any]]] = commandHandlers.flatMap {
    case (commandName, commandHandler) =>
      commandHandler.methodInvokers.values.headOption.flatMap { methodInvoker =>
        routers.get(methodInvoker.method.getDeclaringClass).map(commandName -> _)
      }
  }

  private def createViewRouter(
      updaterClass: Class[TableUpdater[AnyRef]],
      updater: TableUpdater[AnyRef]): ReflectiveViewRouter[Any, TableUpdater[Any]] = {
    val ignoreUnknown = ComponentDescriptorFactory.findIgnore(updaterClass)
    val tableCommandHandlers = commandHandlers.filter { case (_, commandHandler) =>
      commandHandler.methodInvokers.exists { case (_, methodInvoker) =>
        methodInvoker.method.getDeclaringClass eq updaterClass
      }
    }
    new ReflectiveViewRouter[Any, TableUpdater[Any]](
      updater.asInstanceOf[TableUpdater[Any]],
      tableCommandHandlers,
      ignoreUnknown)
  }

  override def viewRouter(commandName: String): ViewRouter[_, _] = {
    commandRouters.getOrElse(commandName, throw new RuntimeException(s"No view router for '$commandName'"))
  }
}

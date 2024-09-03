/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.annotation.InternalApi
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.view.TableUpdater
import akka.javasdk.view.View
import akka.javasdk.view.ViewContext
import com.google.protobuf.Descriptors

/**
 * INTERNAL API
 */
@InternalApi
final case class ViewProvider[V <: View](
    cls: Class[V],
    messageCodec: JsonMessageCodec,
    viewId: String,
    viewUpdaterFactory: ViewContext => Set[TableUpdater[AnyRef]]) {

  private val componentDescriptor = ComponentDescriptor.descriptorFor(cls, messageCodec)

  val serviceDescriptor: Descriptors.ServiceDescriptor = componentDescriptor.serviceDescriptor

  private def newRouter(context: ViewContext): ViewUpdateRouter = {
    val viewUpdaters = viewUpdaterFactory(context)
      .map { updater =>
        val anyRefUpdater: TableUpdater[AnyRef] = updater
        anyRefUpdater.getClass.asInstanceOf[Class[TableUpdater[AnyRef]]] -> anyRefUpdater
      }
      .toMap[Class[TableUpdater[AnyRef]], TableUpdater[AnyRef]]
    new ReflectiveViewMultiTableRouter(viewUpdaters, componentDescriptor.commandHandlers)
  }

  def newServiceInstance(): ViewService =
    new ViewService(Some(newRouter), serviceDescriptor, Array.empty, messageCodec, viewId)
}

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.view

import akka.annotation.InternalApi
import akka.platform.javasdk.impl.ComponentDescriptor
import akka.platform.javasdk.impl.JsonMessageCodec
import akka.platform.javasdk.impl.MessageCodec
import akka.platform.javasdk.view.TableUpdater
import akka.platform.javasdk.view.View
import akka.platform.javasdk.view.ViewContext
import akka.platform.javasdk.view.ViewOptions
import akka.platform.javasdk.view.ViewProvider
import com.google.protobuf.Descriptors

import java.util.Collections
import java.util.Optional

/**
 * INTERNAL API
 */
@InternalApi
final class ViewProviderImpl[V <: View](
    cls: Class[V],
    messageCodec: JsonMessageCodec,
    val viewId: String,
    viewUpdaterFactory: ViewContext => Set[TableUpdater[AnyRef]])
    extends ViewProvider {

  private val componentDescriptor = ComponentDescriptor.descriptorFor(cls, messageCodec)

  override val serviceDescriptor = componentDescriptor.serviceDescriptor

  override val options: ViewOptions = ViewOptionsImpl(Collections.emptySet())

  override def newRouter(context: ViewContext): ViewUpdateRouter = {
    val viewUpdaters = viewUpdaterFactory(context)
      .map { updater =>
        val anyRefUpdater: TableUpdater[AnyRef] = updater
        anyRefUpdater.getClass.asInstanceOf[Class[TableUpdater[AnyRef]]] -> anyRefUpdater
      }
      .toMap[Class[TableUpdater[AnyRef]], TableUpdater[AnyRef]]
    new ReflectiveViewMultiTableRouter(viewUpdaters, componentDescriptor.commandHandlers)
  }

  override def additionalDescriptors(): Array[Descriptors.FileDescriptor] = Array.empty

  override def alternativeCodec(): Optional[MessageCodec] = Optional.of(messageCodec)
}

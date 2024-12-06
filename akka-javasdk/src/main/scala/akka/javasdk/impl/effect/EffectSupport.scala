/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.effect

import akka.annotation.InternalApi
import akka.javasdk.impl.MetadataImpl
import com.google.protobuf.any.{ Any => ScalaPbAny }
import com.google.protobuf.{ Any => JavaPbAny }
import kalix.protocol.component

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object EffectSupport {

  def asProtocol(messageReply: MessageReplyImpl[_]): component.Reply = {
    val scalaPbAny =
      messageReply.message match {
        case pb: ScalaPbAny => pb
        case pb: JavaPbAny  => ScalaPbAny.fromJavaProto(pb)
        case other          => throw new IllegalStateException(s"Expected PbAny, but was [${other.getClass.getName}]")
      }

    component.Reply(Some(scalaPbAny), MetadataImpl.toProtocol(messageReply.metadata))
  }

}

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

  def asProtocol(messageReply: MessageReplyImpl[JavaPbAny]): component.Reply =
    component.Reply(
      Some(ScalaPbAny.fromJavaProto(messageReply.message)),
      MetadataImpl.toProtocol(messageReply.metadata))

}

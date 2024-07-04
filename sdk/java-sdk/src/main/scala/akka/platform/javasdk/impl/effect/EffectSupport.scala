/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.effect

import com.google.protobuf.any.{ Any => ScalaPbAny }
import com.google.protobuf.{ Any => JavaPbAny }
import akka.platform.javasdk.impl.MetadataImpl
import kalix.protocol.component

object EffectSupport {

  def asProtocol(messageReply: MessageReplyImpl[JavaPbAny]): component.Reply =
    component.Reply(
      Some(ScalaPbAny.fromJavaProto(messageReply.message)),
      MetadataImpl.toProtocol(messageReply.metadata))

}

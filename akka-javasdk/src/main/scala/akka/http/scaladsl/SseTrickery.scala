/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.http.scaladsl

import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.util.ByteString

// FIXME drop once we have Akka HTTP with public encode out
object SseTrickery {
  def encode(event: ServerSentEvent): ByteString = event.encode

}

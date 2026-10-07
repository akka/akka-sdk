/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util.Optional

import scala.jdk.OptionConverters.RichOption

import akka.annotation.InternalApi
import akka.javasdk.CallerSpiffeContext
import akka.javasdk.SpiffeContext
import akka.runtime.sdk.spi.SpiSpiffeContext

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object SpiffeContextImpl {

  def fromSpi(spi: SpiSpiffeContext): SpiffeContext =
    new SpiffeContext {
      override def getSpiffeId: String = spi.spiffeId
      override def getCallerContext: Optional[CallerSpiffeContext] =
        spi.callerContext
          .map[CallerSpiffeContext](c =>
            new CallerSpiffeContext {
              override def getSpiffeId: String = c.spiffeId
            })
          .toJava
    }

  def fromSpiOpt(spi: Option[SpiSpiffeContext]): Optional[SpiffeContext] =
    spi.map(fromSpi).toJava
}

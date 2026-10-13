/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object ForwardCompat {

  // A runtime that is newer than this SDK can hand over a value that this SDK does not know. Such a value
  // reads as `unknown`. A `null` also reads as `unknown`, because it fails the match too. The matches have no
  // default case, so that the compiler reports a value that the SPI adds when the SDK is built against that
  // runtime.
  def knownOr[T](unknown: T)(known: => T): T =
    try known
    catch { case _: MatchError => unknown }
}

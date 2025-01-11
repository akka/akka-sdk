/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.annotations.Acl

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AclDescriptorFactory {

  val invalidAnnotationUsage: String =
    "Invalid annotation usage. Matcher has both 'principal' and 'service' defined. " +
    "Only one is allowed."

  def validateMatcher(matcher: Acl.Matcher): Unit = {
    if (matcher.principal() != Acl.Principal.UNSPECIFIED && matcher.service().nonEmpty)
      throw new IllegalArgumentException(invalidAnnotationUsage)
  }

}

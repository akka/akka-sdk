/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util
import java.util.Optional

import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.jdk.CollectionConverters.IterableHasAsJava

import akka.annotation.InternalApi
import akka.javasdk.Principal
import akka.javasdk.Principals

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final case class PrincipalsImpl(source: Option[String], service: Option[String]) extends Principals {

  override def isInternet: Boolean = source.contains("internet")
  override def isSelf: Boolean = source.contains("self")
  override def isBackoffice: Boolean = source.contains("backoffice")

  override def isLocalService(name: String): Boolean = service.contains(name)

  override def isAnyLocalService: Boolean = service.nonEmpty

  override def getLocalService: Optional[String] = service.asJava
  override def get(): util.Collection[Principal] = (source.collect {
    case "internet"   => Principal.INTERNET
    case "self"       => Principal.SELF
    case "backoffice" => Principal.BACKOFFICE
  } ++ service.map(Principal.localService)).asJavaCollection
}

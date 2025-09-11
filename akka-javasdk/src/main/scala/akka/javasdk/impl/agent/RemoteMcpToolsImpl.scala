/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util
import java.util.function.Predicate
import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpHeader
import akka.javasdk.agent.RemoteMcpTools

import java.time.{ Duration => JDuration }
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.JavaDurationOps

/**
 * INTERNAL API
 */
@InternalApi
final case class RemoteMcpToolsImpl(
    serverUri: String,
    toolNameFilter: Option[Predicate[String]],
    interceptor: Option[RemoteMcpTools.ToolInterceptor],
    additionalClientHeaders: Seq[HttpHeader],
    timeout: FiniteDuration)
    extends RemoteMcpTools {

  def this(serverUri: String) = this(serverUri, None, None, Seq.empty, Duration.Zero)

  override def withToolNameFilter(toolNameFilter: Predicate[String]): RemoteMcpTools =
    copy(toolNameFilter = Some(toolNameFilter))

  override def withAllowedToolNames(allowedToolNames: util.Set[String]): RemoteMcpTools =
    copy(toolNameFilter = Some(allowedToolNames.contains))

  override def withAllowedToolNames(allowedToolName: String, moreAllowedToolNames: String*): RemoteMcpTools = {
    val names = (allowedToolName +: moreAllowedToolNames).toSet
    copy(toolNameFilter = Some(names.contains _))
  }

  override def withToolInterceptor(interceptor: RemoteMcpTools.ToolInterceptor): RemoteMcpTools =
    copy(interceptor = Some(interceptor))

  override def addClientHeader(header: HttpHeader): RemoteMcpTools =
    copy(additionalClientHeaders = additionalClientHeaders :+ header)

  override def withTimeout(timeout: JDuration): RemoteMcpTools =
    copy(timeout = timeout.toScala)
}

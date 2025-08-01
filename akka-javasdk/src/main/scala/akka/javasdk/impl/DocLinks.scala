/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object DocLinks {

  private val baseUrl = s"https://doc.akka.io/"

  val MessageBrokersPage = "operations/message-brokers.html"

  private[javasdk] val errorCodes = Map(
    "AK-00112" -> "java/views.html#changing",
    "AK-00406" -> MessageBrokersPage,
    "AK-00415" -> "java/consuming-producing.html#consume-from-event-sourced-entity",
    "AK-00416" -> MessageBrokersPage,
    "AK-01206" -> "java/agents.html#configuring_tool_call_limits")

  // fallback if isn't defined in errorCodes
  private val errorCodeCategories = Map(
    "AK-001" -> "java/views.html",
    "AK-002" -> "java/key-value-entities.html",
    "AK-003" -> "java/event-sourced-entities.html",
    "AK-004" -> "java/consuming-producing.html",
    "AK-007" -> "java/using-jwts.html",
    "AK-008" -> "java/timed-actions.html",
    "AK-009" -> "java/access-control.html",
    "AK-010" -> "java/workflows.html",
    "AK-012" -> "java/agents.html")

  def forErrorCode(code: String): Option[String] = {
    val page = errorCodes.get(code).orElse(errorCodeCategories.get(code.take("AK-000".length)))
    page.map(p => s"$baseUrl$p")
  }
}

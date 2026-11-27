/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.testkit.MockA2aAgent
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/**
 * INTERNAL API
 *
 * Serves the agent card and the JSON-RPC endpoint of mocked remote A2A agents on a local port.
 */
@InternalApi
final class MockedA2aAgentsServer private (server: HttpServer, agents: Seq[MockA2aAgent]) {
  private val mapper = new ObjectMapper()
  private val port = server.getAddress.getPort
  private def baseUrl(name: String) = s"http://localhost:$port/$name"

  /**
   * Application configuration that points each agent name at its mock. It replaces the credentials and the allowed
   * hosts that the service has for the agent, as the mock is plain HTTP and has no use for them.
   */
  val config: Config = {
    def key(a: MockA2aAgent, setting: String) = s"akka.javasdk.agent.a2a-client.agents.\"${a.name}\".$setting"
    ConfigFactory.parseMap(
      agents
        .flatMap { a =>
          Seq(
            key(a, "card-url") -> s"${baseUrl(a.name)}/card",
            key(a, "auth.type") -> "none",
            key(a, "allowed-hosts") -> java.util.List.of[String]())
        }
        .toMap
        .asJava)
  }

  private def start(): Unit = {
    agents.foreach { agent =>
      server.createContext(s"/${agent.name}/card", exchange => handle(exchange)(reply(exchange, 200, card(agent))))
      server.createContext(s"/${agent.name}/rpc", exchange => handle(exchange)(rpc(exchange, agent)))
    }
    server.setExecutor(Executors.newCachedThreadPool())
    server.start()
  }

  def stop(): Unit = {
    server.stop(0)
    server.getExecutor match {
      case e: java.util.concurrent.ExecutorService => e.shutdownNow()
      case _                                       =>
    }
  }

  private def handle(exchange: HttpExchange)(body: => Unit): Unit =
    try body
    catch {
      case NonFatal(e) => reply(exchange, 500, mapper.createObjectNode().put("error", String.valueOf(e.getMessage)))
    } finally exchange.close()

  private def card(agent: MockA2aAgent): ObjectNode = {
    val node = mapper.createObjectNode()
    node.put("name", agent.name)
    node.put("description", s"Mock of ${agent.name}")
    node.put("version", "1.0.0")
    val anInterface = node.putArray("supportedInterfaces").addObject()
    anInterface.put("url", s"${baseUrl(agent.name)}/rpc")
    anInterface.put("protocolBinding", "JSONRPC")
    anInterface.put("protocolVersion", "1.0")
    node.putObject("capabilities")
    node.putArray("defaultInputModes").add("text/plain")
    node.putArray("defaultOutputModes").add("text/plain")
    val skills = node.putArray("skills")
    agent.skills.asScala.foreach { s =>
      val skill = skills.addObject()
      skill.put("id", s.id)
      skill.put("name", s.id)
      skill.put("description", Option(s.description).getOrElse(""))
      skill.putArray("tags")
    }
    node
  }

  private def textMessage(node: ObjectNode, role: String, text: String): Unit = {
    node.put("messageId", java.util.UUID.randomUUID().toString)
    node.put("role", role)
    node.putArray("parts").addObject().put("text", text)
  }

  // Like an A2A 1.0 server: the version header is required, and a request without it is read as version 0.3.
  private def rpc(exchange: HttpExchange, agent: MockA2aAgent): Unit = {
    val request = mapper.readTree(exchange.getRequestBody.readAllBytes())
    val message = request.path("params").path("message")
    val skillId = message.path("metadata").path("skillId").asText("")
    val text = message.path("parts").elements().asScala.map(_.path("text").asText("")).mkString("\n")
    val version = Option(exchange.getRequestHeaders.getFirst("A2A-Version")).getOrElse("")

    val response = mapper.createObjectNode()
    response.put("jsonrpc", "2.0")
    response.set[JsonNode]("id", request.path("id"))
    def error(code: Int, text: String): Unit = {
      val e = response.putObject("error")
      e.put("code", code)
      e.put("message", text)
    }
    if (version != "1.0" && !version.startsWith("1."))
      error(
        -32009,
        s"A2A version '${if (version.isEmpty) "0.3" else version}' is not supported by this mock. Expected version '1.0'.")
    else if (request.path("method").asText("") != "SendMessage") error(-32601, "Method not found")
    else
      agent.skills.asScala.find(_.id == skillId) match {
        case None => error(-32602, s"Mock agent [${agent.name}] has no skill [$skillId]")
        case Some(skill) =>
          try {
            textMessage(response.putObject("result").putObject("message"), "ROLE_AGENT", skill.handler.apply(text))
          } catch {
            case NonFatal(e) =>
              response.remove("result")
              val task = response.putObject("result").putObject("task")
              task.put("id", java.util.UUID.randomUUID().toString)
              task.put("contextId", java.util.UUID.randomUUID().toString)
              val status = task.putObject("status")
              status.put("state", "TASK_STATE_FAILED")
              textMessage(status.putObject("message"), "ROLE_AGENT", String.valueOf(e.getMessage))
          }
      }
    reply(exchange, 200, response)
  }

  private def reply(exchange: HttpExchange, status: Int, body: JsonNode): Unit = {
    val bytes = mapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}

@InternalApi
object MockedA2aAgentsServer {
  def start(agents: java.util.List[MockA2aAgent]): MockedA2aAgentsServer = {
    val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, 0), 0)
    val mocks = new MockedA2aAgentsServer(server, agents.asScala.toSeq)
    try mocks.start()
    catch {
      case NonFatal(e) =>
        mocks.stop()
        throw e
    }
    mocks
  }
}

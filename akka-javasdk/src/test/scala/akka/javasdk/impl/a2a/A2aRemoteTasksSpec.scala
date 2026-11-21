/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.net.InetAddress
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.javasdk.a2a._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class A2aRemoteTasksSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with LogCapturing {

  private val at = Instant.parse("2026-11-20T10:00:00Z")

  private def status(state: A2aTaskState, timestamp: Instant, message: String = null) =
    new A2aTaskStatus(state, if (message == null) null else A2aMessage.user(message).withMessageId(message), timestamp)

  private def task(state: A2aTaskState, artifacts: A2aArtifact*) =
    new A2aTask("t1", "c1", status(state, at), artifacts.asJava, null, null)

  private def artifact(id: String, text: String) =
    new A2aArtifact(id, null, null, java.util.List.of(A2aPart.text(text)), null, null)

  "The updates of a remote task" should {

    "be each artifact and then the status" in {
      val updates = A2aRemoteTasks.updatesOf(task(A2aTaskState.COMPLETED, artifact("a1", "done")))
      updates.map(_._2.getClass) shouldBe Seq(classOf[A2aTaskArtifactUpdate], classOf[A2aTaskStatusUpdate])
    }

    "have the same key when the task has not changed, and another when it has" in {
      val first = A2aRemoteTasks.updatesOf(task(A2aTaskState.WORKING)).map(_._1)
      A2aRemoteTasks.updatesOf(task(A2aTaskState.WORKING)).map(_._1) shouldBe first
      A2aRemoteTasks.updatesOf(task(A2aTaskState.COMPLETED)).map(_._1) should not be first
    }

    "tell statuses apart by timestamp and message, and artifacts by content" in {
      def key(update: A2aTaskUpdate) = A2aRemoteTasks.keyOf(update)
      def statusUpdate(s: A2aTaskStatus) = new A2aTaskStatusUpdate("t1", "c1", s, null)
      def artifactUpdate(a: A2aArtifact) = new A2aTaskArtifactUpdate("t1", "c1", a, false, true, null)
      key(statusUpdate(status(A2aTaskState.WORKING, at))) should not be key(
        statusUpdate(status(A2aTaskState.WORKING, at.plusSeconds(1))))
      key(statusUpdate(status(A2aTaskState.WORKING, at, "m1"))) should not be key(
        statusUpdate(status(A2aTaskState.WORKING, at, "m2")))
      key(artifactUpdate(artifact("a1", "x"))) shouldBe key(artifactUpdate(artifact("a1", "x")))
      key(artifactUpdate(artifact("a1", "x"))) should not be key(artifactUpdate(artifact("a1", "y")))
    }

    "settle when the task ends or needs input" in {
      A2aRemoteTasks.settles(task(A2aTaskState.WORKING)) shouldBe false
      A2aRemoteTasks.settles(task(A2aTaskState.INPUT_REQUIRED)) shouldBe true
      A2aRemoteTasks.settles(task(A2aTaskState.FAILED)) shouldBe true
    }

    "read as the update type that a workflow command declares" in {
      val mapper = akka.javasdk.JsonSupport.getObjectMapper
      val statusUpdate = new A2aTaskStatusUpdate("t1", "c1", status(A2aTaskState.WORKING, at), null)
      val artifactUpdate = new A2aTaskArtifactUpdate("t1", "c1", artifact("a1", "x"), true, false, null)
      mapper.readValue(mapper.writeValueAsBytes(statusUpdate), classOf[A2aTaskUpdate]) shouldBe statusUpdate
      mapper.readValue(mapper.writeValueAsBytes(artifactUpdate), classOf[A2aTaskUpdate]) shouldBe artifactUpdate
    }
  }

  "The push notification token" should {

    "match only the token whose hash is stored" in {
      val token = A2aRemoteTasks.newToken()
      val hash = A2aRemoteTasks.tokenHash(token)
      A2aRemoteTasks.tokenMatches(Some(hash), Some(token)) shouldBe true
      A2aRemoteTasks.tokenMatches(Some(hash), Some(token + "x")) shouldBe false
      A2aRemoteTasks.tokenMatches(Some(hash), None) shouldBe false
      A2aRemoteTasks.tokenMatches(None, Some(token)) shouldBe false
      A2aRemoteTasks.tokenMatches(None, None) shouldBe false
      A2aRemoteTasks.tokenMatches(Some(A2aRemoteTasks.tokenHash("")), Some("")) shouldBe false
    }

    "be new each time" in {
      A2aRemoteTasks.newToken() should not be A2aRemoteTasks.newToken()
    }
  }

  "A push notification delivery" should {

    "connect to the checked address and keep the host name in the request" in {
      val hosts = new ConcurrentLinkedQueue[String]()
      val binding = Await.result(
        Http()
          .newServerAt("127.0.0.1", 0)
          .bindSync { (request: HttpRequest) =>
            hosts.add(request.uri.authority.host.address())
            request.discardEntityBytes()
            HttpResponse()
          },
        10.seconds)
      try {
        val port = binding.localAddress.getPort
        val delivery =
          new A2aPushDeliveryWorkflow.State(s"http://webhook.invalid:$port/hook", "tok", null, "{}", 0)

        A2aPushDeliveryWorkflow.post(
          system.classicSystem,
          delivery,
          java.util.List.of(InetAddress.getByName("127.0.0.1"))) shouldBe true
        hosts.asScala.toSeq shouldBe Seq("webhook.invalid")

        // Without a checked address the name is resolved when connecting, and does not resolve
        A2aPushDeliveryWorkflow.post(system.classicSystem, delivery, java.util.List.of()) shouldBe false
      } finally binding.unbind()
    }

    "close its connection after the response" in {
      val server = new java.net.ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
      val closedByClient = scala.concurrent.Promise[Boolean]()
      val serverThread = new Thread(() =>
        try {
          val socket = server.accept()
          try {
            val in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream))
            var contentLength = 0
            var line = in.readLine()
            while (line != null && line.nonEmpty) {
              if (line.toLowerCase.startsWith("content-length:")) contentLength = line.substring(15).trim.toInt
              line = in.readLine()
            }
            (1 to contentLength).foreach(_ => in.read())
            val out = socket.getOutputStream
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes("US-ASCII"))
            out.flush()
            // A pooled connection would stay open here until its idle timeout
            socket.setSoTimeout(5000)
            closedByClient.trySuccess(in.read() == -1)
          } finally socket.close()
        } catch {
          case e: java.net.SocketTimeoutException => closedByClient.trySuccess(false)
          case e: Throwable                       => closedByClient.tryFailure(e)
        })
      serverThread.start()
      try {
        val delivery =
          new A2aPushDeliveryWorkflow.State(s"http://webhook.invalid:${server.getLocalPort}/hook", "tok", null, "{}", 0)
        A2aPushDeliveryWorkflow.post(
          system.classicSystem,
          delivery,
          java.util.List.of(InetAddress.getByName("127.0.0.1"))) shouldBe true
        Await.result(closedByClient.future, 10.seconds) shouldBe true
      } finally server.close()
    }
  }
}

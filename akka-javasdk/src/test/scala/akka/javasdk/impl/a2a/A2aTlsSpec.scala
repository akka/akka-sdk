/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.nio.file.Files

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.a2a.A2aClientException
import akka.javasdk.a2a.RemoteA2aAgentClient
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class A2aTlsSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with LogCapturing {

  private val pki = TestPki.generate()

  // Requires a client certificate that the test authority signed
  private val mutualTlsServer = new FakeA2aServer
  private val mutualTlsBinding = Await.result(
    mutualTlsServer.start(
      Some(pki.serverContext(pki.serverCertificate, pki.serverKey)),
      requireClientCertificate = true),
    10.seconds)

  private val wrongHostServer = new FakeA2aServer
  private val wrongHostBinding =
    Await.result(wrongHostServer.start(Some(pki.serverContext(pki.wrongHostCertificate, pki.wrongHostKey))), 10.seconds)

  override protected def afterAll(): Unit = {
    mutualTlsBinding.unbind()
    wrongHostBinding.unbind()
    super.afterAll()
  }

  private def client(cardUrl: String, settings: (String, String)*): RemoteA2aAgentClient = {
    val config = ConfigFactory
      .parseMap(
        (("card-url" -> cardUrl) +: settings).map { case (k, v) => s"remote-agents.agent.$k" -> v }.toMap.asJava)
      .withFallback(system.settings.config.getConfig("akka.javasdk.a2a"))
    A2aClientImpl(new A2aRemoteAgents(system, config), None, None).forAgent("agent")
  }

  private def ca = "ca-bundle" -> pki.caCertificate
  private def clientCert = Seq("client-certificate" -> pki.clientCertificate, "client-key" -> pki.clientKey)

  "TLS to a remote agent" should {

    "succeed with the CA bundle and a client certificate" in {
      client(mutualTlsServer.cardUrl, ca +: clientCert: _*).card().name() shouldBe "fake"
    }

    "call the agent over the verified connection" in {
      val c = client(mutualTlsServer.cardUrl, ca +: clientCert: _*)
      c.getTask("task-1").id() shouldBe "task-1"
    }

    "read the CA bundle, certificate and key from files" in {
      val dir = Files.createTempDirectory("a2a-pem-files")
      val caFile = Files.writeString(dir.resolve("ca.pem"), pki.caCertificate)
      val certFile = Files.writeString(dir.resolve("client.pem"), pki.clientCertificate)
      val keyFile = Files.writeString(dir.resolve("client.key"), pki.clientKey)
      client(
        mutualTlsServer.cardUrl,
        "ca-bundle" -> caFile.toString,
        "client-certificate" -> certFile.toString,
        "client-key" -> keyFile.toString).card().name() shouldBe "fake"
    }

    "fail without a client certificate" in {
      val e = intercept[A2aClientException](client(mutualTlsServer.cardUrl, ca).card())
      e.getMessage should include("certificate_required")
    }

    "fail without the CA bundle, because the server certificate is not trusted" in {
      intercept[A2aClientException](client(mutualTlsServer.cardUrl, clientCert: _*).card()).getMessage should include(
        "PKIX path building failed")
    }

    "fail with a CA bundle of another authority" in {
      intercept[A2aClientException](
        client(mutualTlsServer.cardUrl, ("ca-bundle" -> pki.otherCaCertificate) +: clientCert: _*)
          .card()).getMessage should include("PKIX path building failed")
    }

    "fail when the server certificate is for another host name" in {
      intercept[A2aClientException](client(wrongHostServer.cardUrl, ca).card()).getMessage should include(
        "No subject alternative DNS name")
    }

    "reject a client certificate with no key at configuration time" in {
      intercept[IllegalArgumentException](
        client(mutualTlsServer.cardUrl, ca, "client-certificate" -> pki.clientCertificate))
    }

    "reject a client key that is not PKCS#8" in {
      val rsaKey = "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----"
      intercept[IllegalArgumentException](
        client(
          mutualTlsServer.cardUrl,
          ca,
          "client-certificate" -> pki.clientCertificate,
          "client-key" -> rsaKey)).getMessage should include("PKCS#8")
    }

    "name the setting, and not print the key, when a client key has no PEM header" in {
      val body = pki.clientKey.linesIterator.filterNot(_.startsWith("-----")).mkString
      val e = intercept[IllegalArgumentException](
        client(mutualTlsServer.cardUrl, ca, "client-certificate" -> pki.clientCertificate, "client-key" -> body))
      e.getMessage should include("remote-agents.agent.client-key")
      (e.getMessage should not).include(body.take(40))
      e.getCause shouldBe null
    }

    "reject a CA bundle that holds no certificate" in {
      intercept[IllegalArgumentException](client(mutualTlsServer.cardUrl, "ca-bundle" -> "-----BEGIN NOTHING-----\nx"))
    }
  }
}

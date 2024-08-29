/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.javasdk.impl.AclDescriptorFactory
import akka.javasdk.impl.DiscoveryImpl
import com.typesafe.config.ConfigFactory
import kalix.protocol.discovery.ProxyInfo
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DiscoverySpec extends AnyWordSpec with Matchers with ScalaFutures {

  "Discovery" should {

    val emptyAcl = AclDescriptorFactory.buildAclFileDescriptor(classOf[Nothing])

    "pass along env by default" in {
      var system: ActorSystem[Nothing] = null
      try {
        system = ActorSystem[Nothing](Behaviors.empty[Nothing], "DiscoverySpec1")
        val discovery = new DiscoveryImpl(system.classicSystem, Map.empty, emptyAcl, "test", None)
        val result = discovery.discover(ProxyInfo()).futureValue
        result.getServiceInfo.env should not be empty
      } finally {
        system.terminate()
      }
    }

    "pass along only allowed names if configured" in {
      var system: ActorSystem[Nothing] = null
      try {
        system = ActorSystem[Nothing](
          Behaviors.empty[Nothing],
          "DiscoverySpec2",
          ConfigFactory.parseString("""
              |akka.javasdk.discovery.pass-along-env-all = false
              |akka.javasdk.discovery.pass-along-env-allow = ["HOME"]""".stripMargin))
        val discovery = new DiscoveryImpl(system.classicSystem, Map.empty, emptyAcl, "test", None)
        val result = discovery.discover(ProxyInfo()).futureValue
        result.getServiceInfo.env should have size 1
      } finally {
        system.terminate()
      }
    }

    "pass along nothing if no names allowed" in {
      var system: ActorSystem[Nothing] = null
      try {
        system = ActorSystem[Nothing](
          Behaviors.empty[Nothing],
          "DiscoverySpec2",
          ConfigFactory.parseString("""
              |akka.javasdk.discovery.pass-along-env-all = false
              |akka.javasdk.discovery.pass-along-env-allow = []
              |""".stripMargin))
        val discovery = new DiscoveryImpl(system.classicSystem, Map.empty, emptyAcl, "test", None)
        val result = discovery.discover(ProxyInfo()).futureValue
        result.getServiceInfo.env should be(empty)
      } finally {
        system.terminate()
      }
    }

  }

}

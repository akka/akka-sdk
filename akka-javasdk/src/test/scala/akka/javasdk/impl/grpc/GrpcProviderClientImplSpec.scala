/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.grpc

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GrpcProviderClientImplSpec extends AnyWordSpec with Matchers {

  "The gRPC client config" should {

    "filter out unsupported config" in {
      val result = GrpcClientProviderImpl.serviceConfigFor(
        "one.example.com",
        ConfigFactory.parseString("""
          "one.example.com" {
            host = "example.com"
            port = 8080
            use-tls = false
            service-discovery {
              mechanism = "custom"
            }
            load-balancing-policy = "round_robin"
            deadline = 5s
            override-authority = "aargh"
            user-agent = "My Service"
            trusted = "some/classpath/"
            ssl-provider = "openssl"
            connection-attempts = 5
            eager-connection = on
          }"""))

      result should ===(ConfigFactory.parseString("""
          "one.example.com" {
            host = "example.com"
            port = 8080
            use-tls = false
          }"""))
    }

    "should make an empty block if config missing" in {
      val result = GrpcClientProviderImpl.serviceConfigFor(
        "one.example.com",
        ConfigFactory.parseString("""
          "some-other.example.com" {
            host = "other.example.com"
            port = 9000
            use-tls = true
          }
        """))

      result.getConfig(""""one.example.com"""").isEmpty shouldBe true
    }

    "works for simple service name as well" in {
      // for dev/test mode
      val validConfig = ConfigFactory.parseString("""
          some-service {
            host = "other.example.com"
            port = 9000
            use-tls = true
          }
        """)
      val result = GrpcClientProviderImpl.serviceConfigFor("some-service", validConfig)

      result shouldEqual validConfig
    }

  }

}

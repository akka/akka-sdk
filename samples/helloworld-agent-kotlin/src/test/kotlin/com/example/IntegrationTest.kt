package com.example

import akka.javasdk.testkit.TestKit
import akka.javasdk.testkit.TestKitSupport
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Test

/**
 * This is a skeleton for implementing integration tests for an Akka application built with the Akka
 * SDK.
 *
 * It interacts with the components of the application using a componentClient (already
 * configured and provided automatically through injection).
 */
class IntegrationTest : TestKitSupport() {

    override fun testKitSettings(): TestKit.Settings {
        // Bootstrap will check if key exists when running integration tests.
        // We don't need a real one though.
        return TestKit.Settings.DEFAULT.withAdditionalConfig(
            ConfigFactory.parseString("akka.javasdk.agent.openai.api-key=fake-key")
        )
    }

    @Test
    fun test() {
        // implement your integration tests here by calling your
        // components by using the `componentClient`
    }
}
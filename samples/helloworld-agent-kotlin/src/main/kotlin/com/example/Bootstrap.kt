package com.example

import akka.javasdk.ServiceSetup
import akka.javasdk.annotations.Setup
import com.typesafe.config.Config

@Setup
class Bootstrap(config: Config) : ServiceSetup {
    init {
        if (config.getString("akka.javasdk.agent.model-provider") == "openai" &&
            config.getString("akka.javasdk.agent.openai.api-key").isBlank()) {
            throw IllegalStateException(
                "No API keys found. Make sure you have OPENAI_API_KEY defined as environment variable, or change the model provider configuration in application.conf to use a different LLM."
            )
        }
    }
}
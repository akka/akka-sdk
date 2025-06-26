/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ModelProviderSpec extends AnyWordSpec with Matchers {

  private val defaultConfig = ConfigFactory.load()

  "The model providers" should {
    "load defaults from config for anthropic" in {
      ModelProvider.Anthropic.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.anthropic"))
    }
    "load defaults from config for googleai-gemini" in {
      ModelProvider.GoogleAIGemini.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.googleai-gemini"))
    }
    "load defaults from config for hugging-face" in {
      ModelProvider.HuggingFace.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.hugging-face"))
    }
    "load defaults from config for local-ai" in {
      ModelProvider.LocalAI.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.local-ai"))
    }
    "load defaults from config for ollama" in {
      ModelProvider.Ollama.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.ollama"))
    }
    "load defaults from config for openai" in {
      ModelProvider.OpenAi.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.openai"))
    }
  }

}

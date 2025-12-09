/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.javasdk.agent.ModelProvider
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object ModelProviderSpec {
  private val config = ConfigFactory.load(ConfigFactory.parseString(s"""
    akka.javasdk {
      agent {
        model-provider = openai

        openai {
          model-name = "gpt-4o-mini"
          temperature = 0.5
        }

        anthropic {
          model-name = "claude-instant-1"
          temperature = 0.6
        }

        gpt-o3 = $${akka.javasdk.agent.openai}
        gpt-o3 {
          model-name = "o3"
          max-completion-tokens = 200000
        }
      }
    }

    gemini = $${akka.javasdk.agent.googleai-gemini}
    gemini {
      model-name = "gemini-2.5-flash"
    }
    """))
}

class ModelProviderSpec extends AnyWordSpec with Matchers {
  import ModelProviderSpec.config

  private val defaultConfig = ConfigFactory.load()

  "The model providers" should {
    "load defaults from config for anthropic" in {
      val m = ModelProvider.Anthropic.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.anthropic"))
      m shouldBe ModelProvider.anthropic()
    }
    "load defaults from config for googleai-gemini" in {
      val m = ModelProvider.GoogleAIGemini.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.googleai-gemini"))
      m shouldBe ModelProvider.googleAiGemini()
    }
    "load defaults from config for hugging-face" in {
      val m = ModelProvider.HuggingFace.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.hugging-face"))
      m shouldBe ModelProvider.huggingFace()
    }
    "load defaults from config for local-ai" in {
      val m = ModelProvider.LocalAI.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.local-ai"))
      m shouldBe ModelProvider.localAI()
    }
    "load defaults from config for ollama" in {
      val m = ModelProvider.Ollama.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.ollama"))
      m shouldBe ModelProvider.ollama()
    }
    "load defaults from config for openai" in {
      val m = ModelProvider.OpenAi.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.openai"))
      m shouldBe ModelProvider.openAi()
    }

    "load from model-provider in config" in {
      val m = AgentImpl.modelProviderFromConfig(config, "", "myagent")
      m shouldBe ModelProvider.openAi().withModelName("gpt-4o-mini").withTemperature(0.5)
    }

    "load from config, which exists in reference.conf" in {
      val m = AgentImpl.modelProviderFromConfig(config, "anthropic", "myagent")
      m shouldBe ModelProvider.anthropic().withModelName("claude-instant-1").withTemperature(0.6)
      // or full path
      val m2 = AgentImpl.modelProviderFromConfig(config, "akka.javasdk.agent.anthropic", "myagent")
      m2 shouldBe m
    }

    "load from config, which doesn't exists in reference.conf" in {
      val m = AgentImpl.modelProviderFromConfig(config, "gpt-o3", "myagent")
      m shouldBe ModelProvider.openAi().withModelName("o3").withTemperature(0.5).withMaxCompletionTokens(200000)
      // or full path
      val m2 = AgentImpl.modelProviderFromConfig(config, "akka.javasdk.agent.gpt-o3", "myagent")
      m2 shouldBe m
    }

    "load openai from config, which exists in reference.conf" in {
      val m: ModelProvider.OpenAi = ModelProvider.OpenAi.fromConfig(config.getConfig("akka.javasdk.agent.openai"))
      m shouldBe ModelProvider.openAi().withModelName("gpt-4o-mini").withTemperature(0.5)
    }

    "load openai from config, which doesn't exists in reference.conf" in {
      val m: ModelProvider.OpenAi = ModelProvider.OpenAi.fromConfig(config.getConfig("akka.javasdk.agent.gpt-o3"))
      m shouldBe ModelProvider.openAi().withModelName("o3").withTemperature(0.5).withMaxCompletionTokens(200000)
    }

    "load gemini from config, which doesn't exists in reference.conf" in {
      val m: ModelProvider.GoogleAIGemini =
        ModelProvider.GoogleAIGemini.fromConfig(config.getConfig("gemini"))
      m shouldBe ModelProvider.googleAiGemini().withModelName("gemini-2.5-flash")
    }

    "load defaults from config for bedrock" in {
      val m = ModelProvider.Bedrock.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.bedrock"))
      m shouldBe ModelProvider.bedrock()
    }

  }

}

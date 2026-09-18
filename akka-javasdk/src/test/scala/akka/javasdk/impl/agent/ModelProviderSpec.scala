/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.javadsl.model.HttpHeader
import akka.http.javadsl.model.headers.RawHeader
import akka.javasdk.agent.ModelProvider
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

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

        gateway-openai = $${akka.javasdk.agent.openai}
        gateway-openai {
          additional-model-request-headers = ["Authorization:Bearer configured-token"]
        }

        anonymous-openai = $${akka.javasdk.agent.openai}
        anonymous-openai {
          additional-model-request-headers = ["Authorization:Bearer configured-token"]
          identity-headers = off
        }
      }
    }

    gemini = $${akka.javasdk.agent.googleai-gemini}
    gemini {
      model-name = "gemini-2.5-flash"
    }
    """))

  private val identityHeadersOffConfig =
    ConfigFactory.load(ConfigFactory.parseString(s"""
    akka.javasdk.agent {
      model-provider = openai
      identity-headers = off

      gateway-openai = $${akka.javasdk.agent.openai}
      gateway-openai {
        additional-model-request-headers = ["Authorization:Bearer configured-token"]
      }
    }
    """))

  /**
   * A provider kind, by the simple name of its `ModelProvider` type, the reference.conf section that configures it, and
   * an instance built in code.
   */
  final case class ProviderKind(name: String, configSection: Option[String], provider: ModelProvider)

  /**
   * Every provider kind that reaches the runtime with model settings. Adding a kind without adding it here fails `cover
   * every model provider kind`.
   */
  private val providersCarryingModelSettings: Seq[ProviderKind] = Seq(
    ProviderKind("Anthropic", Some("anthropic"), ModelProvider.anthropic()),
    ProviderKind("GoogleAIGemini", Some("googleai-gemini"), ModelProvider.googleAiGemini()),
    ProviderKind("HuggingFace", Some("hugging-face"), ModelProvider.huggingFace()),
    ProviderKind("Ollama", Some("ollama"), ModelProvider.ollama()),
    ProviderKind("OpenAi", Some("openai"), ModelProvider.openAi()),
    ProviderKind("AzureOpenAi", Some("azure-openai"), ModelProvider.azureOpenAi()),
    ProviderKind("VertexAi", Some("vertex-ai"), ModelProvider.vertexAi()),
    ProviderKind("Bedrock", Some("bedrock"), ModelProvider.bedrock()),
    ProviderKind("MistralAi", Some("mistral-ai"), ModelProvider.mistralAi()))

  /** The SPI has no model settings for these two, so they never carry the switch. */
  private val providersWithoutModelSettings: Seq[ProviderKind] = Seq(
    ProviderKind("LocalAI", Some("local-ai"), ModelProvider.localAI()),
    ProviderKind("Custom", None, new NoConfigMyModelProvider()))
}

class ModelProviderSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {
  import ModelProviderSpec.config
  import ModelProviderSpec.identityHeadersOffConfig
  import ModelProviderSpec.providersCarryingModelSettings
  import ModelProviderSpec.providersWithoutModelSettings

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
    "load defaults from config for azure-openai" in {
      val m = ModelProvider.AzureOpenAi.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.azure-openai"))
      m shouldBe ModelProvider.azureOpenAi()
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

    "load anthropic prompt caching from config" in {
      val cfg = ConfigFactory
        .parseString("""
        cache-system-messages = true
        cache-tools = true
        """)
        .withFallback(defaultConfig.getConfig("akka.javasdk.agent.anthropic"))
      val m = ModelProvider.Anthropic.fromConfig(cfg)
      m.cacheSystemMessages() shouldBe true
      m.cacheTools() shouldBe true
    }

    "load bedrock prompt caching from config" in {
      val cfg = ConfigFactory
        .parseString("""
        prompt-caching = "after-system"
        """)
        .withFallback(defaultConfig.getConfig("akka.javasdk.agent.bedrock"))
      val m = ModelProvider.Bedrock.fromConfig(cfg)
      m.promptCaching().get() shouldBe ModelProvider.BedrockPromptCachePlacement.AFTER_SYSTEM
    }

    "bedrock prompt caching supports all placement values" in {
      def parse(v: String) = {
        val cfg = ConfigFactory
          .parseString(s"""prompt-caching = "$v" """)
          .withFallback(defaultConfig.getConfig("akka.javasdk.agent.bedrock"))
        ModelProvider.Bedrock.fromConfig(cfg).promptCaching().get()
      }
      parse("after-system") shouldBe ModelProvider.BedrockPromptCachePlacement.AFTER_SYSTEM
      parse("after-user-message") shouldBe ModelProvider.BedrockPromptCachePlacement.AFTER_USER_MESSAGE
      parse("after-tools") shouldBe ModelProvider.BedrockPromptCachePlacement.AFTER_TOOLS
    }

    "load defaults from config for vertex-ai" in {
      val m = ModelProvider.VertexAi.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.vertex-ai"))
      m shouldBe ModelProvider.vertexAi()
    }

    "load defaults from config for mistral-ai" in {
      val m = ModelProvider.MistralAi.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.mistral-ai"))
      m shouldBe ModelProvider.mistralAi()
    }

    "fail when custom model provider class not found" in {
      assertThrows[IllegalArgumentException] {
        AgentImpl.modelProviderFromConfig(
          ConfigFactory.parseString(s"""
          akka.javasdk {
            agent {
              model-provider = my-provider

              my-provider {
                # fully qualified class name of the provider implementation
                provider = "wrong.package.MyModelProvider"
              }
            }
          }
          """),
          "akka.javasdk.agent.my-provider",
          "myagent")
      }
    }

    "load custom model provider class with config" in {
      val provider =
        AgentImpl
          .modelProviderFromConfig(
            ConfigFactory.parseString(s"""
          akka.javasdk {
            agent {
              model-provider = my-provider

              my-provider {
                # fully qualified class name of the provider implementation
                provider = "akka.javasdk.impl.agent.MyModelProvider"
                model-name = "my-model"
              }
            }
          }
          """),
            "akka.javasdk.agent.my-provider",
            "myagent")
          .asInstanceOf[MyModelProvider]

      provider.modelName() shouldBe "my-model"
    }

    "load custom model provider class without config" in {
      val provider =
        AgentImpl
          .modelProviderFromConfig(
            ConfigFactory.parseString(s"""
          akka.javasdk {
            agent {
              model-provider = my-provider

              my-provider {
                # fully qualified class name of the provider implementation
                provider = "akka.javasdk.impl.agent.NoConfigMyModelProvider"
              }
            }
          }
          """),
            "akka.javasdk.agent.my-provider",
            "myagent")
          .asInstanceOf[NoConfigMyModelProvider]

      provider.modelName() shouldBe "no-config-model-name"
    }

  }

  "Additional model request headers" should {

    def spiHeaders(modelProvider: ModelProvider): Seq[(String, String)] =
      AgentImpl
        .toSpiModelProvider(modelProvider, config, "myagent")
        .modelSettings
        .additionalModelRequestHeaders
        .map(h => h.name -> h.value)

    def fromConfigWith(headers: HttpHeader*): ModelProvider =
      ModelProvider.fromConfig("gateway-openai").withAdditionalModelRequestHeaders(headers.toList.asJava)

    "keep the configured headers when fromConfig states another one" in {
      spiHeaders(fromConfigWith(RawHeader.create("X-Request-Id", "r-1"))) shouldBe Seq(
        "Authorization" -> "Bearer configured-token",
        "X-Request-Id" -> "r-1")
    }

    "keep the configured headers when fromConfig states none" in {
      spiHeaders(ModelProvider.fromConfig("gateway-openai")) shouldBe Seq("Authorization" -> "Bearer configured-token")
    }

    "let a header stated in code replace the configured one with the same name" in {
      spiHeaders(fromConfigWith(RawHeader.create("Authorization", "Bearer from-code"))) shouldBe Seq(
        "Authorization" -> "Bearer from-code")
    }

    "compare header names ignoring case" in {
      spiHeaders(fromConfigWith(RawHeader.create("AUTHORIZATION", "Bearer from-code"))) shouldBe Seq(
        "AUTHORIZATION" -> "Bearer from-code")
      spiHeaders(fromConfigWith(RawHeader.create("authorization", "Bearer from-code"))) shouldBe Seq(
        "authorization" -> "Bearer from-code")
    }

    "replace the configured headers for a provider built in code" in {
      val provider = ModelProvider.OpenAi
        .fromConfig(config.getConfig("akka.javasdk.agent.gateway-openai"))
        .withAdditionalModelRequestHeaders(List[HttpHeader](RawHeader.create("X-Request-Id", "r-1")).asJava)
      spiHeaders(provider) shouldBe Seq("X-Request-Id" -> "r-1")
    }

    "tell a custom provider to override the wither" in {
      val exc = intercept[UnsupportedOperationException] {
        new NoConfigMyModelProvider().withAdditionalModelRequestHeaders(
          List[HttpHeader](RawHeader.create("X-Request-Id", "r-1")).asJava)
      }
      exc.getMessage should include("builds its own chat model")
    }

    "keep the constructor taking only a config path" in {
      val provider = new ModelProvider.FromConfig("gateway-openai")
      provider.additionalModelRequestHeaders() shouldBe empty
      spiHeaders(provider) shouldBe Seq("Authorization" -> "Bearer configured-token")
    }
  }

  "Identity headers" should {

    def identityHeaders(modelProvider: ModelProvider, cfg: Config): Boolean =
      AgentImpl.toSpiModelProvider(modelProvider, cfg, "myagent").modelSettings.identityHeaders

    "cover every model provider kind" in {
      val permitted = classOf[ModelProvider].getPermittedSubclasses.map(_.getSimpleName).toSet
      val covered =
        (providersCarryingModelSettings ++ providersWithoutModelSettings).map(_.name).toSet + "FromConfig"
      covered shouldBe permitted
    }

    "be on by default for every provider kind built in code" in {
      providersCarryingModelSettings.foreach { kind =>
        withClue(s"[${kind.name}] ") {
          identityHeaders(kind.provider, config) shouldBe true
        }
      }
    }

    "be on by default for every provider kind named in config" in {
      providersCarryingModelSettings.foreach { kind =>
        withClue(s"[${kind.name}] ") {
          identityHeaders(ModelProvider.fromConfig(kind.configSection.get), config) shouldBe true
        }
      }
    }

    "follow the global switch for a provider built in code" in {
      providersCarryingModelSettings.foreach { kind =>
        withClue(s"[${kind.name}] ") {
          identityHeaders(kind.provider, identityHeadersOffConfig) shouldBe false
        }
      }
    }

    "follow the global switch for every provider kind named in config" in {
      providersCarryingModelSettings.foreach { kind =>
        withClue(s"[${kind.name}] ") {
          identityHeaders(ModelProvider.fromConfig(kind.configSection.get), identityHeadersOffConfig) shouldBe false
        }
      }
    }

    "follow the global switch for a provider from config" in {
      identityHeaders(ModelProvider.fromConfig("gateway-openai"), identityHeadersOffConfig) shouldBe false
      identityHeaders(ModelProvider.fromConfig(""), identityHeadersOffConfig) shouldBe false
    }

    "keep the provider headers when the switch is off" in {
      AgentImpl
        .toSpiModelProvider(ModelProvider.fromConfig("gateway-openai"), identityHeadersOffConfig, "myagent")
        .modelSettings
        .additionalModelRequestHeaders
        .map(h => h.name -> h.value) shouldBe Seq("Authorization" -> "Bearer configured-token")
    }

    "let the resolved section override the global switch" in {
      identityHeaders(ModelProvider.fromConfig("anonymous-openai"), config) shouldBe false
      // the same section reached through akka.javasdk.agent.model-provider
      val cfg = ConfigFactory
        .parseString("akka.javasdk.agent.model-provider = anonymous-openai")
        .withFallback(config)
        .resolve()
      identityHeaders(ModelProvider.fromConfig(""), cfg) shouldBe false
      // a sibling section is unaffected
      identityHeaders(ModelProvider.fromConfig("gateway-openai"), config) shouldBe true
    }

    "never carry the switch for a provider without model settings" in {
      providersWithoutModelSettings.foreach { kind =>
        withClue(s"[${kind.name}] ") {
          identityHeaders(kind.provider, config) shouldBe false
        }
      }
    }
  }

}

<!-- <nav> -->
- [Akka](../index.html)
- [AI Model Provider Details](model-provider-details.html)

<!-- </nav> -->

# AI Model Provider Details

Akka provides integration with several backend AI models, and you have to select which model to use. [Configuring the model](agents.html#_configuring_the_model) gives an overview of how to specify the model to use. This page describes the configuration for each model provider in more detail. Each model provider may have different settings.

## <a href="about:blank#_anthropic"></a> Anthropic

Configuration properties:

```hocon
# Configuration for Anthropic's large language models
akka.javasdk.agent.anthropic {
  # The provider name, must be "anthropic"
  provider = "anthropic"
  # The API key for authentication with Anthropic's API
  api-key = ""
  # Environment variable override for the API key
  api-key = ${?ANTHROPIC_API_KEY}
  # The name of the model to use, e.g. "claude-2" or "claude-instant-1"
  model-name = ""
  # Optional base URL override for the Anthropic API
  base-url = ""
  # Controls randomness in the model's output (0.0 to 1.0)
  temperature = NaN
  # Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
  # only considering the most likely tokens whose cumulative probability
  # exceeds the threshold value. It helps balance between diversity and
  # quality of outputs—lower values (like 0.3) produce more focused,
  # predictable text while higher values (like 0.9) allow more creativity
  # and variation.
  top-p = NaN
  # Top-k sampling parameter (-1 to disable).
  # Top-k sampling limits text generation to only the k most probable
  # tokens at each step, discarding all other possibilities regardless
  # of their probability. It provides a simpler way to control randomness,
  # smaller k values (like 10) produce more focused outputs while larger
  # values (like 50) allow for more diversity.
  top-k = -1
  # Maximum number of tokens to generate (-1 for model default)
  max-tokens = -1
}
```
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.Anthropic.html">`ModelProvider.Anthropic`</a> for programmatic settings.

## <a href="about:blank#_googleaigemini"></a> GoogleAIGemini

Configuration properties:

```hocon
# Configuration for Google's Gemini AI large language models
akka.javasdk.agent.googleai-gemini {
  # The provider name, must be "googleai-gemini"
  provider = "googleai-gemini"
  # The API key for authentication with Google AI Gemini's API
  api-key = ""
  # Environment variable override for the API key
  api-key = ${?GOOGLE_AI_GEMINI_API_KEY}
  # The name of the model to use, e.g. "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro" or "gemini-1.0-pro"
  model-name = ""
  # Controls randomness in the model's output (0.0 to 1.0)
  temperature = NaN
  # Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
  # only considering the most likely tokens whose cumulative probability
  # exceeds the threshold value. It helps balance between diversity and
  # quality of outputs—lower values (like 0.3) produce more focused,
  # predictable text while higher values (like 0.9) allow more creativity
  # and variation.
  top-p = NaN
  # Maximum number of tokens to generate (-1 for model default)
  max-output-tokens = -1
}
```
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.GoogleAIGemini.html">`ModelProvider.GoogleAIGemini`</a> for programmatic settings.

## <a href="about:blank#_huggingface"></a> HuggingFace

Configuration properties:

```hocon
# Configuration for large language models from HuggingFace https://huggingface.co
akka.javasdk.agent.hugging-face {
  # The provider name, must be "hugging-face"
  provider = "hugging-face"
  # The access token for authentication with the Hugging Face API
  access-token = ""
  # The Hugging face model id, e.g. "microsoft/Phi-3.5-mini-instruct"
  model-id = ""
  # Optional base URL override for the Hugging Face API
  base-url = ""
  # Controls randomness in the model's output (0.0 to 1.0)
  temperature = NaN
  # Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
  # only considering the most likely tokens whose cumulative probability
  # exceeds the threshold value. It helps balance between diversity and
  # quality of outputs—lower values (like 0.3) produce more focused,
  # predictable text while higher values (like 0.9) allow more creativity
  # and variation.
  top-p = NaN
  # Maximum number of tokens to generate (-1 for model default)
  max-new-tokens = -1
}
```
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.HuggingFace.html">`ModelProvider.HuggingFace`</a> for programmatic settings.

## <a href="about:blank#_localai"></a> LocalAI

Configuration properties:

```hocon
# Configuration for Local AI large language models
akka.javasdk.agent.local-ai {
  # The provider name, must be "local-ai"
  provider = "local-ai"
  # server base url
  base-url = "http://localhost:8080/v1"
  # One of the models installed in the Ollama server
  model-name = ""
  # Controls randomness in the model's output (0.0 to 1.0)
  temperature = NaN
  # Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
  # only considering the most likely tokens whose cumulative probability
  # exceeds the threshold value. It helps balance between diversity and
  # quality of outputs—lower values (like 0.3) produce more focused,
  # predictable text while higher values (like 0.9) allow more creativity
  # and variation.
  top-p = NaN
  # Maximum number of tokens to generate (-1 for model default)
  max-tokens = -1
}
```
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.LocalAI.html">`ModelProvider.LocalAI`</a> for programmatic settings.

## <a href="about:blank#_ollama"></a> Ollama

Configuration properties:

```hocon
# Configuration for Ollama large language models
akka.javasdk.agent.ollama {
  # The provider name, must be "ollama"
  provider = "ollama"
  # Ollama server base url
  base-url = "http://localhost:11434"
  # One of the models installed in the Ollama server
  model-name = ""
  # Controls randomness in the model's output (0.0 to 1.0)
  temperature = NaN
  # Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
  # only considering the most likely tokens whose cumulative probability
  # exceeds the threshold value. It helps balance between diversity and
  # quality of outputs—lower values (like 0.3) produce more focused,
  # predictable text while higher values (like 0.9) allow more creativity
  # and variation.
  top-p = NaN
}
```
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.Ollama.html">`ModelProvider.Ollama`</a> for programmatic settings.

## <a href="about:blank#_openai"></a> OpenAi

Configuration properties:

```hocon
# Configuration for OpenAI's large language models
akka.javasdk.agent.openai {
  # The provider name, must be "openai"
  provider = "openai"
  # The API key for authentication with OpenAI's API
  api-key = ""
  # Environment variable override for the API key
  api-key = ${?OPENAI_API_KEY}
  # The name of the model to use, e.g. "gpt-4" or "gpt-3.5-turbo"
  model-name = ""
  # Optional base URL override for the OpenAI API
  base-url = ""
  # Controls randomness in the model's output (0.0 to 1.0)
  temperature = NaN
  # Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
  # only considering the most likely tokens whose cumulative probability
  # exceeds the threshold value. It helps balance between diversity and
  # quality of outputs—lower values (like 0.3) produce more focused,
  # predictable text while higher values (like 0.9) allow more creativity
  # and variation.
  top-p = NaN
  # Maximum number of tokens to generate (-1 for model default)
  max-tokens = -1
}
```
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.OpenAi.html">`ModelProvider.OpenAi`</a> for programmatic settings.

<!-- <footer> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
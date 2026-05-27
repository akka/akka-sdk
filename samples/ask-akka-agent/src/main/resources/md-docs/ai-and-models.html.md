<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Integrations](index.html)
- [AI & models](ai-and-models.html)

<!-- </nav> -->

# AI & models

Akka provides native integration with 10 LLM providers. You configure a model in `application.conf` and your Agents use it automatically. No additional libraries or adapters are required.

## <a href="about:blank#_built_in_providers"></a> Built-in providers

| Provider | Site |
| --- | --- |
| Anthropic | [anthropic.com](https://www.anthropic.com/) |
| OpenAI | [openai.com](https://openai.com/) |
| Google AI Gemini | [gemini.google.com](https://gemini.google.com/) |
| Google Cloud Vertex AI | [cloud.google.com/vertex-ai](https://cloud.google.com/vertex-ai) |
| AWS Bedrock | [aws.amazon.com/bedrock](https://aws.amazon.com/bedrock/) |
| Mistral AI | [mistral.ai](https://mistral.ai/) |
| Hugging Face | [huggingface.co](https://huggingface.co/) |
| Ollama (local) | [ollama.com](https://ollama.com/) |
| LocalAI (local) | [localai.io](https://localai.io/) |

## <a href="about:blank#_custom_providers"></a> Custom providers

You can plug in any model by implementing the `ModelProvider.Custom` interface. This involves the underlying LangChain4J `ChatModel` and optionally `StreamingChatModel` implementations.

## <a href="about:blank#_see_also"></a> See also

- [Agents](../agents.html) — how Agents interact with AI models
- [Model provider details](../model-provider-details.html) — full configuration reference for each provider

<!-- <footer> -->
<!-- <nav> -->
[Component and service calls](../component-and-service-calls.html) [Data & knowledge](data-and-knowledge.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Setup and configuration](setup-and-configuration/index.html)
- [AI model provider configuration](model-provider-details.html)

<!-- </nav> -->

# AI model provider configuration

Akka provides integration with several backend AI models. You are responsible for configuring the AI model provider for every agent you build, whether you do so with configuration settings or via code.

As discussed in the [Configuring the model](agents.html#model) section of the Agent documentation, supplying a model provider through code will override the model provider configured through `application.conf` settings. You can also have multiple model providers configured and then use the `fromConfig` method of the `ModelProvider` class to load a specific one.

This page provides a detailed list of all of the configuration values available to each provider. As with all Akka configuration, the model configuration is declared using the [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) format.

## <a href="about:blank#_definitions"></a> Definitions

The following are a few definitions that might not be familiar to you. Not all models support these properties, but when they do, their definition remains the same.

### <a href="about:blank#_temperature"></a> Temperature

A value from 0.0 to 1.0 that indicates the amount of randomness in the model output. Often described as controlling how "creative" a model can get. The lower the value, the more precise and strict you want the model to behave. The higher the value, the more you expect it to improvise and the less deterministic it will be.

### <a href="about:blank#_top_p"></a> top-p

This property refers to the "Nucleus sampling parameter." Controls text generation by only considering the most likely tokens whose cumulative probability
exceeds the threshold value. It helps balance between diversity and
quality of outputs—lower values (like 0.3) produce more focused,
predictable text while higher values (like 0.9) allow more creativity
and variation.

### <a href="about:blank#_top_k"></a> top-k

Top-k sampling limits text generation to only the k most probable
tokens at each step, discarding all other possibilities regardless
of their probability. It provides a simpler way to control randomness,
smaller k values (like 10) produce more focused outputs while larger
values (like 50) allow for more diversity.

### <a href="about:blank#_max_tokens_or_max_completion_tokens"></a> max-tokens or max-completion-tokens

If this value is supplied and the model supports this property, then it will stop operations in mid flight if the token quota runs out. It’s important to check *how* the model counts tokens, as some may count differently. Be aware of the fact that this parameter name frequently varies from one provider to the next. Make sure you’re using the right property name.

### <a href="about:blank#_thinking"></a> Thinking

Some models support thinking, also sometimes referred to as "thought process" or reasoning, where the model generates an intermediate
thought process, breaking down bigger problems into smaller more manageable tasks. This can be good for more complex problems but comes
at the price of tokens used for the thought process.

For models that support thinking, it is enabled through model-specific configuration, either by setting a thinking token budget, or
by a toggle. Thinking is disabled for all models by default.

## <a href="about:blank#_model_configuration"></a> Model configuration

The following is a list of all natively supported model configurations. Remember that if you don’t see your model or model format here, you can always create your own custom configuration and still use all of the Agent-related components.

### <a href="about:blank#_anthropic"></a> Anthropic

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "anthropic" | Name of the provider. Must always be `anthropic` |
| `api-key` | String | The API key. Defaults to the value of the `ANTHROPIC_API_KEY` environment variable |
| `model-name` | String | The name of the model to use. See vendor documentation for a list of available models |
| `base-url` | Url | Optional override to the base URL of the API |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `top-k` | Integer | Top-k sampling parameter |
| `max-tokens` | Integer | Max token quota. Leave as –1 for model default |
| `connection-timeout` | Duration | Fail the request if connecting to the model API takes longer than this |
| `response-timeout` | Duration | Fail the request if getting a response from the model API takes longer than this |
| `max-retries` | Integer | Retry this many times if the request to the model fails |
| `thinking-budget-tokens` | Integer | A maximum number of tokens to spend on thinking, use 0 to disable thinking |
| `cache-system-messages` | Boolean | Enable prompt caching of the last system message. Disabled by default. See [Anthropic prompt caching](https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching) for details. |
| `cache-tools` | Boolean | Enable prompt caching of the last tool definition. Disabled by default. |
| `additional-model-request-headers` | List<String> | List of `"name:value"` strings for HTTP headers to include in every request to the model API. Inherits from `akka.javasdk.agent.additional-model-request-headers` by default. Can also be set via `ADDITIONAL_ANTHROPIC_REQUEST_HEADERS_N` environment variables. |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.Anthropic.html">`ModelProvider.Anthropic`</a> for programmatic settings.

### <a href="about:blank#_azure_openai"></a> Azure OpenAI

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "azure-openai" | Name of the provider. Must always be `azure-openai` |
| `endpoint` | Url | The endpoint of the Azure OpenAI resource, e.g. `https://my-resource.openai.azure.com`. See the [Azure OpenAI documentation](https://learn.microsoft.com/azure/ai-services/openai/) for details |
| `deployment-name` | String | The name of the Azure OpenAI deployment to use |
| `api-key` | String | The API key. Defaults to the value of the `AZURE_OPENAI_API_KEY` environment variable |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `max-tokens` | Integer | Max token quota. Leave as –1 for model default |
| `max-completion-tokens` | Integer | Max token quota. Leave as –1 for model default |
| `frequency-penalty` | Float | Penalizes repeated tokens based on their frequency in the text so far (-2.0 to 2.0) |
| `presence-penalty` | Float | Penalizes tokens that have already appeared in the text (-2.0 to 2.0) |
| `seed` | Long | Seed for deterministic sampling, for reproducible outputs. Leave as –1 for unset |
| `stop` | List of String | Sequences where the model will stop generating further tokens |
| `reasoning-effort` | String | Reasoning effort level for o-series models. One of `low`, `medium`, `high` |
| `service-version` | String | Azure OpenAI API service version override, e.g. `2024-02-15-preview` |
| `connection-timeout` | Duration | Fail the request if connecting to the model API takes longer than this |
| `response-timeout` | Duration | Fail the request if getting a response from the model API takes longer than this |
| `max-retries` | Integer | Retry this many times if the request to the model fails |
| `additional-model-request-headers` | List of String | List of `"name:value"` strings for HTTP headers to include in every request to the model API. Inherits from `akka.javasdk.agent.additional-model-request-headers` by default. Can also be set via `ADDITIONAL_AZURE_OPENAI_REQUEST_HEADERS_N` environment variables. |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.AzureOpenAi.html">`ModelProvider.AzureOpenAi`</a> for programmatic settings.

### <a href="about:blank#_bedrock"></a> Bedrock

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "bedrock" | Name of the provider. Must always be `bedrock` |
| `region` | String | The region to be used, e.g. "us-east-1" |
| `model-id` | String | The Bedrock model id, e.g. "ai21.jamba-1-5-large-v1:0" |
| `max-output-tokens` | Integer | Max token *output* quota. Leave as –1 for model default |
| `reasoning-token-budget` | Integer | Max reasoning token budget. Leave as –1 for model default |
| `additional-model-request-fields` | Map<String, Object> | Send additional fields, e.g. *additional-model-request-fields.key=value* |
| `access-token` | String | The access token for authentication with the Bedrock API |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `max-tokens` | Integer | Maximum number of tokens to generate. Leave as –1 for model default |
| `response-timeout` | Duration | Fail the request if getting a response from the model API takes longer than this |
| `max-retries` | Integer | Retry this many times if the request to the model fails |
| `prompt-caching` | String | Enable prompt caching for Anthropic Claude or Amazon Nova models by selecting where the cache point is placed. Valid values: `""` (disabled, default), `"after-system"`, `"after-user-message"`, `"after-tools"`. See [AWS Bedrock prompt caching](https://docs.aws.amazon.com/bedrock/latest/userguide/capacity-limits-cost-optimization.html#prompt-caching) for details. |
| `additional-model-request-headers` | List<String> | List of `"name:value"` strings for HTTP headers to include in every request to the model API. Inherits from `akka.javasdk.agent.additional-model-request-headers` by default. Can also be set via `ADDITIONAL_BEDROCK_REQUEST_HEADERS_N` environment variables. |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.Bedrock.html">`ModelProvider.Bedrock`</a> for programmatic settings.

### <a href="about:blank#_gemini"></a> Gemini

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "googleai-gemini" | Name of the provider. Must always be `googleai-gemini` |
| `api-key` | String | The API key. Defaults to the value of the `GOOGLE_AI_GEMINI_API_KEY` environment variable |
| `model-name` | String | The name of the model to use. See vendor documentation for a list of available models |
| `base-url` | Url | Optional override to the base URL of the API |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `max-output-tokens` | Integer | Max token *output* quota. Leave as –1 for model default |
| `connection-timeout` | Duration | Fail the request if connecting to the model API takes longer than this |
| `response-timeout` | Duration | Fail the request if getting a response from the model API takes longer than this |
| `max-retries` | Integer | Retry this many times if the request to the model fails |
| `thinking-budget` | Integer | A budget of tokens to spend on thinking for Gemini 2.5 models, set to "none" for other models. Can be –1 for dynamic budget, 0 for disabled, a positive value to define an upper limit for tokens spent on thinking. See [https://ai.google.dev/gemini-api/docs/thinking#set-budget](https://ai.google.dev/gemini-api/docs/thinking#set-budget) for details |
| `thinking-level` | String | Control thinking for Gemini 3 models, exact values depend on the specific model chosen, must be empty for 2.5 models. See Google Gemini docs for more details: [https://ai.google.dev/gemini-api/docs/thinking#thinking-levels](https://ai.google.dev/gemini-api/docs/thinking#thinking-levels) |
| `media-resolution` | String | Defines the level for media resolution. Possible values: `MEDIA_RESOLUTION_UNSPECIFIED`, `MEDIA_RESOLUTION_LOW`, `MEDIA_RESOLUTION_MEDIUM`, `MEDIA_RESOLUTION_HIGH`, `MEDIA_RESOLUTION_ULTRA_HIGH`. See [https://ai.google.dev/gemini-api/docs/media-resolution](https://ai.google.dev/gemini-api/docs/media-resolution) for details |
| `media-resolution-per-part-enabled` | Boolean | When enabled, allows setting media resolution for individual media objects (like images) within your request |
| `additional-model-request-headers` | List of String | List of `"name:value"` strings for HTTP headers to include in every request to the model API. Inherits from `akka.javasdk.agent.additional-model-request-headers` by default. Can also be set via `ADDITIONAL_GOOGLE_AI_GEMINI_REQUEST_HEADERS_N` environment variables. |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.GoogleAIGemini.html">`ModelProvider.GoogleAIGemini`</a> for programmatic settings.

### <a href="about:blank#_hugging_face"></a> Hugging Face

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "hugging-face" | Name of the provider. Must always be `hugging-face` |
| `access-token` | String | The access token for authentication with the Hugging Face API |
| `model-id` | String | The ID of the model to use. See vendor documentation for a list of available models |
| `base-url` | Url | Optional override to the base URL of the API |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `max-new-tokens` | Integer | Max number of tokens to generate (–1 for model default) |
| `connection-timeout` | Duration | Fail the request if connecting to the model API takes longer than this |
| `response-timeout` | Duration | Fail the request if getting a response from the model API takes longer than this |
| `max-retries` | Integer | Retry this many times if the request to the model fails |
| `thinking` | Boolean | Enable thinking, only supported for some models. Make sure the chosen model supports thinking before enabling. |
| `additional-model-request-headers` | List of String | List of `"name:value"` strings for HTTP headers to include in every request to the model API. Inherits from `akka.javasdk.agent.additional-model-request-headers` by default. Can also be set via `ADDITIONAL_HUGGING_FACE_REQUEST_HEADERS_N` environment variables. |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.HuggingFace.html">`ModelProvider.HuggingFace`</a> for programmatic settings.

### <a href="about:blank#_local_ai"></a> Local AI

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "local-ai" | Name of the provider. Must always be `local-ai` |
| `model-name` | String | The name of the model to use. See vendor documentation for a list of available models |
| `base-url` | Url | Optional override to the base URL of the API (default `http://localhost:8080/v1`) |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `max-tokens` | Integer | Max number of tokens to generate (–1 for model default) |
| `additional-model-request-headers` | List of String | List of `"name:value"` strings for HTTP headers to include in every request to the model API. Inherits from `akka.javasdk.agent.additional-model-request-headers` by default. Can also be set via `ADDITIONAL_LOCAL_AI_REQUEST_HEADERS_N` environment variables. |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.LocalAI.html">`ModelProvider.LocalAI`</a> for programmatic settings.

### <a href="about:blank#_mistral_ai"></a> Mistral AI

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "mistral-ai" | Name of the provider. Must always be `mistral-ai` |
| `api-key` | String | The API key. Defaults to the value of the `MISTRAL_AI_API_KEY` environment variable |
| `model-name` | String | The name of the model to use, e.g. "mistral-large-latest". See vendor documentation for a list of available models |
| `base-url` | Url | Optional override to the base URL of the API |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `max-tokens` | Integer | Maximum number of tokens to generate. Leave as –1 for model default |
| `safe-prompt` | Boolean | When enabled, a safety prompt is injected in front of all conversations |
| `random-seed` | Integer | Random seed for deterministic sampling. Leave as –1 to disable |
| `frequency-penalty` | Float | Penalty for frequent tokens. Leave as `NaN` for model default |
| `presence-penalty` | Float | Penalty for repeated topics. Leave as `NaN` for model default |
| `stop-sequences` | List of String | Stop sequences at which the model stops generating |
| `thinking` | Boolean | Enable thinking, only supported for some models. Make sure the chosen model supports thinking before enabling. |
| `connection-timeout` | Duration | Fail the request if connecting to the model API takes longer than this |
| `response-timeout` | Duration | Fail the request if getting a response from the model API takes longer than this |
| `max-retries` | Integer | Retry this many times if the request to the model fails |
| `additional-model-request-headers` | List of String | List of `"name:value"` strings for HTTP headers to include in every request to the model API. Inherits from `akka.javasdk.agent.additional-model-request-headers` by default. Can also be set via `ADDITIONAL_MISTRAL_AI_REQUEST_HEADERS_N` environment variables. |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.MistralAi.html">`ModelProvider.MistralAi`</a> for programmatic settings.

### <a href="about:blank#_ollama"></a> Ollama

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "ollama" | Name of the provider. Must always be `ollama` |
| `model-name` | String | The name of the model to use. See vendor documentation for a list of available models |
| `base-url` | Url | Optional override to the base URL of the API (default `http://localhost:11434`) |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `connection-timeout` | Duration | Fail the request if connecting to the model API takes longer than this |
| `response-timeout` | Duration | Fail the request if getting a response from the model API takes longer than this |
| `max-retries` | Integer | Retry this many times if the request to the model fails |
| `think` | Boolean | Enable thinking, only supported for some models. Make sure the chosen model supports thinking before enabling. |
| `additional-model-request-headers` | List of String | List of `"name:value"` strings for HTTP headers to include in every request to the model API. Inherits from `akka.javasdk.agent.additional-model-request-headers` by default. Can also be set via `ADDITIONAL_OLLAMA_REQUEST_HEADERS_N` environment variables. |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.Ollama.html">`ModelProvider.Ollama`</a> for programmatic settings.

### <a href="about:blank#_openai"></a> OpenAI

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "openai" | Name of the provider. Must always be `openai` |
| `api-key` | String | The API key. Defaults to the value of the `OPENAI_API_KEY` environment variable |
| `model-name` | String | The name of the model to use (e.g. "gpt-4" or "gpt-3.5-turbo"). See vendor documentation for a list of available models |
| `base-url` | Url | Optional override to the base URL of the API |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `max-tokens` | Integer | Max token quota. Leave as –1 for model default. Not supported by GPT-5, use max-completion-tokens instead. |
| `max-completion-tokens` | Integer | Max token quota. Leave as –1 for model default |
| `connection-timeout` | Duration | Fail the request if connecting to the model API takes longer than this |
| `response-timeout` | Duration | Fail the request if getting a response from the model API takes longer than this |
| `max-retries` | Integer | Retry this many times if the request to the model fails |
| `thinking` | Boolean | Enable thinking, only supported for DeepSeek. Make sure the chosen model supports thinking before enabling. |
| `additional-model-request-headers` | List of String | List of `"name:value"` strings for HTTP headers to include in every request to the model API. Inherits from `akka.javasdk.agent.additional-model-request-headers` by default. Can also be set via `ADDITIONAL_OPENAI_REQUEST_HEADERS_N` environment variables. |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.OpenAi.html">`ModelProvider.OpenAi`</a> for programmatic settings.

### <a href="about:blank#_vertex_ai"></a> Vertex AI

| Property | Type | Description |
| --- | --- | --- |
| `provider` | "vertex-ai" | Name of the provider. Must always be `vertex-ai` |
| `model-name` | String | The name of the model to use, e.g. "gemini-2.0-flash-001". See vendor documentation for a list of available models |
| `api-key` | String | API key for authentication. Defaults to the value of the `VERTEX_AI_API_KEY` environment variable. Use either this or `project-id` + `location`, not both |
| `project-id` | String | Google Cloud project ID. Used together with `location` for authentication via application default credentials. Use either this or `api-key`, not both |
| `location` | String | Google Cloud region, e.g. "us-central1". Used together with `project-id` |
| `base-url` | Url | Optional override to the base URL of the Vertex AI API |
| `api-version` | String | Optional override for the API version |
| `temperature` | Float | Model randomness. The default is not supplied so check with the model documentation for default behavior |
| `top-p` | Float | Nucleus sampling parameter |
| `thinking-budget` | Integer | Tokens to spend on thinking (0 to disable thinking) |
| `max-output-tokens` | Integer | Max token *output* quota. Leave as –1 for model default |
| `connection-timeout` | Duration | Fail the request if connecting to the model API takes longer than this |
| `response-timeout` | Duration | Fail the request if getting a response from the model API takes longer than this |
| `max-retries` | Integer | Retry this many times if the request to the model fails |
| `additional-model-request-headers` | List of String | Additional HTTP headers to include in each request to the model API, as a list of `"name:value"` strings. Can also be set via `ADDITIONAL_VERTEX_AI_REQUEST_HEADERS_N` environment variables |
See <a href="_attachments/api/akka/javasdk/agent/ModelProvider.VertexAi.html">`ModelProvider.VertexAi`</a> for programmatic settings.

## <a href="about:blank#_default_model_configuration"></a> Default model configuration

The default model will be used if the agent doesn’t specify another model.

You can define a default model in `application.conf`:

src/main/resources/application.conf
```json
akka.javasdk {
  agent {
    model-provider = openai

    openai {
      model-name = "gpt-4o-mini"
      api-key = ${?OPENAI_API_KEY}
    }
  }
}
```
The `model-provider` property points to the name of another configuration section, in this case `akka.javasdk.agent.openai`. That configuration section contains the actual configuration for the model provider, according to the properties described in below [Reference configurations](about:blank#_reference_configurations).

Another example where we have selected `anthropic` with `claude-sonnet-4` as the default model provider:

src/main/resources/application.conf
```json
akka.javasdk {
  agent {
    model-provider = anthropic

    anthropic {
      model-name = "claude-opus-4-6"
      api-key = ${?ANTHROPIC_API_KEY}
      max-tokens = 5000
    }
  }
}
```
The API key can be defined with an environment variable, `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` in the above examples.

## <a href="about:blank#_additional_model_request_headers"></a> Additional model request headers

All built in model providers support attaching extra HTTP headers to every request sent to the model API. This is useful for passing authentication tokens to an AI gateway or proxy, adding routing or tracing headers, or meeting any other header requirements imposed by the endpoint.

### <a href="about:blank#_configuration"></a> Configuration

Set a global default in `application.conf` as a HOCON list of `name:value` strings:

```hocon
akka.javasdk.agent.additional-model-request-headers = [
  "X-Custom-Header:value",
  "X-Custom-Header-2:value-2",
]
```
All providers inherit this value automatically. You can override it for a specific provider:

```hocon
akka.javasdk.agent.openai.additional-model-request-headers = ["X-Custom-Header:value"]
```

### <a href="about:blank#_environment_variable"></a> Environment variable

The global setting can also be supplied via environment variables, this is the recommended approach for secrets such as gateway tokens.

Each header entry is a separate environment variable with an incrementing numeric suffix:

```bash
ADDITIONAL_MODEL_REQUEST_HEADERS_0="Authorization:Bearer mytoken"
ADDITIONAL_MODEL_REQUEST_HEADERS_1="X-Custom-Header:value"
```
You can also set headers for a specific model provider using provider-specific environment variables. These replace the inherited global headers for that provider:

| Provider | Environment variable prefix |
| --- | --- |
| `Anthropic` | `ADDITIONAL_ANTHROPIC_REQUEST_HEADERS_N` |
| `Azure OpenAI` | `ADDITIONAL_AZURE_OPENAI_REQUEST_HEADERS_N` |
| `OpenAI` | `ADDITIONAL_OPENAI_REQUEST_HEADERS_N` |
| `Google AI Gemini` | `ADDITIONAL_GOOGLE_AI_GEMINI_REQUEST_HEADERS_N` |
| `Google Cloud Vertex AI` | `ADDITIONAL_VERTEX_AI_REQUEST_HEADERS_N` |
| `Ollama` | `ADDITIONAL_OLLAMA_REQUEST_HEADERS_N` |
| `Local AI` | `ADDITIONAL_LOCAL_AI_REQUEST_HEADERS_N` |
| `Hugging Face` | `ADDITIONAL_HUGGING_FACE_REQUEST_HEADERS_N` |
| `Bedrock` | `ADDITIONAL_BEDROCK_REQUEST_HEADERS_N` |
| `Mistral AI` | `ADDITIONAL_MISTRAL_AI_REQUEST_HEADERS_N` |
For example, to set a header only for Anthropic requests:

```bash
ADDITIONAL_ANTHROPIC_REQUEST_HEADERS_0="Authorization:Bearer mytoken"
```

### <a href="about:blank#_programmatic_override"></a> Programmatic override

Headers can also be set directly in code, which overrides any configuration value for that model:

```java
ModelProvider.openAi()
    .withApiKey(System.getenv("OPENAI_API_KEY"))
    .withAdditionalModelRequestHeaders(
        List.of(RawHeader.create("Authorization", "Bearer mygatewaytoken")));
```

## <a href="about:blank#_reference_configurations"></a> Reference configurations

The following is a list of the various reference configurations for each of the AI models

Note that the following reference configurations are the default values, and you would typically only define the properties that you want to override, such as:

```hocon
akka.javasdk.agent.openai {
  model-name = "gpt-4o-mini"
}
```
You may also have to use a fallback to the reference configuration if you use a different configuration section:

```hocon
gpt-o3 = ${akka.javasdk.agent.openai}
gpt-o3 {
  model-name = "o3"
  max-completion-tokens = 200000
}
```

### <a href="about:blank#_anthropic_2"></a> Anthropic

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
  # Fail the request if connecting to the model API takes longer than this
  connection-timeout = 15s
  # Fail the request if getting a response from the model API takes longer than this
  response-timeout = 1m
  # Retry this many times if the request to the model fails
  max-retries = 2
  # A maximum number of tokens to spend on thinking, use 0 to disable thinking
  thinking-budget-tokens = 0
  # Enable prompt caching of the last system message (reduces cost and latency for repeated
  # system prompts). Disabled by default. See the Anthropic prompt caching documentation.
  cache-system-messages = false
  # Enable prompt caching of the last tool definition (reduces cost and latency for repeated
  # tool specifications). Disabled by default.
  cache-tools = false
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_ANTHROPIC_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_azure_openai_2"></a> Azure OpenAI

```hocon
# Configuration for Azure OpenAI large language models
akka.javasdk.agent.azure-openai {
  # The provider name, must be "azure-openai"
  provider = "azure-openai"
  # The endpoint of the Azure OpenAI resource, e.g. "https://my-resource.openai.azure.com"
  endpoint = ""
  # The name of the Azure OpenAI deployment to use
  deployment-name = ""
  # The API key for authentication with the Azure OpenAI resource
  api-key = ""
  # Environment variable override for the API key
  api-key = ${?AZURE_OPENAI_API_KEY}
  # Controls randomness in the model's output (0.0 to 1.0)
  temperature = NaN
  # Nucleus sampling parameter (0.0 to 1.0).
  top-p = NaN
  # Maximum number of tokens to generate (-1 for model default)
  max-tokens = -1
  # Maximum number of completion tokens to generate (-1 for model default)
  max-completion-tokens = -1
  # Penalizes repeated tokens based on their frequency in the text so far (-2.0 to 2.0)
  frequency-penalty = NaN
  # Penalizes tokens that have already appeared in the text (-2.0 to 2.0)
  presence-penalty = NaN
  # Seed for deterministic sampling, for reproducible outputs (-1 for unset)
  seed = -1
  # Sequences where the model will stop generating further tokens
  stop = []
  # Reasoning effort level for o-series models ("low", "medium", "high", "" for unset)
  reasoning-effort = ""
  # Azure OpenAI API service version override (e.g. "2024-02-15-preview", "" for default)
  service-version = ""
  # Fail the request if connecting to the model API takes longer than this
  connection-timeout = 15s
  # Fail the request if getting a response from the model API takes longer than this
  response-timeout = 1m
  # Retry this many times if the request to the model fails
  max-retries = 2
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_AZURE_OPENAI_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_bedrock_2"></a> Bedrock

```hocon
# Configuration for large language models from Amazon Bedrock https://aws.amazon.com/bedrock
akka.javasdk.agent.bedrock {
  # The provider name, must be "bedrock"
  provider = "bedrock"
  # The region to be used, e.g. "us-east-1"
  region = ""
  # The Bedrock model id, e.g. "ai21.jamba-1-5-large-v1:0"
  model-id = ""
  # Return thinking can be enabled
  return-thinking = false
  # Send thinking can be enabled
  send-thinking = false
  # Max output tokens
  max-output-tokens = -1
  # Reasoning token budget
  reasoning-token-budget = -1
  additional-model-request-fields {
    # additional-key = "additional value"
  }
  # The access token for authentication with the Bedrock API
  access-token = ""
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
  # Fail the request if getting a response from the model API takes longer than this
  response-timeout = 1m
  # Retry this many times if the request to the model fails
  max-retries = 2
  # Enable prompt caching for Anthropic Claude or Amazon Nova models by selecting where the
  # cache point is placed. Valid values: "", "after-system", "after-user-message", "after-tools".
  # Empty disables prompt caching (default). See the AWS Bedrock prompt caching documentation.
  prompt-caching = ""
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_BEDROCK_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_gemini_2"></a> Gemini

```hocon
# Configuration for Google's Gemini AI large language models
akka.javasdk.agent.googleai-gemini {
  # The provider name, must be "googleai-gemini"
  provider = "googleai-gemini"
  # The API key for authentication with Google AI Gemini's API
  api-key = ""
  # Optional base URL override for the Gemini API
  base-url = ""
  # Environment variable override for the API key
  api-key = ${?GOOGLE_AI_GEMINI_API_KEY}
  # The name of the model to use, e.g. "gemini-2.5-flash", "gemini-2.5-pro", etc
  # The full list is at https://ai.google.dev/gemini-api/docs/models - select a model to find its "Model code"
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
  # Fail the request if connecting to the model API takes longer than this
  connection-timeout = 15s
  # Fail the request if getting a response from the model API takes longer than this
  response-timeout = 1m
  # Retry this many times if the request to the model fails
  max-retries = 2
  # A budget of tokens to spend on thinking for Gemini 2.5 models, set to "none" for other models
  # Can be -1 for dynamic budget, 0 for disabled, a positive value to define an upper limit for tokens spent on thinking.
  # See https://ai.google.dev/gemini-api/docs/thinking#set-budget for details
  thinking-budget = "none"
  # Control thinking for Gemini 3 models, exact values depend on the specific model chosen, must be empty for 2.5 models
  # See Google Gemini docs for more details: https://ai.google.dev/gemini-api/docs/thinking#thinking-levels
  thinking-level = ""
  # Defines the level for media resolution: https://ai.google.dev/gemini-api/docs/media-resolution
  # Possible values: MEDIA_RESOLUTION_UNSPECIFIED, MEDIA_RESOLUTION_LOW, MEDIA_RESOLUTION_MEDIUM, MEDIA_RESOLUTION_HIGH, MEDIA_RESOLUTION_ULTRA_HIGH
  media-resolution = "MEDIA_RESOLUTION_UNSPECIFIED"
  # Media resolution for individual media objects (like images) within your request
  media-resolution-per-part-enabled = false
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_GOOGLE_AI_GEMINI_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_hugging_face_2"></a> Hugging face

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
  # Fail the request if connecting to the model API takes longer than this
  connection-timeout = 15s
  # Fail the request if getting a response from the model API takes longer than this
  response-timeout = 1m
  # Retry this many times if the request to the model fails
  max-retries = 2
  # Enable thinking, only supported for some models. Make sure the chosen model supports thinking before enabling.
  thinking = false
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_HUGGING_FACE_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_local_ai_2"></a> Local AI

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
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_LOCAL_AI_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_mistral_ai_2"></a> Mistral AI

```hocon
# Configuration for large language models from Mistral AI https://mistral.ai
akka.javasdk.agent.mistral-ai {
  # The provider name, must be "mistral-ai"
  provider = "mistral-ai"
  # The API key for authentication with Mistral AI's API
  api-key = ""
  api-key = ${?MISTRAL_AI_API_KEY}
  # The Mistral model name, e.g. "mistral-large-latest"
  model-name = ""
  # Optional base URL override for the Mistral AI API
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
  # When enabled, a safety prompt is injected in front of all conversations
  safe-prompt = false
  # Random seed for deterministic sampling (-1 for none)
  random-seed = -1
  # Penalty for frequent tokens (NaN for model default)
  frequency-penalty = NaN
  # Penalty for repeated topics (NaN for model default)
  presence-penalty = NaN
  # Stop sequences at which the model stops generating
  stop-sequences = []
  # Enable thinking, only supported for some models. Make sure the chosen model supports thinking before enabling.
  thinking = false
  # Fail the request if connecting to the model API takes longer than this
  connection-timeout = 15s
  # Fail the request if getting a response from the model API takes longer than this
  response-timeout = 1m
  # Retry this many times if the request to the model fails
  max-retries = 2
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_MISTRAL_AI_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_ollama_2"></a> Ollama

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
  # Fail the request if connecting to the model API takes longer than this
  connection-timeout = 15s
  # Fail the request if getting a response from the model API takes longer than this
  response-timeout = 1m
  # Retry this many times if the request to the model fails
  max-retries = 2
  # Enable thinking, only supported for some models. Make sure the chosen model supports thinking before enabling.
  think = false
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_OLLAMA_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_openai_2"></a> OpenAI

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
  # Not supported by GPT-5.
  temperature = NaN
  # Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
  # only considering the most likely tokens whose cumulative probability
  # exceeds the threshold value. It helps balance between diversity and
  # quality of outputs—lower values (like 0.3) produce more focused,
  # predictable text while higher values (like 0.9) allow more creativity
  # and variation.
  # Not supported by GPT-5.
  top-p = NaN
  # Maximum number of tokens to generate (-1 for model default)
  # Not supported by GPT-5, use max-completion-tokens instead.
  max-tokens = -1
  # Maximum number of tokens to generate (-1 for model default)
  max-completion-tokens = -1
  # Fail the request if connecting to the model API takes longer than this
  connection-timeout = 15s
  # Fail the request if getting a response from the model API takes longer than this
  response-timeout = 1m
  # Retry this many times if the request to the model fails
  max-retries = 2
  # Enable thinking, only supported for deepseek. Make sure the chosen model supports thinking before enabling.
  thinking = false
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_OPENAI_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_vertex_ai_2"></a> Vertex AI

```hocon
# Configuration for large language models from Google Cloud Vertex AI https://cloud.google.com/vertex-ai
akka.javasdk.agent.vertex-ai {
  # The provider name, must be "vertex-ai"
  provider = "vertex-ai"
  # The Vertex AI model name, e.g. "gemini-2.0-flash-001"
  model-name = ""
  # The API key for authentication with Vertex AI
  # If using this, the project-id and location must be empty.
  api-key = ""
  api-key = ${?VERTEX_AI_API_KEY}
  # The Google Cloud project ID
  # If defining a project-id, location should also be defined. The api-key must be empty and credentials provided through GOOGLE_APPLICATION_CREDENTIALS
  project-id = ""
  # The Google Cloud region, e.g. "us-central1"
  location = ""
  # Optional base URL override for the Vertex AI API
  base-url = ""
  # Optional API version override, if left undefined beta API versions are used, set to for example "v1" to use a stable API version
  api-version = ""
  # Controls randomness in the model's output (0.0 to 2.0)
  temperature = NaN
  # Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
  # only considering the most likely tokens whose cumulative probability
  # exceeds the threshold value. It helps balance between diversity and
  # quality of outputs—lower values (like 0.3) produce more focused,
  # predictable text while higher values (like 0.9) allow more creativity
  # and variation.
  top-p = NaN
  # Tokens to spend on thinking (0 to disable thinking)
  thinking-budget = 0
  # Maximum number of tokens to generate (-1 for model default)
  max-output-tokens = -1
  # Fail the request if connecting to the model API takes longer than this
  connection-timeout = 15s
  # Fail the request if getting a response from the model API takes longer than this
  response-timeout = 1m
  # Retry this many times if the request to the model fails
  max-retries = 2
  # Additional HTTP headers to include in each request to the model API.
  # Inherits from akka.javasdk.agent.additional-model-request-headers by default.
  additional-model-request-headers = ${akka.javasdk.agent.additional-model-request-headers}
  additional-model-request-headers = ${?ADDITIONAL_VERTEX_AI_REQUEST_HEADERS[]}
}
```

### <a href="about:blank#_custom"></a> Custom

If none of the built-in providers fit your needs, you can implement a custom model provider. Set the `provider` property to the fully qualified class name (FQCN) of your implementation.

The custom class must implement the `ModelProvider.Custom` interface, which requires two methods: `createChatModel()` and `createStreamingChatModel()`. These should return instances of Langchain4j’s `ChatModel` and `StreamingChatModel` respectively. If streaming is not needed, `createStreamingChatModel()` can throw an exception. Overriding the `modelName()` method allows to pass this information to observability attributes.

```hocon
akka.javasdk {
  agent {
    model-provider = my-provider

    my-provider {
      # fully qualified class name of the provider implementation
      provider = "com.example.application.MyModelProvider"
      # additional settings
      model-name = "gpt-4o-mini"
      api-key = ""
      api-key = ${?MY_PROVIDER_API_KEY}
    }
  }
}
```
Any additional properties in the configuration section (such as `model-name` or `api-key`) are available to the provider implementation through an injected `Config` parameter.

com/example/application/MyModelProvider.java
```java
public class MyModelProvider implements ModelProvider.Custom {

  private final String modelName;

  public MyModelProvider(Config config) { // (1)
    modelName = config.getString("model-name");
    //TODO model provider initialization based on injected config
  }

  @Override
  public String modelName() {
    return modelName;
  }

  @Override
  public Object createChatModel() { // (2)
    //TODO return ChatModel implementation
    return null;
  }

  @Override
  public Object createStreamingChatModel() { // (3)
    //TODO return StreamingChatModel implementation
    return null;
  }
}
```

| **1** | If the constructor accepts a `com.typesafe.config.Config` parameter, the full provider configuration section is injected automatically. A no-arg constructor is also supported. |
| **2** | Return an instance of `dev.langchain4j.model.chat.ChatModel`. |
| **3** | Return an instance of `dev.langchain4j.model.chat.StreamingChatModel`, or throw an exception if streaming is not needed. |

<!-- <footer> -->
<!-- <nav> -->
[Running locally with a message broker](running-with-broker.html) [Data sanitization](sanitization.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
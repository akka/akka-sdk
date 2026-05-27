<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Telemetry reference](index.html)
- [Tracing reference](tracing.html)

<!-- </nav> -->

# Tracing reference

Akka can collect traces for observing the runtime behavior of your services.

These trace spans are available for [export](../../operations/observability-and-monitoring/observability-exports.html) to external monitoring systems.

## <a href="about:blank#_http_endpoints"></a> HTTP Endpoints

### <a href="about:blank#_http_server_span"></a> HTTP server span

This span represents an inbound HTTP request.
Span kind:: server
Akka span kind:: `endpoint.request`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Endpoint attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.endpoint.method` | string | Yes | Name of the endpoint method being invoked. |
Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.debug.auto` | boolean | Conditionally required: when debug tracing is enabled | Whether debug mode was automatically enabled. |
| `akka.debug.id` | string | Conditionally required: when debug tracing is enabled | Debug identifier for correlating traces. |
Http Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `http.request.body.content` | string | Conditionally required: when debug tracing is enabled | Captured content of the HTTP request body. |
| `http.request.body.encoding` | string | Conditionally required: when debug tracing is enabled | Encoding format of the captured request body. |
| `http.request.body.type` | string | Conditionally required: when debug tracing is enabled | The expected type used to deserialize the request body. |
| `http.response.body.content` | string | Conditionally required: when debug tracing is enabled | Captured content of the HTTP response body. |
| `http.response.body.encoding` | string | Conditionally required: when debug tracing is enabled | Encoding format of the captured response body. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if and only if an error has occurred. | Describes the error condition, such as the HTTP status code. |
| `http.request.method` | string | Yes | HTTP request method. |
| `http.response.status_code` | int | Conditionally required: If and only if one was received/sent. | [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6). |
| `http.route` | string | Conditionally required: If and only if it’s available | The matched route template, e.g. `/pets/id`. |
| `server.address` | string | Recommended | The client-facing server hostname. |
| `url.path` | string | Recommended | The [URI path](https://www.rfc-editor.org/rfc/rfc3986#section-3.3) component |
| `url.query` | string | Recommended | The [URI query](https://www.rfc-editor.org/rfc/rfc3986#section-3.4) component |

## <a href="about:blank#_grpc_endpoints"></a> gRPC Endpoints

### <a href="about:blank#_grpc_server_span"></a> gRPC server span

This span represents an incoming gRPC server call.
Span kind:: server
Akka span kind:: `endpoint.request`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Endpoint attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.endpoint.method` | string | Yes | Name of the endpoint method being invoked. |
Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.debug.auto` | boolean | Conditionally required: when debug tracing is enabled | Whether debug mode was automatically enabled. |
| `akka.debug.id` | string | Conditionally required: when debug tracing is enabled | Debug identifier for correlating traces. |
Grpc Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `grpc.input.message.content` | string | Conditionally required: when debug tracing is enabled | Captured content of the gRPC input message (unary). |
| `grpc.input.message.type` | string | Conditionally required: when debug tracing is enabled | The fully qualified protobuf type name of the input message. |
| `grpc.input.stream.message.content` | string[] | Conditionally required: when debug tracing is enabled and input is streaming | Captured content of gRPC input stream messages. |
| `grpc.input.streaming` | boolean | Conditionally required: when debug tracing is enabled and input is streaming | Whether the input is a client stream. |
| `grpc.output.message.content` | string | Conditionally required: when debug tracing is enabled | Captured content of the gRPC output message (unary). |
| `grpc.output.message.type` | string | Conditionally required: when debug tracing is enabled | The fully qualified protobuf type name of the output message. |
| `grpc.output.stream.message.content` | string[] | Conditionally required: when debug tracing is enabled and output is streaming | Captured content of gRPC output stream messages. |
| `grpc.output.streaming` | boolean | Conditionally required: when debug tracing is enabled and output is streaming | Whether the output is a server stream. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if and only if an error has occurred. | Describes the error condition, such as the gRPC status code. |
| `rpc.method` | string | Yes | The gRPC service and method name, e.g. `com.example.MyService/MyMethod`. |
| `rpc.response.status_code` | string | Conditionally required: if available. | The gRPC status code. |
| `rpc.system.name` | enum | Yes | The Remote Procedure Call (RPC) system. |
| `server.address` | string | Recommended | The client-facing server hostname. |

## <a href="about:blank#_mcp_endpoints"></a> MCP Endpoints

### <a href="about:blank#_mcp_server_span"></a> MCP server span

This span represents an incoming MCP server operation.
Span kind:: server
Akka span kind:: `endpoint.request`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.debug.auto` | boolean | Conditionally required: when debug tracing is enabled | Whether debug mode was automatically enabled. |
| `akka.debug.id` | string | Conditionally required: when debug tracing is enabled | Debug identifier for correlating traces. |
Mcp Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `mcp.request.content` | string | Conditionally required: when debug tracing is enabled | Captured content of the MCP JSON-RPC request. |
| `mcp.response.content` | string | Conditionally required: when debug tracing is enabled | Captured content of the MCP JSON-RPC response. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if and only if an error has occurred. | Describes the error condition. |
| `gen_ai.operation.name` | enum | Conditionally required: when applicable for the MCP method | The name of the operation being performed. |
| `gen_ai.prompt.name` | string | Conditionally required: When operation is related to a specific prompt. | The MCP prompt name. |
| `gen_ai.tool.name` | string | Conditionally required: When operation is related to a specific tool. | The MCP tool name. |
| `jsonrpc.request.id` | string | Recommended | A string representation of the `id` property of the request and its corresponding response. |
| `mcp.method.name` | enum | Yes | The name of the request or notification method. |
| `mcp.resource.uri` | string | Conditionally required: when applicable for the MCP method | The value of the resource uri. |
| `rpc.response.status_code` | string | Conditionally required: if available. | The gRPC status code for the MCP transport. |

## <a href="about:blank#_agents"></a> Agents

### <a href="about:blank#_agent_command_span"></a> Agent command span

This span represents an agent command invocation.
Span kind:: server
Akka span kind:: `agent.command`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `gen_ai.agent.id` | string | Recommended | The unique identifier of the GenAI agent. |
| `gen_ai.agent.name` | string | Recommended | Human-readable name of the GenAI agent provided by the application. |
| `gen_ai.conversation.id` | string | Conditionally required: when available | The unique identifier for a conversation (session, thread), used to store and correlate messages within this conversation. |
| `gen_ai.operation.name` | enum | Yes | The name of the operation being performed. |

### <a href="about:blank#_agent_content_loading_span"></a> Agent content loading span

This span represents content loading within an agent invocation.
Span kind:: internal
Akka span kind:: `agent.content.loading`

Agent Content attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.agent.content.kind` | string | Yes | The kind of content being loaded. |
| `akka.agent.content.loader.class` | string | Recommended: if available | The class name of the content loader used. |
| `akka.agent.content.size` | int | Recommended: if available | The size of the loaded content in bytes. |
| `akka.agent.content.uri` | string | Yes | The URI of the content being loaded. |
Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_agent_guardrail_span"></a> Agent guardrail span

This span represents a guardrail evaluation within an agent invocation.
Span kind:: internal
Akka span kind:: `agent.guardrail`

Agent Guardrail attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.agent.guardrail.category` | string | Yes | The category of the guardrail being evaluated. |
| `akka.agent.guardrail.name` | string | Yes | The name of the guardrail being evaluated. |
| `akka.agent.guardrail.result` | enum | Yes | The result of a guardrail evaluation. |
Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_agent_model_call_span"></a> Agent model call span

This span represents an LLM model call within an agent invocation.
Span kind:: client
Akka span kind:: `agent.model.call`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `gen_ai.conversation.id` | string | Conditionally required: when available | The unique identifier for a conversation (session, thread), used to store and correlate messages within this conversation. |
| `gen_ai.input.messages` | any | Opt-in | The chat history provided to the model as an input. |
| `gen_ai.operation.name` | enum | Yes | The name of the operation being performed. |
| `gen_ai.output.messages` | any | Opt-in | Messages returned by the model where each message represents a specific model response (choice, candidate). |
| `gen_ai.output.type` | enum | Conditionally required: when applicable and if the request includes an output format. | Represents the content type requested by the client. |
| `gen_ai.provider.name` | enum | Yes | The Generative AI provider as identified by the client or server instrumentation. |
| `gen_ai.request.max_tokens` | int | Recommended | The maximum number of tokens the model generates for a request. |
| `gen_ai.request.model` | string | Conditionally required: If available. | The name of the GenAI model a request is being made to. |
| `gen_ai.request.temperature` | double | Recommended | The temperature setting for the GenAI request. |
| `gen_ai.request.top_k` | double | Recommended | The top_k sampling setting for the GenAI request. |
| `gen_ai.request.top_p` | double | Recommended | The top_p sampling setting for the GenAI request. |
| `gen_ai.response.finish_reasons` | string[] | Recommended | Array of reasons the model stopped generating tokens, corresponding to each generation received. |
| `gen_ai.response.id` | string | Recommended | The unique identifier for the completion. |
| `gen_ai.response.model` | string | Recommended | The name of the model that generated the response. |
| `gen_ai.usage.input_tokens` | int | Recommended | The number of tokens used in the GenAI input (prompt). |
| `gen_ai.usage.output_tokens` | int | Recommended | The number of tokens used in the GenAI response (completion). |

### <a href="about:blank#_agent_tool_call_span"></a> Agent tool call span

This span represents a tool execution within an agent invocation.
Span kind:: internal
Akka span kind:: `agent.tool.call`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Agent Mcp attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.agent.mcp.endpoint` | string | Conditionally required: when the tool is from an MCP endpoint | The MCP endpoint from which the tool was discovered. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `gen_ai.operation.name` | enum | Yes | The name of the operation being performed. |
| `gen_ai.tool.call.id` | string | Recommended | The tool call identifier. |
| `gen_ai.tool.description` | string | Recommended | The tool description. |
| `gen_ai.tool.name` | string | Conditionally required: When operation is related to a specific tool. | Name of the tool utilized by the agent. |
| `gen_ai.tool.type` | string | Recommended | Type of the tool utilized by the agent |

### <a href="about:blank#_agent_tool_discovery_span"></a> Agent tool discovery span

This span represents MCP tool discovery within an agent invocation.
Span kind:: internal
Akka span kind:: `agent.tool.discovery`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Agent Mcp attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.agent.mcp.endpoint` | string | Conditionally required: when the tool is from an MCP endpoint | The MCP endpoint from which the tool was discovered. |

## <a href="about:blank#_agents_openinference"></a> Agents (OpenInference)

### <a href="about:blank#_agent_command_span_openinference"></a> Agent command span (OpenInference)

This span represents an agent command invocation with OpenInference attributes.
Span kind:: server
Akka span kind:: `agent.command`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Open Inference attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.session.id` | string | Recommended: if available | The session identifier for the conversation. |
| `open_inference.span.kind` | enum | Yes | The kind of span in OpenInference semantic conventions. |
Open Inference Agent attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.agent.name` | string | Recommended: if available | The name of the agent. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `gen_ai.agent.id` | string | Recommended | The unique identifier of the GenAI agent. |
| `gen_ai.agent.name` | string | Recommended | Human-readable name of the GenAI agent provided by the application. |
| `gen_ai.conversation.id` | string | Conditionally required: when available | The unique identifier for a conversation (session, thread), used to store and correlate messages within this conversation. |
| `gen_ai.operation.name` | enum | Yes | The name of the operation being performed. |

### <a href="about:blank#_agent_guardrail_span_openinference"></a> Agent guardrail span (OpenInference)

This span represents a guardrail evaluation with OpenInference attributes.
Span kind:: internal
Akka span kind:: `agent.guardrail`

Agent Guardrail attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.agent.guardrail.category` | string | Yes | The category of the guardrail being evaluated. |
| `akka.agent.guardrail.name` | string | Yes | The name of the guardrail being evaluated. |
| `akka.agent.guardrail.result` | enum | Yes | The result of a guardrail evaluation. |
Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Open Inference attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.session.id` | string | Recommended: if available | The session identifier for the conversation. |
| `open_inference.span.kind` | enum | Yes | The kind of span in OpenInference semantic conventions. |
Open Inference Agent Guardrail attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.agent.guardrail.category` | string | Recommended: if available | The category of the guardrail being evaluated. |
| `open_inference.agent.guardrail.name` | string | Recommended: if available | The name of the guardrail being evaluated. |

### <a href="about:blank#_agent_model_call_span_openinference"></a> Agent model call span (OpenInference)

This span represents an LLM model call with OpenInference attributes.
Span kind:: client
Akka span kind:: `agent.model.call`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Open Inference attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.session.id` | string | Recommended: if available | The session identifier for the conversation. |
| `open_inference.span.kind` | enum | Yes | The kind of span in OpenInference semantic conventions. |
Open Inference Io attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.input.mime_type` | string | Recommended: if available | The MIME type of the input. |
| `open_inference.input.value` | string | Recommended: if available | The input value. |
| `open_inference.output.mime_type` | string | Recommended: if available | The MIME type of the output. |
| `open_inference.output.value` | string | Recommended: if available | The output value. |
Open Inference Llm attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.llm.input_messages` | string | Recommended: if available | JSON string of the input messages. |
| `open_inference.llm.invocation_parameters` | string | Recommended: if available | JSON string of the invocation parameters. |
| `open_inference.llm.model_name` | string | Recommended: if available | The name of the LLM model. |
| `open_inference.llm.output_messages` | string | Recommended: if available | JSON string of the output messages. |
| `open_inference.llm.provider` | string | Recommended: if available | The LLM provider name. |
| `open_inference.llm.response.finish_reasons` | string[] | Recommended: if available | The finish reasons from the LLM response. |
| `open_inference.llm.system` | string | Recommended: if available | The LLM system being used. |
| `open_inference.llm.token_count.completion` | int | Recommended: if available | The number of tokens in the completion. |
| `open_inference.llm.token_count.prompt` | int | Recommended: if available | The number of tokens in the prompt. |
| `open_inference.llm.token_count.total` | int | Recommended: if available | The total number of tokens. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `gen_ai.conversation.id` | string | Conditionally required: when available | The unique identifier for a conversation (session, thread), used to store and correlate messages within this conversation. |
| `gen_ai.input.messages` | any | Opt-in | The chat history provided to the model as an input. |
| `gen_ai.operation.name` | enum | Yes | The name of the operation being performed. |
| `gen_ai.output.messages` | any | Opt-in | Messages returned by the model where each message represents a specific model response (choice, candidate). |
| `gen_ai.output.type` | enum | Conditionally required: when applicable and if the request includes an output format. | Represents the content type requested by the client. |
| `gen_ai.provider.name` | enum | Yes | The Generative AI provider as identified by the client or server instrumentation. |
| `gen_ai.request.max_tokens` | int | Recommended | The maximum number of tokens the model generates for a request. |
| `gen_ai.request.model` | string | Conditionally required: If available. | The name of the GenAI model a request is being made to. |
| `gen_ai.request.temperature` | double | Recommended | The temperature setting for the GenAI request. |
| `gen_ai.request.top_k` | double | Recommended | The top_k sampling setting for the GenAI request. |
| `gen_ai.request.top_p` | double | Recommended | The top_p sampling setting for the GenAI request. |
| `gen_ai.response.finish_reasons` | string[] | Recommended | Array of reasons the model stopped generating tokens, corresponding to each generation received. |
| `gen_ai.response.id` | string | Recommended | The unique identifier for the completion. |
| `gen_ai.response.model` | string | Recommended | The name of the model that generated the response. |
| `gen_ai.usage.input_tokens` | int | Recommended | The number of tokens used in the GenAI input (prompt). |
| `gen_ai.usage.output_tokens` | int | Recommended | The number of tokens used in the GenAI response (completion). |

### <a href="about:blank#_agent_tool_call_span_openinference"></a> Agent tool call span (OpenInference)

This span represents a tool execution with OpenInference attributes.
Span kind:: internal
Akka span kind:: `agent.tool.call`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Open Inference attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.session.id` | string | Recommended: if available | The session identifier for the conversation. |
| `open_inference.span.kind` | enum | Yes | The kind of span in OpenInference semantic conventions. |
Open Inference Tool attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.tool.description` | string | Recommended: if available | The description of the tool. |
| `open_inference.tool.name` | string | Recommended: if available | The name of the tool. |
| `open_inference.tool_call.id` | string | Recommended: if available | The identifier of the tool call. |
Agent Mcp attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.agent.mcp.endpoint` | string | Conditionally required: when the tool is from an MCP endpoint | The MCP endpoint from which the tool was discovered. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `gen_ai.operation.name` | enum | Yes | The name of the operation being performed. |
| `gen_ai.tool.call.id` | string | Recommended | The tool call identifier. |
| `gen_ai.tool.description` | string | Recommended | The tool description. |
| `gen_ai.tool.name` | string | Conditionally required: When operation is related to a specific tool. | Name of the tool utilized by the agent. |
| `gen_ai.tool.type` | string | Recommended | Type of the tool utilized by the agent |

### <a href="about:blank#_agent_tool_discovery_span_openinference"></a> Agent tool discovery span (OpenInference)

This span represents MCP tool discovery with OpenInference attributes.
Span kind:: internal
Akka span kind:: `agent.tool.discovery`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Open Inference attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `open_inference.session.id` | string | Recommended: if available | The session identifier for the conversation. |
| `open_inference.span.kind` | enum | Yes | The kind of span in OpenInference semantic conventions. |
Agent Mcp attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.agent.mcp.endpoint` | string | Conditionally required: when the tool is from an MCP endpoint | The MCP endpoint from which the tool was discovered. |

## <a href="about:blank#_workflows"></a> Workflows

### <a href="about:blank#_workflow_command_span"></a> Workflow command span

This span represents a workflow command invocation.
Span kind:: server
Akka span kind:: `workflow.command`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Workflow Command attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.command.name` | string | Yes | The name of the workflow command. |
Workflow Span attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.id` | string | Yes | The workflow ID. |
Workflow Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.command.content` | string | Conditionally required: when debug tracing is enabled | The JSON content of the workflow command payload. |
| `akka.workflow.command.type` | string | Conditionally required: when debug tracing is enabled | The protobuf type name of the workflow command payload. |
| `akka.workflow.reply.content` | string | Conditionally required: when debug tracing is enabled | The JSON content of the workflow reply payload. |
| `akka.workflow.reply.type` | string | Conditionally required: when debug tracing is enabled | The protobuf type name of the workflow reply payload. |

### <a href="about:blank#_workflow_step_span"></a> Workflow step span

This span represents a workflow step execution.
Span kind:: server
Akka span kind:: `workflow.step`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Workflow Span attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.id` | string | Yes | The workflow ID. |
Workflow Step attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.step.attempt` | int | Yes | The attempt number for the workflow step. |
| `akka.workflow.step.id` | int | Yes | The ID of the workflow step. |
| `akka.workflow.step.name` | string | Yes | The name of the workflow step. |
Workflow Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.step.input.content` | string | Conditionally required: when debug tracing is enabled | The JSON content of the workflow step input payload. |
| `akka.workflow.step.input.type` | string | Conditionally required: when debug tracing is enabled | The protobuf type name of the workflow step input payload. |

## <a href="about:blank#_views"></a> Views

### <a href="about:blank#_view_query_span"></a> View query span

This span represents a view query execution.
Span kind:: internal
Akka span kind:: `view.query`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.query` | string | Conditionally required: when debug tracing is enabled | The view query string. |

### <a href="about:blank#_view_update_span"></a> View update span

This span represents a view update processing invocation.
Span kind:: internal
Akka span kind:: `view.update`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Consumer attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.consumer.source` | enum | Yes | The type of source the consumer is consuming from. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |
Consumer Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.consumer.message.content` | string | Conditionally required: when debug tracing is enabled | The JSON content of the consumer message payload. |
| `akka.consumer.message.type` | string | Conditionally required: when debug tracing is enabled | The protobuf type name of the consumer message payload. |
| `akka.consumer.output.content` | string | Conditionally required: when debug tracing is enabled | The JSON content of the consumer output payload. |
| `akka.consumer.output.type` | string | Conditionally required: when debug tracing is enabled | The protobuf type name of the consumer output payload. |
Consumer Span attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.consumer.source.group` | string | Conditionally required: when the source has a consumer group | The consumer group for the source. |
| `akka.consumer.source.id` | string | Conditionally required: when debug tracing is enabled | The identifier of the consumer source. |
| `akka.consumer.subject` | string | Conditionally required: when the message has a subject | The subject of the consumer message. |

## <a href="about:blank#_consumers"></a> Consumers

### <a href="about:blank#_consumer_message_span"></a> Consumer message span

This span represents a consumer message processing invocation.
Span kind:: server
Akka span kind:: `consumer.message`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Consumer attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.consumer.source` | enum | Yes | The type of source the consumer is consuming from. |
Consumer Debug attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.consumer.message.content` | string | Conditionally required: when debug tracing is enabled | The JSON content of the consumer message payload. |
| `akka.consumer.message.type` | string | Conditionally required: when debug tracing is enabled | The protobuf type name of the consumer message payload. |
| `akka.consumer.output.content` | string | Conditionally required: when debug tracing is enabled | The JSON content of the consumer output payload. |
| `akka.consumer.output.destination` | string | Conditionally required: when debug tracing is enabled and output has a destination | The destination for the consumer output. |
| `akka.consumer.output.type` | string | Conditionally required: when debug tracing is enabled | The protobuf type name of the consumer output payload. |
Consumer Span attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.consumer.source.group` | string | Conditionally required: when the source has a consumer group | The consumer group for the source. |
| `akka.consumer.source.id` | string | Conditionally required: when debug tracing is enabled | The identifier of the consumer source. |
| `akka.consumer.subject` | string | Conditionally required: when the message has a subject | The subject of the consumer message. |

<!-- <footer> -->
<!-- <nav> -->
[(Deprecated) Prometheus metrics reference](deprecated-metrics.html) [Akka Libraries](../../libraries/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
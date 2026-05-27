<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Telemetry reference](index.html)
- [Metrics reference](metrics.html)

<!-- </nav> -->

# Metrics reference

Akka collects metrics for monitoring the runtime behavior of your services.

These metrics are available for [export](../../operations/observability-and-monitoring/observability-exports.html) to external monitoring systems.

In addition to the Akka-specific metrics listed below, standard JVM runtime metrics (memory, garbage collection, threads, class loading, CPU, etc.) are also collected and exported. These follow the OpenTelemetry semantic conventions for JVM metrics — see [OpenTelemetry JVM metrics](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/) for the full list and attribute definitions.

## <a href="about:blank#_http_endpoints"></a> HTTP Endpoints

### <a href="about:blank#_http_server_active_requests"></a> HTTP server active requests

Number of active HTTP server requests.
Metric:: `http.server.active_requests` Type:: up-down counter
Unit:: `{request}`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `http.request.method` | string | Yes | HTTP request method. |
| `http.route` | string | Conditionally required: If and only if it’s available | The matched route template, e.g. `/pets/{id}`. |
| `server.address` | string | Recommended | The client-facing server hostname. |

### <a href="about:blank#_http_server_request_body_size"></a> HTTP server request body size

Size of HTTP server request bodies.
Metric:: `http.server.request.body.size` Type:: histogram
Unit:: `By`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `http.request.method` | string | Yes | HTTP request method. |
| `http.route` | string | Conditionally required: If and only if it’s available | The matched route template, e.g. `/pets/{id}`. |
| `server.address` | string | Recommended | The client-facing server hostname. |

### <a href="about:blank#_http_server_request_duration"></a> HTTP server request duration

Duration of HTTP server requests.
Metric:: `http.server.request.duration` Type:: histogram
Unit:: `s`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if and only if an error has occurred. | Describes the error condition, such as the HTTP status code. |
| `http.request.method` | string | Yes | HTTP request method. |
| `http.response.status_code` | int | Conditionally required: If and only if one was received/sent. | [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6). |
| `http.route` | string | Conditionally required: If and only if it’s available | The matched route template, e.g. `/pets/{id}`. |
| `server.address` | string | Recommended | The client-facing server hostname. |

### <a href="about:blank#_http_server_response_body_size"></a> HTTP server response body size

Size of HTTP server response bodies.
Metric:: `http.server.response.body.size` Type:: histogram
Unit:: `By`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if and only if an error has occurred. | Describes the error condition, such as the HTTP status code. |
| `http.request.method` | string | Yes | HTTP request method. |
| `http.response.status_code` | int | Conditionally required: If and only if one was received/sent. | [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6). |
| `http.route` | string | Conditionally required: If and only if it’s available | The matched route template, e.g. `/pets/{id}`. |
| `server.address` | string | Recommended | The client-facing server hostname. |

## <a href="about:blank#_grpc_endpoints"></a> gRPC Endpoints

### <a href="about:blank#_rpc_server_call_duration"></a> RPC server call duration

Duration of incoming gRPC server calls.
Metric:: `rpc.server.call.duration` Type:: histogram
Unit:: `s`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if and only if an error has occurred. | Describes the error condition, such as the gRPC status code. |
| `rpc.method` | string | Yes | The gRPC service and method name, e.g. `com.example.MyService/MyMethod`. |
| `rpc.response.status_code` | string | Conditionally required: if available. | The gRPC status code. |
| `rpc.system.name` | enum | Yes | The Remote Procedure Call (RPC) system. |
| `server.address` | string | Recommended | The client-facing server hostname. |

### <a href="about:blank#_rpc_server_response_size"></a> RPC server response size

Size of RPC server response messages.
Metric:: `rpc.server.response.size` Type:: histogram
Unit:: `By`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if and only if an error has occurred. | Describes the error condition, such as the gRPC status code. |
| `rpc.method` | string | Yes | The gRPC service and method name, e.g. `com.example.MyService/MyMethod`. |
| `rpc.response.status_code` | string | Conditionally required: if available. | The gRPC status code. |
| `rpc.system.name` | enum | Yes | The Remote Procedure Call (RPC) system. |
| `server.address` | string | Recommended | The client-facing server hostname. |

## <a href="about:blank#_mcp_endpoints"></a> MCP Endpoints

### <a href="about:blank#_mcp_server_operation_duration"></a> MCP server operation duration

Duration of incoming MCP server operations.
Metric:: `mcp.server.operation.duration` Type:: histogram
Unit:: `s`

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
| `error.type` | string | Conditionally required: if and only if an error has occurred. | Describes the error condition. |
| `gen_ai.operation.name` | enum | Conditionally required: when applicable for the MCP method | The name of the operation being performed. |
| `gen_ai.prompt.name` | string | Conditionally required: When operation is related to a specific prompt. | The MCP prompt name. |
| `gen_ai.tool.name` | string | Conditionally required: When operation is related to a specific tool. | The MCP tool name. |
| `mcp.method.name` | enum | Yes | The name of the request or notification method. |
| `rpc.response.status_code` | string | Conditionally required: if available. | The gRPC status code for the MCP transport. |

## <a href="about:blank#_agents"></a> Agents

### <a href="about:blank#_agent_command_duration"></a> Agent command duration

Duration of agent command handling.
Metric:: `akka.agent.command.duration` Type:: histogram
Unit:: `s`

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
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_agent_content_load_duration"></a> Agent content load duration

Duration of agent content loading operations.
Metric:: `akka.agent.content.load.duration` Type:: histogram
Unit:: `s`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_agent_content_load_size"></a> Agent content load size

Size of content loaded by the agent.
Metric:: `akka.agent.content.load.size` Type:: histogram
Unit:: `By`

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

### <a href="about:blank#_agent_evaluation_result"></a> Agent evaluation result

Number of agent evaluation results.
Metric:: `akka.agent.evaluation.result` Type:: counter
Unit:: `{result}`

Agent Evaluation attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.agent.evaluation.result` | enum | Yes | The result of an agent evaluation. |
Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_agent_guardrail_evaluation_duration"></a> Agent guardrail evaluation duration

Duration of agent guardrail evaluations.
Metric:: `akka.agent.guardrail.duration` Type:: histogram
Unit:: `s`

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

### <a href="about:blank#_genai_client_operation_duration"></a> GenAI client operation duration

Duration of GenAI client operations.
Metric:: `gen_ai.client.operation.duration` Type:: histogram
Unit:: `s`

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
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |
| `gen_ai.operation.name` | enum | Yes | The name of the operation being performed. |
| `gen_ai.provider.name` | enum | Yes | The Generative AI provider as identified by the client or server instrumentation. |
| `gen_ai.request.model` | string | Conditionally required: If available. | The name of the GenAI model a request is being made to. |
| `gen_ai.tool.name` | string | Conditionally required: when gen_ai.operation.name is execute_tool | Name of the tool utilized by the agent. |

### <a href="about:blank#_genai_client_token_usage"></a> GenAI client token usage

Number of input and output tokens used in GenAI operations.
Metric:: `gen_ai.client.token.usage` Type:: histogram
Unit:: `{token}`

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
| `gen_ai.operation.name` | enum | Yes | The name of the operation being performed. |
| `gen_ai.provider.name` | enum | Yes | The Generative AI provider as identified by the client or server instrumentation. |
| `gen_ai.request.model` | string | Conditionally required: If available. | The name of the GenAI model a request is being made to. |
| `gen_ai.token.type` | enum | Yes | The type of token being counted. |

## <a href="about:blank#_workflows"></a> Workflows

### <a href="about:blank#_active_workflows"></a> Active workflows

Number of currently active workflow instances.
Metric:: `akka.workflow.active` Type:: up-down counter
Unit:: `{workflow}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_workflow_active_duration"></a> Workflow active duration

Duration that a workflow instance was active (from activation to passivation or failure).
Metric:: `akka.workflow.active.duration` Type:: histogram
Unit:: `s`

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
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_workflow_command_duration"></a> Workflow command duration

Total duration of workflow command handling (from received to completed).
Metric:: `akka.workflow.command.duration` Type:: histogram
Unit:: `s`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_workflow_command_processing_duration"></a> Workflow command processing duration

Duration of workflow command processing in the user function (excludes persist).
Metric:: `akka.workflow.command.processing.duration` Type:: histogram
Unit:: `s`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_workflow_conflicts_detected"></a> Workflow conflicts detected

Number of workflow replication conflicts detected.
Metric:: `akka.workflow.conflicts_detected` Type:: counter
Unit:: `{conflict}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_workflow_conflicts_resolved"></a> Workflow conflicts resolved

Number of workflow replication conflicts resolved.
Metric:: `akka.workflow.conflicts_resolved` Type:: counter
Unit:: `{conflict}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_workflow_loaded_size"></a> Workflow loaded size

Size in bytes of loaded workflow data (events, snapshots) during recovery. Each record represents one loaded item.
Metric:: `akka.workflow.loaded.size` Type:: histogram
Unit:: `By`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Workflow Persistence attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.persistence.type` | enum | Yes | The type of workflow persistence operation. |

### <a href="about:blank#_workflow_persist_duration"></a> Workflow persist duration

Duration of workflow persistence operations.
Metric:: `akka.workflow.persist.duration` Type:: histogram
Unit:: `s`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_workflow_persisted_size"></a> Workflow persisted size

Size in bytes of persisted workflow data (events, snapshots, replicated events). Each record represents one persisted item.
Metric:: `akka.workflow.persisted.size` Type:: histogram
Unit:: `By`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Workflow Persistence attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.persistence.type` | enum | Yes | The type of workflow persistence operation. |

### <a href="about:blank#_workflow_recovery_duration"></a> Workflow recovery duration

Duration of workflow recovery.
Metric:: `akka.workflow.recovery.duration` Type:: histogram
Unit:: `s`

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
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_workflow_recovery_events_loaded"></a> Workflow recovery events loaded

Number of workflow recoveries that loaded from events (not snapshot-only).
Metric:: `akka.workflow.recovery.events_loaded` Type:: counter
Unit:: `{recovery}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_workflows_registered_for_deletion"></a> Workflows registered for deletion

Number of workflows registered for deletion.
Metric:: `akka.workflow.registered_for_deletion` Type:: counter
Unit:: `{workflow}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_workflow_step_duration"></a> Workflow step duration

Duration of workflow step execution.
Metric:: `akka.workflow.step.duration` Type:: histogram
Unit:: `s`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Workflow Step attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.step.id` | int | Yes | The ID of the workflow step. |
| `akka.workflow.step.name` | string | Yes | The name of the workflow step. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_workflow_step_processing_duration"></a> Workflow step processing duration

Duration of workflow step processing in the user function.
Metric:: `akka.workflow.step.processing.duration` Type:: histogram
Unit:: `s`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
Workflow Step attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.workflow.step.id` | int | Yes | The ID of the workflow step. |
| `akka.workflow.step.name` | string | Yes | The name of the workflow step. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_workflow_transition_duration"></a> Workflow transition duration

Total duration of workflow transition handling (from started to completed).
Metric:: `akka.workflow.transition.duration` Type:: histogram
Unit:: `s`

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
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_workflow_transition_processing_duration"></a> Workflow transition processing duration

Duration of workflow transition processing in the user function.
Metric:: `akka.workflow.transition.processing.duration` Type:: histogram
Unit:: `s`

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
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

## <a href="about:blank#_views"></a> Views

### <a href="about:blank#_view_conflict_resolution_duration"></a> View conflict resolution duration

Total duration of view conflict resolution.
Metric:: `akka.view.conflict_resolution.duration` Type:: histogram
Unit:: `s`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_view_conflict_resolutions"></a> View conflict resolutions

Number of view conflict resolutions completed.
Metric:: `akka.view.conflict_resolutions` Type:: counter
Unit:: `{resolution}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |

### <a href="about:blank#_view_load_rows"></a> View load rows

Number of rows loaded from the view store.
Metric:: `akka.view.load.rows` Type:: counter
Unit:: `{row}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |

### <a href="about:blank#_view_load_size"></a> View load size

Size in bytes per loaded view entry.
Metric:: `akka.view.load.size` Type:: histogram
Unit:: `By`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |

### <a href="about:blank#_view_query_byte_limit_exceeded"></a> View query byte limit exceeded

Number of view queries that exceeded the byte limit for loading from the view store.
Metric:: `akka.view.query.byte_limit_exceeded` Type:: counter
Unit:: `{error}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_view_query_duration"></a> View query duration

Total duration of view query execution.
Metric:: `akka.view.query.duration` Type:: histogram
Unit:: `s`

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
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_view_query_result_rows"></a> View query result rows

Number of rows returned per view query.
Metric:: `akka.view.query.result.rows` Type:: histogram
Unit:: `{row}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_view_query_result_size"></a> View query result size

Size in bytes of view query results.
Metric:: `akka.view.query.result.size` Type:: histogram
Unit:: `By`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_view_query_row_limit_exceeded"></a> View query row limit exceeded

Number of view queries that exceeded the row limit for loading from the view store.
Metric:: `akka.view.query.row_limit_exceeded` Type:: counter
Unit:: `{error}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_view_update_duration"></a> View update duration

Total duration of view update handling.
Metric:: `akka.view.update.duration` Type:: histogram
Unit:: `s`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |
View Update Outcome attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.update.outcome` | enum | Yes | The outcome of the view update. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_view_update_load_duration"></a> View update load duration

Duration for loading existing entries from the view store.
Metric:: `akka.view.update.load.duration` Type:: histogram
Unit:: `s`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_view_update_sequence_gaps"></a> View update sequence gaps

Number of sequence gaps detected in view updates.
Metric:: `akka.view.update.sequence_gaps` Type:: counter
Unit:: `{gap}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |

### <a href="about:blank#_view_update_store_duration"></a> View update store duration

Duration for storing updates in the view store.
Metric:: `akka.view.update.store.duration` Type:: histogram
Unit:: `s`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_view_update_transform_duration"></a> View update transform duration

Duration for transforming updates in the user service.
Metric:: `akka.view.update.transform.duration` Type:: histogram
Unit:: `s`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_view_upsert_byte_limit_exceeded"></a> View upsert byte limit exceeded

Number of view upsert operations that exceeded the byte size limit.
Metric:: `akka.view.upsert.byte_limit_exceeded` Type:: counter
Unit:: `{error}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

### <a href="about:blank#_view_upsert_rows"></a> View upsert rows

Number of rows upserted to the view store.
Metric:: `akka.view.upsert.rows` Type:: counter
Unit:: `{row}`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |

### <a href="about:blank#_view_upsert_size"></a> View upsert size

Size in bytes per upserted view entry.
Metric:: `akka.view.upsert.size` Type:: histogram
Unit:: `By`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |
View Update attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.view.table` | string | Yes | The view table name. |

## <a href="about:blank#_consumers"></a> Consumers

### <a href="about:blank#_consumer_message_duration"></a> Consumer message duration

Duration of consumer message processing.
Metric:: `akka.consumer.message.duration` Type:: histogram
Unit:: `s`

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
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

## <a href="about:blank#_timers"></a> Timers

### <a href="about:blank#_timer_call_duration"></a> Timer call duration

Duration of timer call executions.
Metric:: `akka.timer.call.duration` Type:: histogram
Unit:: `s`

OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_timer_operation_duration"></a> Timer operation duration

Duration of internal timer operations (register, unregister, confirm, max_retries).
Metric:: `akka.timer.operation.duration` Type:: histogram
Unit:: `s`

Timer Operation attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.timer.operation` | enum | Yes | The type of timer operation. |
OpenTelemetry semantic convention attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `error.type` | string | Conditionally required: if the operation ended in an error | Describes the error condition. |

### <a href="about:blank#_timer_persisted_size"></a> Timer persisted size

Size in bytes of persisted timer data. Each record represents one persisted item.
Metric:: `akka.timer.persisted.size` Type:: histogram
Unit:: `By`

### <a href="about:blank#_registered_timers"></a> Registered timers

Number of currently registered timer calls per worker.
Metric:: `akka.timer.registered` Type:: gauge
Unit:: `{timer}`

Timer Worker attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.timer.worker_id` | string | Yes | Identifier of the timer worker actor (shard). |

## <a href="about:blank#_timed_actions"></a> Timed Actions

### <a href="about:blank#_timed_action_message_duration"></a> Timed action message duration

Duration of timed action message processing.
Metric:: `akka.timed_action.message.duration` Type:: histogram
Unit:: `s`

Component attributes
| Attribute | Type | Required | Description |
| --- | --- | --- | --- |
| `akka.component.description` | string | Recommended: if available | User-defined description of the component. |
| `akka.component.id` | string | Yes | Unique identifier for the component instance. |
| `akka.component.implementation.name` | string | Recommended: if available | Fully qualified class name of the component implementation. |
| `akka.component.name` | string | Recommended: if available | User-defined name of the component. |
| `akka.component.type` | enum | Yes | The type of Akka component. |

<!-- <footer> -->
<!-- <nav> -->
[Telemetry reference](index.html) [(Deprecated) Prometheus metrics reference](deprecated-metrics.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
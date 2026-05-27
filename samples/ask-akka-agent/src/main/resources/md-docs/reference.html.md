<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Service reference configuration (HOCON)](reference.html)

<!-- </nav> -->

# Service reference configuration (HOCON)

## <a href="about:blank#_akka_sdk_reference_configuration"></a> Akka SDK reference configuration

Akka SDK Services are configured through configuration files in the [HOCON format](https://github.com/lightbend/config?tab=readme-ov-file#using-hocon-the-json-superset).

Below shows the complete default configuration for Akka Services.

For configuration of AI model providers, please refer to [AI model provider configuration](../../sdk/model-provider-details.html).

```hocon
# This is the reference config file that contains the default settings.
# Make your edits/overrides in your application.conf.

akka.javasdk {
  dev-mode {

    # the port it will use when running locally
    http-port = 9000

    # defaults to empty, but maven will set akka.javasdk.dev-mode.project-artifact-id to ${project.artifactId}
    # this is only filled in dev-mode, in prod the name will be the one chosen when the service is created
    # users can override this in their application.conf
    service-name = ""
    service-name =${?akka.javasdk.dev-mode.project-artifact-id}


    eventing {
      # Valid options are: "none", "/dev/null", "logging", "google-pubsub", "kafka",  "google-pubsub-emulator" and "eventhubs"
      support = "none"

      # The configuration for kafka brokers
      kafka {
        # One or more bootstrap servers, comma separated.
        bootstrap-servers = "localhost:9092"

        # Supported are
        # NONE (for easy local/dev mode with no auth at all)
        # PLAIN (for easy local/dev mode - plaintext, for non dev-mode TLS)
        # SCRAM-SHA-256 and SCRAM-SHA-512 (TLS)
        auth-mechanism = "NONE"
        auth-username = ""
        auth-password = ""
        broker-ca-pem-file = ""
      }
    }

    object-storage {
      # Named bucket configurations for dev mode. When this list is empty (the default), any bucket
      # name is accepted and objects are stored under a shared local filesystem directory.
      # When one or more buckets are configured here, only those named buckets are accessible
      # (same strict-matching behaviour as production).
      #
      # Each entry requires a "name" and a "provider". Supported providers:
      #   filesystem  - local directory (always available, no credentials needed)
      #   s3          - Amazon S3 or compatible (e.g. MinIO); requires bucket, region and credentials
      #   gcs         - Google Cloud Storage; requires bucket and credentials
      #
      # filesystem example:
      #   { name = "my-images", provider = "filesystem" }
      #   { name = "my-images", provider = "filesystem", directory = "/tmp/my-images" }
      #
      # s3 example (static credentials, suitable for MinIO or a real S3 bucket with API keys):
      #   { name = "my-data", provider = "s3", bucket = "real-bucket", region = "us-east-1"
      #     credentials { type = "static", access-key-id = "AKID", secret-access-key = "secret" } }
      #
      # gcs example (service-account key file):
      #   { name = "my-docs", provider = "gcs", bucket = "real-bucket"
      #     credentials { type = "service-account-key", path = "/secrets/sa-key.json" } }
      buckets = []
    }

    acl {
      # Whether ACL checking is enabled
      enabled = true
    }

    persistence {
      # Whether persistence is enabled
      enabled = false
    }

    backoffice {
      # The refresh token. Will attempt to read it by running the Akka CLI command if not set
      refresh-token = ""
      refresh-token = ${?AKKA_REFRESH_TOKEN}

      # The api server. If not set, will detect it by running the Akka CLI, or default to api.kalix.io:443
      api-server = ""
      api-server = ${?AKKA_API_SERVER}

      # The context to use when running Akka CLI commands. Uses the default context if not set.
      cli-context = ""

      # The path of the Akka CLI. If not set, will default to looking for akka.exe on Windows and akka on other OS's on
      # the systems configured PATH.
      cli-path = ""

      # Whether backoffice services are enabled. This is true by default so that all a user needs to do is
      # configure the backoffice services to enable it. This flag then serves as a convenient means to disable
      # backoffice support when backoffice services are configured.
      enabled = true
      enabled = ${?AKKA_BACKOFFICE_SERVICES_ENABLED}

      # Timeout for making requests on the API server
      request-timeout = 10s

      services {
        # Specify services that should delegate to the cloud
        # "some-service-name" {
        #    # Optional, if set will override the service name to use in the cloud
        #    # service-name = "my-service"
        #
        #    # The project to use. May be a project id, or friendly name.
        #    project = "my-project"
        #
        #    # The organization. Only needed if referring to a project by friendly name, but multiple projects
        #    # from different organizations that the user is a member of have the same friendly name. May be the
        #    # organization id or friendly name
        #    # organization = "my-organization"
        #
        #    # The region. Optional, only needed for multi-region projects if a region other than the primary region
        #    # should be used.
        #    # region = "my-region"
        # }
      }
    }
  }

  testkit {
    # The port used by the testkit when running integration tests
    http-port = 39390
  }

  agent {
    # The default model provider that is used if an Agent doesn't specify a specific model.
    # References a config section for the model provider, such as anthropic or openai.
    model-provider = ""

    # Configuration for the session history (memory) between an Agent and the LLM model
    memory {
      # By default, the session history is turned on for all agents. It can be turned off with this setting.
      enabled = true

      # The maximum size of the memory window for the session history.
      # This is calculated as the sum of all messages content length in bytes.
      # Once the limit is reached, older messages will be automatically removed in a FIFO approach.
      # The default value is 510 KiB and this is actually the maximum value allowed. This is due to the fact that these
      # messages might be routed around the Akka cluster and as such some resource contraints apply.
      limited-window.max-size = 510 KiB
    }

    # Additional HTTP headers to include in each request to the model API.
    # Format: list of "name:value" strings, e.g. ["Authorization:Bearer token", "X-Custom-Header:value"]
    # This global setting is inherited by each provider config and can be overridden per provider.
    # Can also be set via environment variables, using a special naming where each entry is specified with a list index:
    # ADDITIONAL_MODEL_REQUEST_HEADERS_0="Authorization:Bearer token ..."
    # ADDITIONAL_MODEL_REQUEST_HEADERS_1="X-Custom-Header:value"
    additional-model-request-headers = []
    additional-model-request-headers = ${?ADDITIONAL_MODEL_REQUEST_HEADERS[]}

    # Inside a single request/response cycle, an LLM can successively request the agent to call functions tools.
    # After analysing the result of a tool call, the LLM might decide to request another call to gather more context.
    # This setting limits how many such steps may occur between a user request and the final Ai response.
    # Once this limit is reached, the process will stop even if the LLM has not yet produced its final response.
    max-tool-call-steps = 100

    # Guardrails are enabled by this configuration. Each guardrail is a named config section and it must have
    # the following mandatory properties:
    # - class: implementation class of the guardrail, must implement akka.javasdk.agent.TextGuardrail, be public and
    #          have a public constructor, optionally with a akka.javasdk.agent.GuardrailContext constructor parameter,
    #          which includes the config section for the specific guardrail
    # - category: the type of validation, such as JAILBREAK, PROMPT_INJECTION, PII, TOXIC, HALLUCINATED, NSFW, FORMAT
    # - report-only: if it didn't pass the evaluation criteria, the execution can either be aborted by
    #                throwing Guardrail.GuardrailException or continue anyway. In both cases, the result is tracked in
    #                logs, metrics and traces
    # - use-for: where to use the guardrail, list of possible values are model-request, model-response,
    #            mcp-tool-request, mcp-tool-response, "*"
    #
    # Additionally, to enable the guardrail specify one or both lists of:
    # - agents: enabled for agents with these component ids
    # - agent-roles: enabled for agents with these roles
    #
    # If both agents and agent-roles are defined it's enough that one of them matches to enable the guardrail for
    # an agent.
    #
    # If agents contain "*" the guardrail is enabled for all agents.
    # If agent-roles contain "*" the guardrail is enabled for all agents that has a role, but not for agents without
    # a role.
    #
    # An agent implementation can have additional configuration properties.
    guardrails {

      "default jailbreak" {
        class = "akka.javasdk.agent.SimilarityGuard"
        # not enabled until agents or agent-roles are defined
        agents = []
        agent-roles = []
        category = JAILBREAK
        report-only = false
        use-for = ["model-request"]
        threshold = 0.75
        bad-examples-resource-dir = "guardrail/jailbreak"
      }

    }

    evaluators {
      toxicity-evaluator {
        model-provider = ${akka.javasdk.agent.model-provider}
      }

      summarization-evaluator {
        model-provider = ${akka.javasdk.agent.model-provider}
      }

      hallucination-evaluator {
        model-provider = ${akka.javasdk.agent.model-provider}
      }

    }

    # Settings for autonomous agents
    autonomous {
      # Default maximum iterations per task before the agent fails it.
      # Can be overridden per task group via TaskAcceptance.maxIterationsPerTask().
      max-iterations-per-task = 10

      delegation {
        # Default maximum number of worker agents that can execute delegated subtasks concurrently.
        # Can be overridden per delegation via Delegation.maxParallelWorkers().
        max-parallel-workers = 3
      }
    }

    # All agent interactions with the model, including tool calls, are stored in an interaction log.
    # The purpose is for visibility in the console, troubleshooting, and auditing.
    # This has a performance overhead, but compared to the LLM response times it is typically
    # neglectible. It can be disabled with this configuration. It will always be enabled in local
    # dev mode since it's useful insights in the local console.
    interaction-log {
      enabled = true
    }
  }

  entity {
    # When a EventSourcedEntity, KeyValueEntity or Workflow is deleted the existence of the entity is completely cleaned up after
    # this duration. The events and snapshots will be deleted later to give downstream consumers time to process all
    # prior events, including final deleted event. Default is 7 days.
    cleanup-deleted-after =  ${akka.javasdk.event-sourced-entity.cleanup-deleted-after}
  }

  delete-entity.cleanup-interval = 1 hour

  event-sourced-entity {
    # It is strongly recommended to not disable snapshotting unless it is known that
    # event sourced entities will never have more than 100 events (in which case
    # the default will anyway not trigger any snapshots)
    snapshot-every = 100

    # Deprecated, use akka.javasdk.entity.cleanup-deleted-after
    cleanup-deleted-after = 7 days
  }

  eventing {
    google-pubsub {
      # Possible values:
      #  * automatic - runtime creates topic and subscription if they do not exist
      #  * automatic-subscription - runtime creates subscription if it do not exist, topic must be manually created
      #  * manual - both topic and subscription must be manually created
      mode = "automatic-subscription"
    }

    # All eventing of the service will start from this timestamp and ignore older events. Setting this to a value
    # leads to the timestamp being persisted for all consumers and views in the service, the only way to start consumers
    # and views after having set this, is to reset to empty, and use new view and consumer ids.
    # This is a migration feature and should not generally be used without a very good understanding of the
    # consequences - using it incorrectly can lead to data loss in views and consumers.
    # Accepted format is an ISO-8601 instant, for example "2026-02-17T16:50:00Z"
    # Note: this also applies to dev mode.
    start-from-timestamp = ""
  }

  discovery {
    # By default all environment variables of the process are passed along to the runtime, they are used only for
    # substitution in the descriptor options such as topic names. To selectively pick only a few variables,
    # this setting needs to be set to false and `pass-along-env-allow` should be configured with
    # a list of variables we want to pass along.
    pass-along-env-all = true

    # By default all environment variables of the process are passed along to the runtime, they are used only for
    # substitution in the descriptor options such as topic names. This setting can
    # limit which variables are passed configuring this as a list of allowed names:
    # pass-along-env-allow = ["ENV_NAME1", "ENV_NAME2"]
    # This setting only take effect if pass-along-env-all is set to false, otherwise all env variables will be pass along.
    # To disable any environment variable pass along, this setting needs to be an empty list pass-along-env-allow = []
    # and pass-along-env-all = false
    pass-along-env-allow = []
  }

  grpc.client {
    # Specify entries for the full service DNS names to apply
    # customizations for interacting with external gRPC services.
    # The example block shows the customizations keys that are accepted:
    #
    # "com.example" {
    #   host = "192.168.1.7"
    #   port = 5000
    #   use-tls = false
    # }
  }

  # Sanitization is applied to logs, text before passed to agent models, text received from agent tools, found matching
  # substrings are masked (replaced with a * for each character in the matching substring).
  #
  # By default, no sanitization is applied.
  sanitization {
    regex-sanitizers {
      # Named Java Regular Expressions
      # Example (case insensitive warm colors)
      # "warm-colors" = { pattern = "(?i)(red|orange|yellow)" }
    }
    # Available predefined: CREDIT_CARD, IBAN, PHONE, EMAIL, IP_ADDRESS
    predefined-sanitizers = []
  }

  telemetry {
    tracing {
      collector-endpoint = ""
      collector-endpoint = ${?COLLECTOR_ENDPOINT}
    }
  }
}
```

## <a href="about:blank#_references"></a> References

- [Setup and dependency injection](../../sdk/setup-and-dependency-injection.html)
- [AI model provider configuration](../../sdk/model-provider-details.html)
- [Typesafe Config / HOCON](https://github.com/lightbend/config)

<!-- <footer> -->
<!-- <nav> -->
[Observability descriptor](../descriptors/observability-descriptor.html) [Glossary of terms](../glossary.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
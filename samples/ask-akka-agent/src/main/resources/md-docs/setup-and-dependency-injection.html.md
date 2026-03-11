<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Setup and configuration](setup-and-configuration/index.html)
- [Setup and dependency injection](setup-and-dependency-injection.html)

<!-- </nav> -->

# Setup and dependency injection

## <a href="about:blank#_service_lifecycle"></a> Service lifecycle

It is possible to define logic that runs on service instance start up.

This is done by creating a class implementing `akka.javasdk.ServiceSetup` and annotating it with `akka.javasdk.annotations.Setup`.
Only one such class may exist in the same service.

[Bootstrap.java](https://github.com/akka/akka-sdk/blob/main/samples/spring-dependency-injection/src/main/java/com/example/Bootstrap.java)
```java
@Setup // (1)
public class Bootstrap implements ServiceSetup {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ComponentClient componentClient;

  public Bootstrap(ComponentClient componentClient) { // (2)
    this.componentClient = componentClient;
  }

  @Override
  public void onStartup() { // (3)
    logger.info("Service starting up");
    var result = componentClient.forEventSourcedEntity("123").method(Counter::get).invoke();
    logger.info("Initial value for entity 123 is [{}]", result);
  }
```

| **1** | One annotated implementation of `ServiceSetup` |
| **2** | A few different objects can be dependency injected, see below |
| **3** | `onStartup` is invoked at service start, but before the service is completely started up |
It is important to remember that an Akka service consists of one to many distributed instances that can be restarted
individually and independently, for example during a rolling upgrade. Each such instance starting up will invoke `onStartup` when starting up, even if other instances run it before.

## <a href="about:blank#_disabling_components"></a> Disabling components

You can use `ServiceSetup` to disable components by overriding `disabledComponents` and returning a set of component classes to disable.

[Bootstrap.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/Bootstrap.java)
```java
@Setup
public class Bootstrap implements ServiceSetup {

  private final Config appConfig;

  public Bootstrap(Config appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  public Set<Class<?>> disabledComponents() { // (1)
    if (appConfig.getString("my-app.environment").equals("prod")) {
      return Set.of(MyComponent.class); // (2)
    } else {
      return Set.of(); // (2)
    }
  }
}
```

| **1** | Override `disabledComponents` |
| **2** | Provide a set of component classes to disable depending on the configuration |

## <a href="about:blank#_dependency_injection"></a> Dependency injection

The Akka SDK provides injection of types related to capabilities the SDK provides to components.

Injection is done as constructor parameters for the component implementation class.

The following types can be injected in all component types:

| Injectable class | Description |
| --- | --- |
| `akka.javasdk.agent.AgentRegistry` | Contains information about all agents, see [Implementing agents](agents.html) |
| `com.typesafe.config.Config` | Access the user defined configuration picked up from `application.conf` |
| `akka.javasdk.Sanitizer` | Allows for applying sanitization, see [Sanitization](sanitization.html) |
The following types can be injected in Service Setup, HTTP Endpoints, gRPC Endpoints, Agents, Consumers, Timed Actions, and Workflows:

| Injectable class | Description |
| --- | --- |
| `akka.javasdk.client.ComponentClient` | For interaction between components, see [Component and service calls](component-and-service-calls.html) |
| `akka.javasdk.http.HttpClientProvider` | For creating clients to make calls between Akka services and also to other HTTP servers, see [Component and service calls](component-and-service-calls.html) |
| `akka.javasdk.grpc.GrpcClientProvider` | For creating clients to make calls between Akka services and also to other gRPC servers, see [Component and service calls](component-and-service-calls.html) |
| `akka.javasdk.timer.TimerScheduler` | For scheduling timed actions, see [Timers](timed-actions.html) |
| `akka.stream.Materializer` | Used for running Akka streams |
| `akka.javasdk.Retries` | Utility for retrying calls |
| `java.util.concurrent.Executor` | An executor which runs each task in a virtual thread, and is safe to use for blocking async work, for example with `CompletableFuture.supplyAsync(() → blocking, executor)` |
Furthermore, the following component specific types can also be injected:

| Component Type | Injectable classes |
| --- | --- |
| Agent | `akka.javasdk.agent.AgentContext` for access to the session id that the agent participate in |
| Endpoint | `akka.javasdk.http.RequestContext` with access to request related things |
| Workflow | `akka.javasdk.workflow.WorkflowContext` for access to the workflow id |
| Event Sourced Entity | `akka.javasdk.eventsourcedentity.EventSourcedEntityContext` for access to the entity id |
| Key Value Entity | `akka.javasdk.keyvalueentity.KeyValueEntityContext` for access to the entity id |

## <a href="about:blank#_custom_dependency_injection"></a> Custom dependency injection

In addition to the predefined objects a service can also provide its own objects for injection. Any unknown
types in component constructor parameter lists will be looked up using a `DependencyProvider`.

Providing custom objects for injection is done by implementing a service setup class with an overridden `createDependencyProvider` that returns a custom instance of `akka.javasdk.DependencyProvider`. A single instance
of the provider is used for the entire service instance.

Note that the objects returned from a custom `DependencyProvider` must either be a new instance for every call
to the dependency provider or be thread safe since they will be shared by any component instance accepting
them, potentially each running in parallel. This is best done by using immutable objects which is completely safe.

|  | Injecting shared objects that use regular JVM concurrency primitives such as locks, can easily block
individual component instances from running in parallel and cause throughput issues or even worse, deadlocks,
so should be avoided. |
The implementation can be pure Java without any dependencies:

[Bootstrap.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/Bootstrap.java)
```java
@Setup
public class Bootstrap implements ServiceSetup {

  private final Config appConfig;

  public Bootstrap(Config appConfig) {
    this.appConfig = appConfig;
  }


  @Override
  public DependencyProvider createDependencyProvider() { // (1)
    final var myAppSettings = new MyAppSettings(
      appConfig.getBoolean("my-app.some-feature-flag")
    ); // (2)

    return new DependencyProvider() { // (3)
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == MyAppSettings.class) {
          return (T) myAppSettings;
        } else {
          throw new RuntimeException("No such dependency found: " + clazz);
        }
      }
    };
  }

}
```

| **1** | Override `createDependencyProvider` |
| **2** | Create an object for injection, in this case an immutable settings class built from config defined in
the `application.conf` file of the service. |
| **3** | Return an implementation of `DependencyProvider` that will return the instance if called with its class. |
It is now possible to declare a constructor parameter in any component accepting `MyAppSettings`. The SDK will
inject the instance provided by the `DependencyProvider`.

Or make use of an existing dependency injection framework, like this example leveraging Spring:

[Bootstrap.java](https://github.com/akka/akka-sdk/blob/main/samples/spring-dependency-injection/src/main/java/com/example/Bootstrap.java)
```java
public class Bootstrap implements ServiceSetup {

  @Override
  public DependencyProvider createDependencyProvider() {
    try {
      AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(); // (1)
      ResourcePropertySource resourcePropertySource = new ResourcePropertySource(
        new ClassPathResource("application.properties")
      );
      context.getEnvironment().getPropertySources().addFirst(resourcePropertySource);
      context.registerBean(ComponentClient.class, () -> componentClient);
      context.scan("com.example");
      context.refresh();
      return context::getBean; // (2)
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
```

| **1** | Set up a Spring `AnnotationConfigApplicationContext` |
| **2** | DependencyProvider is a SAM (single abstract method) type with signature `Class<T> → T`, the method reference `AnnotationConfigApplicationContext#getBean` matches it. |

## <a href="about:blank#_custom_dependency_injection_in_tests"></a> Custom dependency injection in tests

The TestKit allows providing a custom `DependencyProvider` through `TestKit.Settings#withDependencyProvider(provider)` so
that mock instances of dependencies can be used in tests.

[MyIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/test/java/com/example/MyIntegrationTest.java)
```java
public class MyIntegrationTest extends TestKitSupport {

  private static final DependencyProvider mockDependencyProvider =
    new DependencyProvider() { // (1)
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getDependency(Class<T> clazz) {
      if (clazz.equals(MyAppSettings.class)) {
        return (T) new MyAppSettings(true);
      } else {
        throw new IllegalArgumentException("Unknown dependency type: " + clazz);
      }
    }
  };

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withDependencyProvider(mockDependencyProvider); // (2)
  }
```

| **1** | Implement a test specific `DependencyProvider`. |
| **2** | Configure the TestKit to use it. |
Any component injection happening during the test will now use the custom `DependencyProvider`.

The test specific `DependencyProvider` must be able to provide all custom dependencies used by
all components that the test interacts with.

## <a href="about:blank#_configuration"></a> Configuration

Configuration properties for the service, or adjustments to the Akka default configuration, can be defined in `src/main/resources/application.conf` in [HOCON format](https://github.com/lightbend/config/blob/main/HOCON.md).

src/main/resources/application.conf
```json
my-app {
  some-feature-flag = true
  environment = "test"
  environment = ${?ENVIRONMENT}
}

akka.javasdk {
  agent {
    model-provider = openai

    openai {
      model-name = "gpt-4o-mini"
      api-key = ${?OPENAI_API_KEY}
    }
  }

  # dev-mode configuration is only used when running locally
  dev-mode {
    http-port = 9001
    acl.enabled = false
  }
}
```
`${?ENVIRONMENT}` and `${?OPENAI_API_KEY}` means that if an environment variable is defined with the given name it will override the configuration property.

To access the configuration in application code you can use a constructor parameter `com.typesafe.config.Config` in all components and the `ServiceSetup` class. An example of this is shown in [Disabling components](about:blank#_disabling_components).

|  | Don’t use `ConfigFactory.load()` since that will not load the `application.conf` as you intended. Use dependency injection of `Config` instead. |

### <a href="about:blank#_test_configuration"></a> Test configuration

Test that are using the `TestKitSupport` are loading configuration from `src/test/resourced/application-test.conf` if that exists, otherwise from `application.conf`.

src/test/resources/application-test.conf
```json
include "application.conf"

my-app {
  some-feature-flag = false
}
```
Alternatively, the configuration of the test can be overridden in the `testKitSettings`:

[ConfigIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/test/java/com/example/ConfigIntegrationTest.java)
```java
public class ConfigIntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      """
      akka.javasdk.agent.openai.api-key = n/a
      """
    );
  }
}
```

### <a href="about:blank#_reference_configuration"></a> Reference configuration

The default configuration of the Akka SDK:

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

    acl {
      # Whether ACL checking is enabled
      enabled = true
    }

    persistence {
      # Whether persistence is enabled
      enabled = false
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
In addition, there is also [AI model provider configuration](model-provider-details.html).

<!-- <footer> -->
<!-- <nav> -->
[Setup and configuration](setup-and-configuration/index.html) [Serialization](serialization.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
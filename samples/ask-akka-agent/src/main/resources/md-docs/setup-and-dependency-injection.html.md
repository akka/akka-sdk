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

[CounterSetup.java](https://github.com/akka/akka-sdk/blob/main/samples/spring-dependency-injection/src/main/java/com/example/CounterSetup.java)
```java
@Setup // (1)
public class CounterSetup implements ServiceSetup {

  private  final Logger logger = LoggerFactory.getLogger(getClass());
  private final ComponentClient componentClient;

  public CounterSetup(ComponentClient componentClient) { // (2)
    this.componentClient = componentClient;
  }

  @Override
  public void onStartup() { // (3)
    logger.info("Service starting up");
    var result = componentClient.forEventSourcedEntity("123")
        .method(Counter::get)
        .invoke();
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

[MyAppSetup.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/MyAppSetup.java)
```java
@Setup
public class MyAppSetup implements ServiceSetup {

  private final Config appConfig;

  public MyAppSetup(Config appConfig) {
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

The following types can be injected in Service Setup, HTTP Endpoints, gRPC Endpoints, Agents, Consumers, Timed Actions, and Workflows:

| Injectable class | Description |
| --- | --- |
| `akka.javasdk.client.ComponentClient` | for interaction between components, see [Component and service calls](component-and-service-calls.html) |
| `akka.javasdk.agent.AgentRegistry` | contains information about all agents, see [Implementing agents](agents.html) |
| `akka.javasdk.http.HttpClientProvider` | for creating clients to make calls between Akka services and also to other HTTP servers, see [Component and service calls](component-and-service-calls.html) |
| `akka.javasdk.grpc.GrpcClientProvider` | for creating clients to make calls between Akka services and also to other gRPC servers, see [Component and service calls](component-and-service-calls.html) |
| `akka.javasdk.timer.TimerScheduler` | for scheduling timed actions, see [Timers](timed-actions.html) |
| `akka.stream.Materializer` | Used for running Akka streams |
| `com.typesafe.config.Config` | Access the user defined configuration picked up from `application.conf` |
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

[MyAppSetup.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/MyAppSetup.java)
```java
@Setup
public class MyAppSetup implements ServiceSetup {

  private final Config appConfig;

  public MyAppSetup(Config appConfig) {
    this.appConfig = appConfig;
  }


  @Override
  public DependencyProvider createDependencyProvider() { // (1)
    final var myAppSettings =
        new MyAppSettings(appConfig.getBoolean("my-app.some-feature-flag")); // (2)

    return new DependencyProvider() { // (3)
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == MyAppSettings.class) {
          return (T) myAppSettings;
        } else {
          throw new RuntimeException("No such dependency found: "+ clazz);
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

[CounterSetup.java](https://github.com/akka/akka-sdk/blob/main/samples/spring-dependency-injection/src/main/java/com/example/CounterSetup.java)
```java
public class CounterSetup implements ServiceSetup {

  @Override
  public DependencyProvider createDependencyProvider() {
    try {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(); // (1)
      ResourcePropertySource resourcePropertySource = new ResourcePropertySource(new ClassPathResource("application.properties"));
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

  private static final DependencyProvider mockDependencyProvider = new DependencyProvider() { // (1)
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
    return TestKit.Settings.DEFAULT
        .withDependencyProvider(mockDependencyProvider); // (2)
  }
```

| **1** | Implement a test specific `DependencyProvider`. |
| **2** | Configure the TestKit to use it. |
Any component injection happening during the test will now use the custom `DependencyProvider`.

The test specific `DependencyProvider` must be able to provide all custom dependencies used by
all components that the test interacts with.

<!-- <footer> -->
<!-- <nav> -->
[Setup and configuration](setup-and-configuration/index.html) [Serialization](serialization.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Setup and configuration](setup-and-configuration/index.html)
- [Metrics](metric.html)

<!-- </nav> -->

# Metrics

The Akka runtime exposes metrics that provide deep insights into the performance and behavior of your application. Metrics covers all components but also exposes internal runtime statistics. In addition to these Akka-specific metrics, standard [JVM metrics](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/runtime-telemetry/library) are also available, giving you comprehensive visibility into memory usage, garbage collection, thread pools, and other JVM-level operations.

## <a href="about:blank#_custom_metrics"></a> Custom Metrics

In addition to the built-in metrics, you can define custom application-level metrics to track business-specific operations and behavior. Custom metrics allow you to instrument your code with counters, gauges, timers, and distribution summaries to measure what matters most for your application. These metrics integrate seamlessly with the Akka metrics infrastructure and can be [exported](../operations/observability-and-monitoring/observability-exports.html) to the same monitoring systems as the runtime metrics.

The OpenTelemetry `Meter` interface can be [injected](setup-and-dependency-injection.html#_dependency_injection) directly into any Akka component, but we recommend encapsulating your metrics definitions in a dedicated class. This keeps metric names, descriptions, and units in one place and provides a convenient API for the rest of your application. It’s recommended to follow Open Telemetry [Semantic conventions](https://opentelemetry.io/docs/specs/semconv/general/metrics/).

[ShoppingCartMetrics.java](https://github.com/akka/akka-sdk/blob/main/sampleskey-value-shopping-cart/src/main/java/com/example/application/ShoppingCartMetrics.java)
```java
public class ShoppingCartMetrics {

  private final Meter meter;
  private final LongCounter shoppingCartCreated;

  public ShoppingCartMetrics(Meter meter) { // (1)
    this.meter = meter;
    this.shoppingCartCreated = meter
      .counterBuilder("shopping.cart.created") // (2)
      .setDescription("How many shopping carts have been created")
      .setUnit("{created}")
      .build();
  }

  public void shoppingCartCreated() { // (3)
    shoppingCartCreated.add(1);
  }
}
```

| **1** | Injects the OpenTelemetry `Meter` provided by the Akka runtime. |
| **2** | Creates a custom counter metric. Please follow Open Telemetry semantic conventions. |
| **3** | Exposes a convenient accessor method. |
Use it in your components.

[ShoppingCartEndpoint.java](https://github.com/akka/akka-sdk/blob/main/sampleskey-value-shopping-cart/src/main/java/com/example/api/ShoppingCartEndpoint.java)
```java
public ShoppingCartEndpoint(
  ComponentClient componentClient,
  ShoppingCartMetrics shoppingCartMetrics
) {
  this.componentClient = componentClient;
  this.shoppingCartMetrics = shoppingCartMetrics; // (1)
}

@Post("/create")
public String create() {
  final String cartId = UUID.randomUUID().toString();
  try {
    componentClient.forKeyValueEntity(cartId).method(ShoppingCartEntity::create).invoke();
    shoppingCartMetrics.shoppingCartCreated(); // (2)
    return cartId;
  } catch (Exception e) {
    throw new RuntimeException("Failed to create cart, please retry", e);
  }
}
```

| **1** | Injects `ShoppingCartMetrics` via constructor. |
| **2** | Increments the cart-created counter. |
Wire the metrics class in your `Bootstrap` so it is available for [dependency injection](setup-and-dependency-injection.html#_dependency_injection).

[Bootstrap.java](https://github.com/akka/akka-sdk/blob/main/sampleskey-value-shopping-cart/src/main/java/com/example/Bootstrap.java)
```java
@Setup
public class Bootstrap implements ServiceSetup {

  private final Meter meter;

  public Bootstrap(Meter meter) {
    this.meter = meter;
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return DependencyProvider.single(new ShoppingCartMetrics(meter)); // (1)
  }
}
```

| **1** | Registers `ShoppingCartMetrics` as a dependency, making it injectable into any component. |
You can validate your metrics locally by running the service with `-Dakka.runtime.telemetry.metrics.disabled=false` parameter and pointing to an open telemetry backend with `OPENTELEMETRY_COLLECTOR_ENDPOINT` environment variable.

<!-- <footer> -->
<!-- <nav> -->
[Data sanitization](sanitization.html) [Developer best practices](dev-best-practices.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
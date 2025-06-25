<!-- <nav> -->
- [Akka](../index.html)
- [Shopping cart part 1: Build a simple cart service](build-and-deploy-shopping-cart.html)

<!-- </nav> -->

# Shopping cart part 1: Build a simple cart service

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |
This guide walks you through the design and implementation of a shopping cart service, illustrating the use of some of Akka’s components.

## <a href="about:blank#_overview"></a> Overview

This quickstart implements an HTTP endpoint and a shopping cart that supports adding, removing, querying, and cart checkout.

In this guide you will:

- Clone a completed shopping cart service repository to examine and run it locally.
- Be introduced to key Akka concepts including [Event Sourced Entities](../java/event-sourced-entities.html).
- See how the Akka [Architecture model](../concepts/architecture-model.html) provides a clear separation of concerns in your microservices.
- Run the service locally and explore it with the local Akka console.
- Deploy the service to [akka.io](https://console.akka.io/).

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- An [Akka account](https://console.akka.io/register)
- [Docker Engine](https://docs.docker.com/get-started/get-docker/) 27 or later

## <a href="about:blank#_clone_the_sample"></a> Clone the sample

1. Clone the full source code of the Shopping Cart sample from [Github](https://github.com/akka-samples/shopping-cart-quickstart), or use the Akka CLI command below to download and unzip this quickstart project.

```bash
akka quickstart download shopping-cart
```
2. Open the project in your favorite editor.

## <a href="about:blank#_understanding_the_shopping_cart"></a> Understanding the shopping cart

Shopping carts are excellent examples of agentic [[1](about:blank#_footnotedef_1) ] behavior (with or without AI). In real-world implementations, they can automatically apply discounts or coupons, monitor inventory levels, detect abandoned carts, maximize conversion rates, and make recommendations based on shopping history and cart contents.

For the purposes of this initial quickstart, we will just have a single endpoint and a single event-sourced entity to keep things easy to digest.

Through our entity we expect to manage our cart, adding and removing items as we please. Being event-sourced means it will represent changes to state as a series of domain events. Let’s have a look at what kind of model we use to store the events our entity generates.

### <a href="about:blank#_the_domain_model"></a> The domain model

The first concept to note is the domain class `ShoppingCart` in package `shoppingcart.domain`. This class is located in the `src/main/java/shoppingcart/domain/` directory and named `ShoppingCart.java`.

[ShoppingCart.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/domain/ShoppingCart.java)
```java
package shoppingcart.domain;

import java.util.List;

public record ShoppingCart(String cartId, List<LineItem> items, boolean checkedOut) { // (1)

  public record LineItem(String productId, String name, int quantity) { // (2)
    public LineItem withQuantity(int quantity) {
      return new LineItem(productId, name, quantity);
    }
  }

}
```

| **1** | Our `ShoppingCart` is fairly simple, being composed only by a `cartId` and a list of line items. |
| **2** | A `LineItem` represents a single product and the quantity we intend to buy. |
Next, we have a domain event for adding items to the cart. Here we’ve created an interface `ShoppingCartEvent` with the `ItemAdded` domain event in package `shoppingcart.domain`. This file is in the `src/main/java/shoppingcart/domain/` directory and named `ShoppingCartEvent.java`:

[ShoppingCartEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/domain/ShoppingCartEvent.java)
```java
package shoppingcart.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface ShoppingCartEvent { // (1)

  @TypeName("item-added") // (2)
  record ItemAdded(ShoppingCart.LineItem item) implements ShoppingCartEvent {
  }

}
```

| **1** | `ItemAdded` event derives from the `ShoppingCartEvent` interface. |
| **2** | Specifying a logical type name, required for serialization. |
You may notice that there are other events defined as well. This is how the application passes events between the Akka runtime and the domain object.

Jumping back to the `ShoppingCart` domain class, there is also business logic to handle the `ItemAdded` domain event for adding items to the cart:

[ShoppingCart.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/domain/ShoppingCart.java)
```java
public ShoppingCart onItemAdded(ShoppingCartEvent.ItemAdded itemAdded) {
  var item = itemAdded.item();
  var lineItem = updateItem(item); // (1)
  List<LineItem> lineItems = removeItemByProductId(item.productId()); // (2)
  lineItems.add(lineItem); // (3)
  lineItems.sort(Comparator.comparing(LineItem::productId));
  return new ShoppingCart(cartId, lineItems, checkedOut); // (4)
}

private LineItem updateItem(LineItem item) {
  return findItemByProductId(item.productId())
    .map(li -> li.withQuantity(li.quantity() + item.quantity()))
    .orElse(item);
}

private List<LineItem> removeItemByProductId(String productId) {
  return items().stream()
    .filter(lineItem -> !lineItem.productId().equals(productId))
    .collect(Collectors.toList());
}

public Optional<LineItem> findItemByProductId(String productId) {
  Predicate<LineItem> lineItemExists =
      lineItem -> lineItem.productId().equals(productId);
  return items.stream().filter(lineItemExists).findFirst();
}
```

| **1** | For an existing item, we make sure to sum the existing quantity with the incoming one. |
| **2** | Returns an updated list of items without the existing item. |
| **3** | Adds the updated item to the shopping cart. |
| **4** | Returns a new instance of the shopping cart with the updated line items. |

### <a href="about:blank#_the_shoppingcart_entity"></a> The ShoppingCart entity

To go with our domain data and our events, we have an entity named `ShoppingCartEntity` in package `shoppingcart.application`. This class is located in the `src/main/java/shoppingcart/application/` directory and named `ShoppingCartEntity.java`. The class signature looks like this:

[ShoppingCartEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java)
```java
package shoppingcart.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.domain.ShoppingCart;
import shoppingcart.domain.ShoppingCart.LineItem;
import shoppingcart.domain.ShoppingCartEvent;

import java.util.Collections;

@ComponentId("shopping-cart") // (2)
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> { // (1)
}
```

| **1** | Extend `EventSourcedEntity<ShoppingCart, ShoppingCartEvent>`, where `ShoppingCart` is the type of state this entity stores, and `ShoppingCartEvent` is the interface for the events it persists. |
| **2** | Annotate the class so Akka can identify it as an event-sourced entity. |
Inside `ShoppingCartEntity`, we define an `addItem` command handler to generate an `ItemAdded` event, and an event handler to process the event and update internal state:

[ShoppingCartEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java)
```java
public Effect<Done> addItem(LineItem item) {
    if (currentState().checkedOut()) {
      logger.info("Cart id={} is already checked out.", entityId);
      return effects().error("Cart is already checked out.");
    }
    if (item.quantity() <= 0) { // (1)
      logger.info("Quantity for item {} must be greater than zero.", item.productId());
      return effects().error("Quantity for item " + item.productId() + " must be greater than zero.");
    }

    var event = new ShoppingCartEvent.ItemAdded(item); // (2)

    return effects()
        .persist(event) // (3)
        .thenReply(newState -> Done.getInstance()); // (4)
  }

  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) {
    return switch (event) {
      case ShoppingCartEvent.ItemAdded evt -> currentState().onItemAdded(evt); // (5)
    };
  }
```

| **1** | Validate the quantity of items added is greater than zero. |
| **2** | Create a new `ItemAdded` event representing the change to the state of the cart. |
| **3** | Persist the event by returning an `Effect` with `effects().persist`. |
| **4** | Acknowledge that the event was successfully persisted. |
| **5** | Event handler to process the `ItemAdded` event and return the updated state. |
Inside `ShoppingCartEntity`, we also define a `getCart` command handler to retrieve the state of the shopping cart:

[ShoppingCartEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java)
```java
private final String entityId;

private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEntity.class);

public ShoppingCartEntity(EventSourcedEntityContext context) {
  this.entityId = context.entityId(); // (1)
}

@Override
public ShoppingCart emptyState() { // (2)
  return new ShoppingCart(entityId, Collections.emptyList(), false);
}

public ReadOnlyEffect<ShoppingCart> getCart() {
  return effects().reply(currentState()); // (3)
}
```

| **1** | Store the `entityId` in an internal attribute, to be used elsewhere. |
| **2** | Define the initial state. |
| **3** | Return the current state as a reply to the request. |
You should be intentional about when you query views versus when you query entity state. Views are *eventually* consistent while entities are *strongly* consistent. You don’t have to worry about views yet, though, as there are none in this quickstart.

### <a href="about:blank#_the_external_api"></a> The external API

The shopping cart API is defined by the `ShoppingCartEndpoint`.

It is a class named `ShoppingCartEndpoint` in package `shoppingcart.api`. This class is located in the `src/main/java/shoppingcart/api/` directory and named `ShoppingCartEndpoint.java`:

[ShoppingCartEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/api/ShoppingCartEndpoint.java)
```java
package shoppingcart.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCart;


// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/carts") // (1)
public class ShoppingCartEndpoint {

  private final ComponentClient componentClient;

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEndpoint.class);

  public ShoppingCartEndpoint(ComponentClient componentClient) { // (2)
    this.componentClient = componentClient;
  }

  @Get("/{cartId}") // (3)
  public ShoppingCart get(String cartId) {
    logger.info("Get cart id={}", cartId);
    return componentClient.forEventSourcedEntity(cartId) // (4)
        .method(ShoppingCartEntity::getCart)
        .invoke(); // (5)
  }

  @Put("/{cartId}/item") // (6)
  public HttpResponse addItem(String cartId, ShoppingCart.LineItem item) {
    logger.info("Adding item to cart id={} item={}", cartId, item);
    componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::addItem)
      .invoke(item);
    return HttpResponses.ok(); // (7)
  }

}
```

| **1** | Annotate the class so Akka can identify it as an endpoint with a common path prefix for all methods `/carts`. |
| **2** | The `ComponentClient` utility enables components to interact with each other. |
| **3** | Combine the `cartId` parameter with the endpoint root, e.g. `/carts/123`. |
| **4** | `ComponentClient` calls a command handler on an event-sourced entity from inside an endpoint. |
| **5** | The result of a request to `ShoppingCart.getCart` is a `ShoppingCart`. |
| **6** | Use path parameter `{cartId}` in combination with request body `ShoppingCart.LineItem`. |
| **7** | On success, respond with a suitable response, in this case an `HTTP 200 OK`. |

## <a href="about:blank#_run_locally"></a> Run locally

1. Start your service locally:

```command
mvn compile exec:java
```
Once successfully started, any defined endpoints become available at `localhost:9000`.
2. Let’s send some requests using `curl`. Add some T-shirts to a shopping cart:

```command
curl -i -XPUT -H "Content-Type: application/json" localhost:9000/carts/123/item -d '
{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 3}'
```
3. Add some blue jeans to the shopping cart:

```command
curl -i -XPUT -H "Content-Type: application/json" localhost:9000/carts/123/item -d '
{"productId":"blue-jeans", "name":"Blue Jeans", "quantity": 2}'
```
4. Add a few more T-shirts to the shopping cart:

```command
curl -i -XPUT -H "Content-Type: application/json" localhost:9000/carts/123/item -d '
{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 3}'
```
5. Request all of the items in the cart:

```command
curl localhost:9000/carts/123
```
6. Observe all the items in the cart:

```none
{"cartId":"123","items":[{"productId":"akka-tshirt","name":"Akka Tshirt","quantity":6},
{"productId":"blue-jeans","name":"Blue Jeans","quantity":5}],"checkedOut":false}
```

## <a href="about:blank#_explore_the_local_console"></a> Explore the local console

1. To get a clear view of your locally running service, [install the Akka CLI](../operations/cli/installation.html). It provides a local web-based management console.
2. Start the local console:

```command
akka local console
```

```none
───────────────────────────────────────────────────────────────────────
│ SERVICE                      │ STATE    │ ADDRESS                   |
───────────────────────────────────────────────────────────────────────
│ shopping-cart-quickstart     │ Running  │ localhost:9000            │
───────────────────────────────────────────────────────────────────────
Local console: http://localhost:9889
(use Ctrl+C to quit)
```
3. Open [http://localhost:9889/](http://localhost:9889/) to see your local service in action:

![local console first app events](_images/local-console-first-app-events.png)


Here, you can view not only the [current state of the cart](http://localhost:9889/services/shopping-cart-quickstart/components/shoppingcart.application.ShoppingCartEntity), but also <a href="http://localhost:9889/services/shopping-cart-quickstart/components/shoppingcart.application.ShoppingCartEntity/eventlog/123">**a detailed log of events**</a>, and the corresponding state changes that occurred along the way.

## <a href="about:blank#_deploy_to_akka_io"></a> Deploy to akka.io

Now that you’ve built and started the shopping cart service locally, it’s time to run it on the Akka Agentic Platform without having to change any code.

1. If you have not already done so, [install the Akka CLI](../operations/cli/installation.html).
2. Authenticate the CLI with your Akka account:

```command
akka auth login
```
3. Build a container image of your shopping cart service:

```command
mvn clean install -DskipTests
```
4. Take note of the container name and tag from the last line in the output, for example:

```command
DOCKER> Tagging image shoppingcart:1.0-SNAPSHOT-20241028102843 successful!
```
5. Deploy your service, replacing:

  - `container-name` with the container name from the `mvn install` output in the previous step
  - `tag-name` with the tag name from the `mvn install` output in the previous step

```command
akka service deploy cart-service shoppingcart:tag-name --push
```
Your service named `cart-service` will now begin deploying.
6. Verify the deployment status of your service:

```command
akka service list
```
A service status can be one of the following:

  - **Ready**: All service instances are up-to-date and fully available.
  - **UpdateInProgress**: Service is updating.
  - **Unavailable**: No service instances are available.
  - **PartiallyReady**: Some, but not all, service instances are available.
Approximately one minute after deploying, your service status should become **Ready**.
7. Expose your service to the internet:

```command
akka service expose cart-service
```
Should respond with something similar to (the exact address will be unique to your service):

```command
Service 'cart-service' was successfully exposed at: spring-tooth-3406.gcp-us-east1.akka.services
```
Congratulations! You have successfully deployed your service. You can now access it using the hostname described in the output of the command above.

## <a href="about:blank#_invoke_your_deployed_service"></a> Invoke your deployed service

You can use [cURL](https://curl.se/) to invoke your service, replacing URL with the hostname from the previous step.

1. Add an item to the shopping cart:

```command
curl -i -XPUT -H "Content-Type: application/json" https://spring-tooth-3406.gcp-us-east1.akka.services/carts/123/item -d '
{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 10}'
```
2. Get cart state:

```command
curl https://spring-tooth-3406.gcp-us-east1.akka.services/carts/123
```

## <a href="about:blank#_explore_the_console"></a> Explore the console

1. Open the <a href="https://console.akka.io/">**Akka Console**</a>.
2. Navigate to the **Project** where the Service is deployed.
3. Click on the **Service** card of the Service. It shows detailed information about the running service.

![console first app service](_images/console-first-app-service.png)


## <a href="about:blank#_next_steps"></a> Next steps

Now that you’ve built and deployed a shopping cart service, take your Akka skills to the next level:

1. **Add a view**: Continue to the [next step](addview.html) in the tour.
2. **Expand on your own**: Explore [other Akka components](../concepts/architecture-model.html#_akka_components) to enhance your application with additional features.
3. **Explore other Akka samples**: Discover more about Akka by exploring [different use cases](samples.html) for inspiration.
[1](about:blank#_footnoteref_1). Defined as a stateful behavior capable of acting on its own.
<!-- <footer> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
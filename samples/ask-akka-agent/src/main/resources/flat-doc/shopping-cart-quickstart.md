# Shopping cart quickstart

This guide walks you through a shopping cart service illustrating the use of some of Akka’s components.

## Overview

The shopping cart service implements a cart that allows users to add and remove items, get the contents of carts, and checkout the carts.

In this guide you will:

* Download a completed shopping cart service to examine and run it locally.
* Be introduced to key Akka concepts including [Event Sourced Entities](java:event-sourced-entities.adoc).
* See how the Akka [concepts:architecture-model.adoc](concepts:architecture-model.adoc) provides a clear separation of concerns in your microservices.
* Run the service locally and explore it with the local Akka console.
* Deploy the service to [akka.io](https://console.akka.io).

## Prerequisites

## Download the sample

You can download the full source code of the Shopping Cart sample as a [zip file](_attachments/shopping-cart-quickstart.zip), or you can use the Akka CLI command below to download and unzip this quickstart project.

```bash
akka quickstart download shopping-cart
```

Then open the project in your favorite Integrated Development Environment (IDE).

## Understanding the shopping cart

For this quickstart walk-through, we will implement a Shopping Cart based on an _Event Sourced Entity_.

Through our "Shopping Cart" Event Sourced Entity we expect to manage our cart, adding and removing items as we please. Being event-sourced means it will represent changes to state as a series of domain events. Let’s have a look at what kind of model we use to store and the events our entity generates.

### The domain model

The first concept to note is the domain class `ShoppingCart` in package `shoppingcart.domain`. This class is located in the `src/main/java/shoppingcart/domain/` directory and named `ShoppingCart.java`. 

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/domain/ShoppingCart.java[ShoppingCart.java]**


```
1. Our `ShoppingCart` is fairly simple, being composed only by a `cartId` and a list of line items.
2. A `LineItem` represents a single product and the quantity we intend to buy.

Next, we have a domain event for adding items to the cart. Add an interface `ShoppingCartEvent` with the `ItemAdded` domain event in package `shoppingcart.domain`. This file should be located in the `src/main/java/shoppingcart/domain/` directory and named `ShoppingCartEvent.java`:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/domain/ShoppingCartEvent.java[ShoppingCartEvent.java]**


```
1. `ItemAdded` event derives from the `ShoppingCartEvent` interface.
2. Specifying a logical type name, required for serialization.

You may notice that there are other events defined as well. This is how the application will pass Java events between the Akka runtime and the domain object.

Jumping back to the `ShoppingCart` domain class, there is also business logic to handle the `ItemAdded` domain event for adding items to the cart:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/domain/ShoppingCart.java[ShoppingCart.java]**


```
1. For an existing item, we will make sure to sum the existing quantity with the incoming one.
2. Returns an updated list of items without the existing item.
3. Adds the updated item to the shopping cart.
4. Returns a new instance of the shopping cart with the updated line items.

### The ShoppingCart entity

We also have an Event Sourced Entity named `ShoppingCartEntity` in package `shoppingcart.application`. This class is located in the `src/main/java/shoppingcart/application/` directory and named `ShoppingCartEntity.java`. The class signature looks like this:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java[ShoppingCartEntity.java]**

```java
```
1. Extend `EventSourcedEntity<ShoppingCart, ShoppingCartEvent>`, where `ShoppingCart` is the type of state this entity will store, and `ShoppingCartEvent` is the interface for the events it persists.
2. Annotate the class so Akka can identify it as an Event Sourced Entity.

Inside `ShoppingCartEntity`, we define an `addItem` command handler to generate an `ItemAdded` event, and an event handler to process the event:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java[ShoppingCartEntity.java]**

```java
```
1. Validate the quantity of items added is greater than zero.
2. Create a new `ItemAdded` event representing the change to the state of the cart.
3. Persist the event by returning an `Effect` with `effects().persist`.
4. Acknowledge that the event was successfully persisted.
5. Event handler to process the `ItemAdded` event and return the updated state.

Inside `ShoppingCartEntity`, we also define a `getCart` command handler to retrieve the state of the shopping cart:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java[ShoppingCartEntity.java]**


```
1. Store the `entityId` in an internal attribute, to be used elsewhere.
2. Define the initial state.
3. Return the current state as a reply to the request.

### The external API

The shopping cart API is defined by the `ShoppingCartEndpoint`.

It is a class named `ShoppingCartEndpoint` in package `shoppingcart.api`. This class is located in the `src/main/java/shoppingcart/api/` directory and named `ShoppingCartEndpoint.java`:

**{sample-base-url}/shopping-cart-quickstart/src/main/java/shoppingcart/api/ShoppingCartEndpoint.java[ShoppingCartEndpoint.java]**


```
1. Annotate the class so Akka can identify it as an Endpoint with a common path prefix for all methods `/carts`.
2. `ComponentClient` utility enables components to interact with each other.
3. GET endpoint path is combined with a path parameter name, e.g. `/carts/123`.
4. `ComponentClient` calling a command handler on an Event Sourced Entity from inside an Endpoint.
5. Result of request is a `CompletionStage<T>`, in this case a `CompletionStage<ShoppingCart>`.
6. Use path parameter `\{cartId}` in combination with request body `ShoppingCart.LineItem`.
7. Map request to a more suitable response, in this case an `HTTP 200 OK` response.

## Run locally

Start your service locally:

```command line
mvn compile exec:java
```

Once successfully started, any defined endpoints become available at `localhost:9000`.

Let’s send some requests using `curl`.

Add some T-shirts to a shopping cart:

```command line
curl -i -XPUT -H "Content-Type: application/json" localhost:9000/carts/123/item -d '
{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 3}'
```

Add some blue jeans to the shopping cart:

```command line
curl -i -XPUT -H "Content-Type: application/json" localhost:9000/carts/123/item -d '
{"productId":"blue-jeans", "name":"Blue Jeans", "quantity": 2}'
```

Add a few more T-shirts to the shopping cart:

```command line
curl -i -XPUT -H "Content-Type: application/json" localhost:9000/carts/123/item -d '
{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 3}'
```

Request to see all of the items in the cart:

```command line
curl localhost:9000/carts/123
```

Observe all the items in the cart:

```
{"cartId":"123","items":[{"productId":"akka-tshirt","name":"Akka Tshirt","quantity":6},
{"productId":"blue-jeans","name":"Blue Jeans","quantity":5}],"checkedOut":false}
```

## Explore the local console

To get a clear view of your locally running service, [install the Akka CLI](reference:cli/installation.adoc). It provides a local web-based management console.

After you have installed the CLI, start the local console:

```command line
akka local console
```

This will start a Docker container running the local console:

```
> Local Console is running at:             http://localhost:3000
 - shopping-cart-quickstart is running at: localhost:9000
--------------------
```

You can open [window="new"](http://localhost:3000/) to see your local service in action:

![local-console-first-app-events](local-console-first-app-events.png)

Here, you can view not only the [current state of the cart, window="new"](http://localhost:3000/services/shopping-cart-quickstart/components/shoppingcart.application.ShoppingCartEntity), but also [**a detailed log of events**, window="new"](http://localhost:3000/services/shopping-cart-quickstart/components/shoppingcart.application.ShoppingCartEntity/eventlog/123), and the corresponding state changes that occurred along the way.

## Deploy to akka.io

1. If you have not already done so, [install the Akka CLI](reference:cli/installation.adoc).
2. Authenticate the CLI with your Akka account:

   ```command line
   akka auth login
   ```
3. Build a container image of your shopping cart service:

   ```command line
   mvn clean install -DskipTests
   ```
4. Take note of the container name and tag from the last line in the output, for example:

   ```command line
   DOCKER> Tagging image shoppingcart:1.0-SNAPSHOT-20241028102843 successful!
   ```
5. Deploy your service, replacing:
   * `container-name` with the container name from the `mvn install` output in the previous step
   * `tag-name` with the tag name from the `mvn install` output in the previous step

+
```command line
akka service deploy cart-service shoppingcart:tag-name --push
```
Your service named `cart-service` will now begin deploying.

1. Verify the deployment status of your service:

   ```command line
   akka service list
   ```

   A service status can be one of the following:
   * **Ready**: All service instances are up-to-date and fully available.
   * **UpdateInProgress**: Service is updating.
   * **Unavailable**: No service instances are available.
   * **PartiallyReady**: Some, but not all, service instances are available.
   Approximately one minute after deploying, your service status should become **Ready**.
2. Expose your service to the internet:

   ```command line
   akka service expose cart-service
   ```

   Should respond with something similar to (the exact address is generated for your service):

   ```command line
   Service 'cart-service' was successfully exposed at: spring-tooth-3406.gcp-us-east1.akka.services
   ```

Congratulations, you have successfully deployed your service. You can now access it using the hostname described in the output of the command above.

## Invoke your deployed service

You can use [cURL, window="new"](https://curl.se) to invoke your service, replacing URL with the hostname that it was exposed as.

Add an item to the shopping cart:

```command window
curl -i -XPUT -H "Content-Type: application/json" https://spring-tooth-3406.gcp-us-east1.akka.services/carts/123/item -d '
{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 10}'
```

Get cart state:

```command window
curl https://spring-tooth-3406.gcp-us-east1.akka.services/carts/123
```

## Explore the console

1. Open the [**Akka Console**, window="new"](https://console.akka.io).
2. Navigate to the **Project** where the Service is deployed.
3. Click on the **Service** card of the Service. It shows detailed information about the running service.

![console-first-app-service](console-first-app-service.png)

## Next steps

Now that you’ve built and deployed a shopping cart service, take your Akka skills to the next level:

1. **Expand the service**: Explore [other Akka components](concepts:architecture-model.adoc#_akka_components) to enhance your application with additional features.
2. **Explore other Akka samples**: Discover more about Akka by exploring [different use cases](samples.adoc) for inspiration.
3. **Join the community**: Visit the [Support page](support:index.adoc) to find resources where you can connect with other Akka developers and expand your knowledge.

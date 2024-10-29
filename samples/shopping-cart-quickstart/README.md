# Shopping Cart

## Designing

To understand the Akka concepts that are the basis for this example, see [Development Process](https://docs.kalix.
io/java/development-process.html) in the documentation.

## Developing

This project demonstrates the use of an Event Sourced Entity.
To understand more about these components, see [Developing services](https://doc.akka.io/java/index.html).

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

To start your Akka service locally, run:

```shell
mvn compile exec:java
```

## Exercising the service

With your Akka service running, any defined endpoints should be available at `http://localhost:9000`.

* Add an item

```shell
curl -i -XPUT -H "Content-Type: application/json" localhost:9000/carts/123/item -d '
{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 10}'
```

* Get cart state

```shell
curl localhost:9000/carts/123
```

* Remove an item

```shell
curl -i -XDELETE -H "Content-Type: application/json" localhost:9000/carts/123/item/akka-tshirt
```

* Checkout the cart

```shell
curl -i -XPOST localhost:9000/carts/123/checkout
```

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/akka-cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy shopping-cart shopping-cart-quickstart:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

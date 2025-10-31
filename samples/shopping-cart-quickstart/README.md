# Build a Shopping Cart

The section [Build your first application](https://doc.akka.io/sdk/build-your-first-application.html) shows how to build this Shopping Cart service step by step.

## Prerequisites

- An [Akka account](https://console.akka.io/register)
- Java 21 installed (recommended: [Eclipse Adoptium](https://adoptium.net/marketplace/))
- [Apache Maven](https://maven.apache.org/install.html)
- [Docker Engine](https://docs.docker.com/get-started/get-docker/)
- [`curl` command-line tool](https://curl.se/download.html)

## Concepts

### Designing

To understand the Akka concepts behind this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

### Developing

Please follow [Build your first application](https://doc.akka.io/sdk/build-your-first-application.html) to understand the details of this service implementation.

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

## Explore the local console

To get a clear view of your locally running service, [install the Akka CLI](https://doc.akka.io/operations/cli/installation.html). It provides a local web-based management console.

After you have installed the CLI, start the local console:

```shell
akka local console
```
`
This will start a Docker container running the local console:

```
───────────────────────────────────────────────────────────────────────
│ SERVICE                      │ STATE    │ ADDRESS                   |
───────────────────────────────────────────────────────────────────────
│ shopping-cart-quickstart     │ Running  │ localhost:9000            │
───────────────────────────────────────────────────────────────────────
Local console: http://localhost:9889
(use Ctrl+C to quit)
```

You can open http://localhost:9889/ to see your local service in action.

## Deploy to akka.io

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/operations/cli/installation.html).

Deploy the service using the image name and tag from above `mvn install`:

```shell
akka service deploy shopping-cart shopping-cart-quickstart:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

## Next steps

Now that you've built and deployed a shopping cart service, take your Akka skills to the next level:

- **Expand the service**: Explore [other Akka components](https://doc.akka.io/concepts/architecture-model.html) to enhance your application with additional features.
- **Explore other Akka samples**: Discover more about Akka by exploring [different use cases](https://doc.akka.io/getting-started/samples.html) for inspiration.

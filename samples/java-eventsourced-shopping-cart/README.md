# Event Sourced Shopping Cart

## Designing

To understand the Akka SDK concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/java/development-process.html) in the documentation.

## Developing

This project demonstrates the use of Key Value Entity and View components.
To understand more about these components, see [Developing services](https://docs.kalix.io/services/)
and in particular the [Java section](https://docs.kalix.io/java/)

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

When running an Akka service locally, we need to have its companion Akka Runtime running alongside it.

To start your service locally, run:

```shell
mvn compile exec:java
```

This command will start your Akka service and a companion Akka Runtime.

## Exercising the service

With your Akka service running, any defined endpoints should be available at `http://localhost:9000`.
The ACL settings in `Setup.java` are very permissive. It has `Acl.Principal.ALL` which allows any traffic from the internet. More info at `Setup.java`.

- Add items to shopping cart

```shell
curl -i -XPOST -H "Content-Type: application/json" localhost:9000/shopping-cart/123/item -d '{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 10}'
curl -i -XPOST -H "Content-Type: application/json" localhost:9000/shopping-cart/123/item -d '{"productId":"scala-tshirt", "name":"Scala Tshirt", "quantity": 20}'
```

- See current status of the shopping cart

```shell
curl -i -XGET localhost:9000/shopping-cart/123
```

- Remove an item from the cart

```shell
curl -i -XDELETE -H "Content-Type: application/json" localhost:9000/shopping-cart/123/item/akka-tshirt
```

- Checkout the cart

```shell
curl -i -XPOST -H "Content-Type: application/json" localhost:9000/shopping-cart/123/checkout
```

## Deploying

To deploy your service, install the `kalix` CLI as documented in
[Install Kalix](https://docs.kalix.io/kalix/install-kalix.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Kalix.

Finally, you can use the [Kalix Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy kalix:deploy` which
will conveniently package, publish your docker image, and deploy your service to Kalix, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `kalix` CLI.

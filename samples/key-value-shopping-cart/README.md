# Key Value Entity Shopping Cart

## Designing

To understand the Akka concepts that are the basis for this example, see [Designing services](https://docs.kalix.
io/java/development-process.html) in the documentation.

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

To start your service locally, run:

```shell
mvn compile exec:java
```

## Exercising the service

With your Akka service running, once you have defined endpoints they should be available at `http://localhost:9000`.

Send a request to the controller:
```shell
curl -XPOST -H "Content-Type: application/json" localhost:9000/carts/create
```

or
```shell
curl -XPOST -H "Content-Type: application/json" localhost:9000/carts/prepopulated
```

* Add a new item:

```shell
curl -XPOST -H "Content-Type: application/json" localhost:9000/carts/cart1/items -d '
{"productId": "akka-tshirt", "name": "Akka t-shirt", "quantity": 3}' 
```

* Get cart:

```shell
curl localhost:9000/carts/cart1
```

* Remove a shopping cart via controller:

FIXME API support for header extraction yet
```shell
curl -XDELETE -H "UserRole: Admin" localhost:9000/carts/cart1
```

## Deploying

To deploy your service, install the `akka` CLI as documented in
[Install Akka](https://docs.kalix.io/kalix/install-kalix.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Akka.

Finally, you can use the [Akka Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy akka:deploy` which
will conveniently package, publish your docker image, and deploy your service to Akka, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `akka` CLI.

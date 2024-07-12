# Key Value Entity Shopping Cart

## Designing

To understand the Kalix concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/java/development-process.html) in the documentation.

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

When running a Kalix service locally, we need to have its companion Kalix Runtime running alongside it.

To start your service locally, run:

```shell
mvn compile exec:java
```

This command will start your Kalix service and a companion Kalix Runtime as configured in [docker-compose.yml](./docker-compose.yml) file.

## Exercising the service

With both the Kalix Runtime and your service running, once you have defined endpoints they should be available at `http://localhost:9000`.

Send a request to the controller:
```shell
curl -v -XPOST -H "Content-Type: application/json" localhost:9000/carts/create
```

or
```shell
curl -v -XPOST -H "Content-Type: application/json" localhost:9000/carts/prepopulated
```

Send a request directly to the shopping-cart entity:
```shell
curl -v -H "Content-Type: application/json" localhost:9000/akka/v1.0/entity/shopping-cart/cart2/create
```

* Add a new item:

```shell
curl -v -XPOST -H "Content-Type: application/json" localhost:9000/akka/v1.0/entity/shopping-cart/cart1/addItem -d '{"productId": "kalix-tshirt", "name": "Kalix t-shirt", "quantity": 3}' 
```

* Remove an item:

```shell
curl -XPOST -v -H "Content-Type: application/json" localhost:9000/akka/v1.0/entity/shopping-cart/cart1/removeItem -d '"kalix-tshirt"' 
```

* Remove a shopping cart via controller:

FIXME API support for header extraction yet
```shell
curl -v -XDELETE -H "UserRole: Admin" localhost:9000/carts/cart1
```

* Remove a shopping cart directly to the entity:

```shell
curl -XGET -v -H "Role: Admin" localhost:9000/akka/v1.0/entity/shopping-cart/cart2/removeCart
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

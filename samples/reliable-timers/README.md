# Implementing an Order Service as a Key Value Entity in combination with Timers

This project provides an example for how to take advantage of the timers API using the Java SDK.

## Designing

To understand the Akka concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/java/development-process.html) in the documentation.

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

To start your Akka service locally, run:

```shell
mvn compile exec:java
```

## Exercise the service

With your Akka service running, any defined endpoints should be available at `http://localhost:9000`.

```shell
curl -XPOST -H "Content-Type: application/json" localhost:9000/orders -d '{ "item":"Pizza Margherita", "quantity":1 }'
```

```shell
# use the return order id from the previous call to confirm or cancel before it expires (10 seconds by default)
curl -i -XPOST -H "Content-Type: application/json" localhost:9000/orders/{returned-order-id}/confirm 
curl -i -XPOST -H "Content-Type: application/json" localhost:9000/orders/{returned-order-id}/cancel
```

## Deploying

To deploy your service, install the `akka` CLI as documented in
[Install Akka](https://docs.kalix.io/kalix/install-kalix.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://doc.akka.io/operations/container-registries.html)
for more information on how to make your docker image available to Akka.

Finally, you can use the [Akka Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy kalix:deploy` which
will conveniently package, publish your docker image, and deploy your service to Akka, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `akka` CLI.

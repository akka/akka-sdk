# Customer Registry with Views

## Designing

To understand the Akka concepts that are the basis for this example, see [Designing services](https://docs.kalix.
io/java/development-process.html) in the documentation.

## Developing

This project demonstrates the use of Event Sourced Entity and View components.
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

* Create a customer with:

```shell
curl -i localhost:9000/customer/one \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test@example.com","name":"Testsson","address":{"street":"Teststreet 25", "city":"Testcity"}}'
```

* Retrieve the customer:

```shell
curl localhost:9000/customer/one
```

* Query by email:

```shell
curl localhost:9000/customer/by-email/test%40example.com
```

* Query by name:

```shell
curl localhost:9000/customer/by-name/Testsson
```

* Change name:

```shell
curl -i -XPATCH --header "Content-Type: application/json"  localhost:9000/customer/one/name/joe
```

* Change address:

```shell
curl -i localhost:9000/customer/one/address \
  --header "Content-Type: application/json" \
  -XPATCH \
  --data '{"street":"Newstreet 25","city":"Newcity"}'
```

## Deploying

To deploy your service, install the `akka` CLI as documented in
[Install Akka](https://docs.kalix.io/kalix/install-kalix.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Akka.

Finally, you can use the [Akka Console](https://console.kalix.io)
to create a project and then deploy your service into the project by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `akka` CLI.

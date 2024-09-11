# Customer Registry with Views

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

This command will start your Kalix service and a companion Kalix Runtime.

## Exercising the services

With your Kalix service running, any defined endpoints should be available at `http://localhost:9000`.

* Create a customer with:

```shell
curl localhost:9000/customer \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"customerId":"one","email":"test@example.com","name":"Test Testsson","address":{"street":"Teststreet 25","city":"Testcity"}}'
```

* Retrieve the customer:

```shell
curl localhost:9000/customer/one
```

* Change name:

```shell
curl localhost:9000/customer/one/name \
  --header "Content-Type: application/json" \
  -XPUT \
  --data '"Jan Banan"'
```

* Change address:

```shell
curl localhost:9000/customer/one/address \
  --header "Content-Type: application/json" \
  -XPUT \
  --data '{"street":"Newstreet 25","city":"Newcity"}'
```

* Request only the address to see changes:

```shell
curl localhost:9000/customer/one/address
```

* Request the full customer again to see changes:

```shell
curl localhost:9000/customer/one
```

## Pre-defined paths

Akka runtime provides pre-defined paths based on the component id, entity id and the method name to interact directly 
with the entities, those are however locked down from access throuh default deny-all ACLs. It is possible to explicitly
allow access on an entity using the `akka.javasdk.annotations.Acl` annotation, or by completely disabling the local 
"dev mode" ACL checking by running the service with `mvn -Dakka.javasdk.dev-mode.acl.enabled=false compile exec:java`
or changing the default in your `src/main/resources/application.conf`.

For deployed services the ACLs are always enabled.

Zero parameter methods are exposed as HTTP GET:

```shell
curl localhost:9000/akka/v1.0/entity/customer/one/getCustomer
```

Methods with a parameter are instead exposed as HTTP POST:

```shell
curl localhost:9000/akka/v1.0/entity/customer/two/create \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"customerId":"two","email":"test2@example.com","name":"Test 2 Testsson","address":{"street":"Teststreet 27","city":"Testcity"}}'
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
through the [Kalix CLI](https://docs.kalix.io/kalix/index.html).

# Customer Registry with Views

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

## Exercising the services

With your service running, any defined endpoints should be available at `http://localhost:9000`.

* Create a customer via the endpoint with:

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

* Query by email:

```shell
curl localhost:9000/customer/by-email/test@example.com
```

* Query by name:

```shell
curl localhost:9000/customer/by-name/Test%20Testsson
```

* Query by name in CSV format
```shell
curl localhost:9000/customer/by-name-csv/Jan%20Janssen
```

* Change name:

```shell
curl localhost:9000/customer/one/name \
  --header "Content-Type: application/json" \
  -XPUT \
  --data '"Jan Janssen"'
```

* Query by name again
```shell
curl localhost:9000/customer/by-name/Jan%20Janssen
```

* Change address:

```shell
curl localhost:9000/customer/one/address \
  --header "Content-Type: application/json" \
  -XPUT \
  --data '{"street":"Newstreet 25","city":"Newcity"}'  
```

* There is also a Server Sent Event (SSE) version that will first emit matching rows that exist in the view, and then
  stay running, continuously stream updates to a view matching the query:

```shell
curl localhost:9000/customer/by-name-sse/Jan%20Janssen 
```

Start this query in one terminal window while triggering updates in another terminal window, for example 
changing the name to and from "Jan Janssen" or adding more customers with different ids and the same name, to see the
updates appear.

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

The views:

```shell
curl localhost:9000/akka/v1.0/view/view_customers_by_email/getCustomer \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test2@example.com"}'
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

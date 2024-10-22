# Key Value Entity Customer Registry

## Designing

To understand the Akka concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Developing

This project demonstrates the use of Key Value Entity and View components.
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


## Exercise the service

With your Akka service running, once you have defined endpoints they should be available at `http://localhost:9000`.

* Create customers with:

```shell
curl -i localhost:9000/customer/001 \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test@example.com","name":"Test Testsson", "street":"Teststreet 25", "city":"City Test"}'
```

```shell
curl -i localhost:9000/customer/002 \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test@example.com","name":"Test Testsson II", "street":"Teststreet 25", "city":"New City Test"}'
```


```shell
curl -i localhost:9000/customer/003 \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test@example.com","name":"Test Testsson III","street":"Teststreet 25", "city":"New York City Test"}'
```

* Retrieve the customers:

```shell
curl localhost:9000/customer/001 
```

```shell
curl localhost:9000/customer/002
```

```shell
curl localhost:9000/customer/003
```

* Query by name with a wrapped result:

```shell
curl localhost:9000/customer/by-name/Test%20Testsson
```

* Query by name with a response using a summary:

```shell
curl localhost:9000/customer/by-name-summary \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"name":"Test Testsson"}'
```

* Query by cities
```shell
curl localhost:9000/customer/by-city \
  --header "Content-Type: application/json" \
  -XPOST  \
  --data '{ "cities": ["City Test", "New City Test"]}'
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
curl -i -XPATCH --header "Content-Type: application/json"  localhost:9000/customer/001/name/Jan%20Janssen
```

* Query by name again
```shell
curl localhost:9000/customer/by-name/Jan%20Janssen
```

* Change address:

```shell
curl -i localhost:9000/customer/001/address \
  --header "Content-Type: application/json" \
  -XPATCH \
  --data '{"street":"Newstreet 25","city":"Newcity"}'  
```

```shell
curl localhost:9000/customer/001 
```

* Delete a customer:

```shell
curl -i -XDELETE localhost:9000/customer/001
```

```shell
curl localhost:9000/customer/001 
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
with the entities, those are however locked down from access through default deny-all ACLs. It is possible to explicitly
allow access on an entity using the `akka.javasdk.annotations.Acl` annotation, or by completely disabling the local
"dev mode" ACL checking by running the service with `mvn -Dakka.javasdk.dev-mode.acl.enabled=false compile exec:java`
or changing the default in your `src/main/resources/application.conf`.

For deployed services the ACLs are always enabled.

Zero parameter methods are exposed as HTTP GET:

```shell
curl localhost:9000/akka/v1.0/entity/customer/002/getCustomer
```

Methods with a parameter are instead exposed as HTTP POST:

```shell
curl -i localhost:9000/akka/v1.0/entity/customer/004/create \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test4@example.com","name":"Test 4 Testsson","address":{"street":"Teststreet 27","city":"Testcity"}}'
```

The views:

```shell
curl localhost:9000/akka/v1.0/view/customers_by_email/getCustomer \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test4@example.com"}'
```

## Deploying

To deploy your service, install the `akka` CLI as documented in
[Install Akka CLI](https://doc.akka.io/akka-cli/index.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://doc.akka.io/operations/container-registries.html)
for more information on how to make your docker image available to Akka.

Finally, you can use the [Akka Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy kalix:deploy` which
will conveniently package, publish your docker image, and deploy your service to Akka, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `akka` CLI.

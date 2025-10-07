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

* There are also a Server Sent Event (SSE) endpoints. One that will first emit matching rows that exist in the view, and then
  stay running, continuously stream updates to a view matching the query:

```shell
curl localhost:9000/customer/by-city-sse/City%20Test
```

* And one that emits changes to a specific customer:

```shell
curl localhost:9000/customer/stream-customer-changes/001
```

Start with either streaming request in one terminal window while triggering updates in another terminal window, for example
changing the address to and from "City Test" or adding more customers with different ids in the same city, to see the
updates appear.

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/reference/cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy key-value-customer-registry key-value-customer-registry:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

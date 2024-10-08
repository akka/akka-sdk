# Event Sourced Customer Registry Subscriber Sample

## Designing

To understand the Akka concepts that are the basis for this example, see [Designing services](https://docs.kalix.
io/java/development-process.html) in the documentation.

The project `event-sourced-customer-registry-subscriber` is a downstream consumer of the Service to Service event stream provided by `event-sourced-customer-registry` project.

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

First start the `event-sourced-customer-registry` service. It will run with the default service port (`9000`).

To start `event-sourced-customer-registry` service locally, run:

```shell
cd ../event-sourced-customer-registry
mvn compile exec:java
```

Then start this service in another terminal. It will run with the service port (`9001`):

```shell
mvn compile exec:java
```

### Create a customer

```shell
curl localhost:9001/customer/create \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"customerId": "one","email":"test@example.com","name":"Testsson","address":{"street":"Teststreet 25", 
  "city":"Testcity"}}'
```

This call is made on the subscriber service and will be forwarded to the `event-sourced-customer-registry` service.

### Run a view query from this project

```shell
curl localhost:9001/customer/by_name/Testsson
```

The subscriber service will receive updates from customer-registry via service-to-service stream and update the view.

### Change name

```shell
curl -i -XPATCH --header "Content-Type: application/json"  localhost:9000/customer/one/name/Joe
```

This call is performed on the customer-registry directly.
  
### Check the view again

```shell
curl localhost:9001/customer/by_name/Joe
```

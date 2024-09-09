# Event Sourced Customer Registry Subscriber Sample

## Designing

To understand the Akka concepts that are the basis for this example, see [Designing services](https://docs.kalix.
io/java/development-process.html) in the documentation.

The project `eventsourced-customer-registry-subscriber` is a downstream consumer of the Service to Service event stream provided by `eventsourced-customer-registry` project.

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

First start the `eventsourced-customer-registry` service. It will run with the default service and proxy ports (`8080` and `9000`).

To start your service locally, run:

```shell
mvn compile exec:java
```

### Create a customer

```shell
curl localhost:9001/customer/create \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"id": "one","email":"test@example.com","name":"Test Testsson","address":{"street":"Teststreet 25", 
  "city":"Testcity"}}'
```

This call is made on the subscriber service and will be forwarded to the `eventsourced-customer-registry` service.

### Run a view query from this project

```shell
curl localhost:9001/akka/v1.0/view/customers_by_name/findByName \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{ "name": "Testsson" }'
```

The subscriber service will receive updates from customer-registry via service-to-service stream and update the view.

### Change name

```shell
curl  -XPOST --header "Content-Type: application/json"  localhost:9000/akka/v1.0/entity/customer/one/changeName -d '"Jan Banan"'
```

This call is performed on the customer-registry directly.
  
### Check the view again

```shell
curl localhost:9001/customers/by_name/Jan%20Banan
```

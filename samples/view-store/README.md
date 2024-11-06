# Store example (using advanced Views)

A simple store example with products, customers, and orders.

Used for code snippets in the Views documentation.

When running a Kalix service locally, we need to have its companion Kalix Runtime running alongside it.

To start your service locally, run:

```shell
mvn compile exec:java
```

This command will start your Kalix service and a companion Kalix Runtime.

## Exercising the services

With your Kalix service running, once you have defined endpoints they should be available at `http://localhost:9000`.

Create some products:

```shell
curl localhost:9000/akka/v1.0/entity/product/P123/create \
  -XPOST \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Super Duper Thingamajig",
    "price": {"currency": "USD", "units": 123, "cents": 45}
  }'
```

```shell
curl localhost:9000/akka/v1.0/entity/product/P987/create \
  -XPOST \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Awesome Whatchamacallit",
    "price": {"currency": "NZD", "units": 987, "cents": 65}
  }'
```

Retrieve a product by id:

```shell
curl localhost:9000/akka/v1.0/entity/product/P123/get
```

Create a customer:

```shell
curl localhost:9000/akka/v1.0/entity/customer/C001/create \
  -XPOST \
  --header "Content-Type: application/json" \
  --data '{
    "email": "someone@example.com",
    "name": "Some Customer",
    "address": {"street": "123 Some Street", "city": "Some City"}
  }'
 ```

Retrieve a customer by id:

```shell
curl localhost:9000/akka/v1.0/entity/customer/C001/get
```

Create customer orders for the products:

```shell
curl localhost:9000/akka/v1.0/entity/order/O1234/create \
  -XPOST \
  --header "Content-Type: application/json" \
  --data '{
    "productId": "P123",
    "customerId": "C001",
    "quantity": 42
  }'
```

```shell
curl localhost:9000/akka/v1.0/entity/order/O5678/create \
  -XPOST \
  --header "Content-Type: application/json" \
  --data '{
    "productId": "P987",
    "customerId": "C001",
    "quantity": 7
  }'
```

Retrieve orders by id:

```shell
curl localhost:9000/akka/v1.0/entity/order/O1234/get
```

```shell
curl localhost:9000/akka/v1.0/entity/order/O5678/get
```

Retrieve all product orders for a customer id using a view (with joins):

```shell
curl localhost:9000/akka/v1.0/view/joined-customer-orders/get \
    --header "Content-Type: application/json" \
    -XPOST \
    --data '{ "customerId": "C001" }'
```

Retrieve all product orders for a customer id using a view (with joins and nested projection):

```shell
curl localhost:9000/akka/v1.0/view/nested-customer-orders/get \
    --header "Content-Type: application/json" \
    -XPOST \
    --data '{ "customerId": "C001" }'
```

Retrieve all product orders for a customer id using a view (with joins and structured projection):

```shell
curl localhost:9000/akka/v1.0/view/structured-customer-orders/get \
    --header "Content-Type: application/json" \
    -XPOST \
    --data '{ "customerId": "C001" }'
```

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/akka-cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy view-store view-store:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

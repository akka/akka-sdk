# Store example (using advanced Views)

A simple store example with products, customers, and orders.

Used for code snippets in the Views documentation.

When running a Akka service locally, we need to have its companion Akka Runtime running alongside it.

To start your service locally, run:

```shell
mvn compile exec:java
```

This command will start your Akka service.

## Exercising the services

With your Akka service running, once you have defined endpoints they should be available at `http://localhost:9000`.

Create some products:

```shell
curl -i localhost:9000/products/P123 \
  -XPOST \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Super Duper Thingamajig",
    "price": {"currency": "USD", "units": 123, "cents": 45}
  }'
```

```shell
curl -i localhost:9000/products/P987 \
  -XPOST \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Awesome Whatchamacallit",
    "price": {"currency": "NZD", "units": 987, "cents": 65}
  }'
```

Retrieve a product by id:

```shell
curl localhost:9000/products/P123
```

Create a customer:

```shell
curl -i localhost:9000/customers/C001 \
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
curl localhost:9000/customers/C001
```

Create customer orders for the products:

```shell
curl -i localhost:9000/orders/O1234 \
  -XPOST \
  --header "Content-Type: application/json" \
  --data '{
    "productId": "P123",
    "customerId": "C001",
    "quantity": 42
  }'
```

```shell
curl -i localhost:9000/orders/O5678 \
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
curl localhost:9000/orders/O1234
```

```shell
curl localhost:9000/orders/O5678
```

Retrieve all product orders for a customer id using a view (with joins):

```shell
curl localhost:9000/orders/joined-by-customer/C001
```

Retrieve all product orders for a customer id using a view (with joins and nested projection):

```shell
curl localhost:9000/orders/nested-by-customer/C001
```

Retrieve all product orders for a customer id using a view (with joins and structured projection):

```shell
curl localhost:9000/orders/structured-by-customer/C001
```

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/reference/cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy view-store view-store:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

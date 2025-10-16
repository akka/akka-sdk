<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Setup and configuration](setup-and-configuration/index.html)
- [Run a service locally](running-locally.html)

<!-- </nav> -->

# Run a service locally

Running a service locally is helpful to test and debug. The following sections provide commands for starting and stopping a single service locally.

## <a href="about:blank#_prerequisites"></a> Prerequisites

In order to run your service locally, you’ll need to have the following prerequisites:

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>

## <a href="about:blank#_starting_your_service"></a> Starting your service

As an example, we will use the [Shopping Cart](../getting-started/shopping-cart/build-and-deploy-shopping-cart.html) sample.

To start your service locally, run the following command from the root of your project:

```command
mvn compile exec:java
```

## <a href="about:blank#_invoking_your_service"></a> Invoking your service

After you start the service it will accept invocations on `localhost:9000`. You can use [cURL](https://curl.se/) in another shell to invoke your service.

### <a href="about:blank#_using_curl"></a> Using cURL

Add an item to the shopping cart:

```command
curl -i -XPUT -H "Content-Type: application/json" localhost:9000/carts/123/item -d '
{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 10}'
```
Get cart state:

```command
curl localhost:9000/carts/123
```

## <a href="about:blank#_shutting_down_the_service"></a> Shutting down the service

Use `Ctrl+c` to shut down the service.

## <a href="about:blank#_run_from_intellij"></a> Run from IntelliJ

The [getting started sample](../getting-started/author-your-first-service.html) and other samples include a run configuration for IntelliJ. In the toolbar you should see:

![run intellij](_images/run-intellij.png)


This is a Maven run configuration for `mvn compile exec:java`. You can also run this with the debugger and set breakpoints in the components.

## <a href="about:blank#_local_console"></a> Local console

The local console gives you insights of the services that you are running locally.

To run the console you need to install:

- [Akka CLI](../operations/cli/installation.html)
Start the console with the following command from a separate terminal window:

```command
akka local console
```
Open [http://localhost:9889/](http://localhost:9889/)

Start one or more services as described in [Starting your service](about:blank#_starting_your_service) and they will show up in the console. You can restart the services without restarting the console.

![local console](_images/local-console.png)


## <a href="about:blank#persistence-enabled"></a> Running a service with persistence enabled

By default, when running locally, persistence is disabled. This means the Akka Runtime will use an in-memory data store for the state of your services. This is useful for local development since it allows you to quickly start and stop your service without having to worry about cleaning the database.

However, if you want to run your service with persistence enabled to keep the data when restarting, you can configure
the service in `application.conf` with `akka.javasdk.dev-mode.persistence.enabled=true` or as a system property when starting the service locally.

```command
mvn compile exec:java -Dakka.javasdk.dev-mode.persistence.enabled=true
```
To clean the local database look for `db.mv.db` file in the root of your project and delete it.

## <a href="about:blank#_local_broker_support"></a> Running a service with broker support

By default, when running locally, broker support is disabled. When running a service that declares consumers or producers locally, you need to configure the broker with property `akka.javasdk.dev-mode.eventing.support=kafka` in `application.conf` or as a system property when starting the service.

```command
mvn compile exec:java -Dakka.javasdk.dev-mode.eventing.support=kafka
```
For Google PubSub Emulator, use `akka.javasdk.dev-mode.eventing.support=google-pubsub-emulator`.

|  | For Kafka, the local Kafka broker is expected to be available on `localhost:9092`. For Google PubSub, the emulator is expected to be available on `localhost:8085`. |

## <a href="about:blank#multiple_services"></a> Running multiple services locally

A typical application is composed of one or more services deployed to the same Akka project. When deployed under the same project, two different services can make [calls to each other](component-and-service-calls.html) or [subscribe to each other’s event streams](consuming-producing.html) by simply using their logical names.

The same can be done on your local machine by configuring the services to run on different ports. The services
will discover each other by name and will be able to interact.

The default port is 9000, and only one of the services can run on the default port. The other service must be configured with another port.

This port is configured in `akka.javasdk.dev-mode.http-port` property in the `src/main/resources/application.conf` file.

```xml
akka.javasdk.dev-mode.http-port=9001
```
With both services configured, we can start them independently by running `mvn compile exec:java` in two separate terminals.

## <a href="about:blank#local_cluster"></a> Running a local cluster

For testing clustering behavior and high availability scenarios, you can run your Akka service as a local cluster with multiple nodes. This allows you to simulate a distributed environment on your local machine.

### <a href="about:blank#_database_requirement"></a> Database requirement

To run in cluster mode, you need a shared database that all nodes can connect to. The `local-nodeX.conf` files configure the application to connect to a PostgreSQL database.

Before starting your cluster nodes, you must start a local PostgreSQL database. We recommend using Docker Compose for this purpose. Create a `docker-compose.yml` file with the following configuration:

```yaml
services:
  postgres-db:
    image: postgres:17
    ports:
      - 5432:5432
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    healthcheck:
      test: ["CMD", "pg_isready", "-q", "-d", "postgres", "-U", "postgres"]
      interval: 5s
      retries: 5
      start_period: 5s
      timeout: 5s
```
Start the database with:

```command
docker compose up -d
```

### <a href="about:blank#_cluster_configuration"></a> Cluster configuration

You can create a local cluster with up to 3 nodes. Each node requires its own configuration file and will run on a different HTTP port.

To start each node, use the following commands in separate terminal windows:

```command
# Node 1 (runs on port 9000)
mvn compile exec:java -Dconfig.resource=local-node1.conf

# Node 2 (runs on port 9001)
mvn compile exec:java -Dconfig.resource=local-node2.conf

# Node 3 (runs on port 9002)
mvn compile exec:java -Dconfig.resource=local-node3.conf
```

### <a href="about:blank#_port_assignment"></a> Port assignment

The cluster nodes use sequential port numbering based on your configured HTTP port:

- **Node 1**: Uses the standard HTTP port (default: 9000)
- **Node 2**: Uses standard port + 1 (default: 9001)
- **Node 3**: Uses standard port + 2 (default: 9002)
If you have configured a custom HTTP port in your `application.conf` (for example, 9010), the cluster nodes will use:

- **Node 1**: 9010
- **Node 2**: 9011
- **Node 3**: 9012
This ensures that each node in the cluster has its own unique port while maintaining a predictable numbering scheme.

<!-- <footer> -->
<!-- <nav> -->
[JSON Web Tokens (JWT)](auth-with-jwts.html) [AI model provider configuration](model-provider-details.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
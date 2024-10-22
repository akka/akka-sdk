# Build a Customer Registry with Query Capabilities

This guide will walk you through the process of creating, retrieving, and updating information from a customer registry service.

## Prerequisites

- An [Akka account](https://console.akka.io/register)
- Java 21 (we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/))
- [Apache Maven](https://maven.apache.org/install.html)
- [Docker Engine](https://docs.docker.com/get-started/get-docker/)
- [`curl` command-line tool](https://curl.se/download.html)

## Concepts

### Designing

To understand the Akka concepts behind this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

### Developing

This project demonstrates the use of Event Sourced Entity and View components. For more information, see [Developing Services](https://doc.akka.io/java/index.html).

## Build

Use Maven to build your project:

```shell
mvn compile
```

## Run Locally

To start your service locally, run:

```shell
mvn compile exec:java
```

## Steps

### 1. Create a new customer

To add a new customer to the registry, use the following command:

```shell
curl -i localhost:9000/customer/one \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test@example.com","name":"Testsson","address":{"street":"Teststreet 25", "city":"Testcity"}}'
```

### 2. Retrieve customer information

To retrieve details of a specific customer:

```shell
curl localhost:9000/customer/one
```

### 3. Query customers by email

To find a customer using their email address:

```shell
curl localhost:9000/customer/by-email/test%40example.com
```

### 4. Query customers by name

To search for a customer by their name:

```shell
curl localhost:9000/customer/by-name/Testsson
```

### 5. Update customer name

To change a customer's name:

```shell
curl -i -XPATCH --header "Content-Type: application/json" localhost:9000/customer/one/name/joe
```

### 6. Update customer address

To modify a customer's address:

```shell
curl -i localhost:9000/customer/one/address \
  --header "Content-Type: application/json" \
  -XPATCH \
  --data '{"street":"Newstreet 25","city":"Newcity"}'
```

## Troubleshooting

If you encounter issues, ensure that:

- The Customer Registry service is running and accessible on port 9000.
- Your `curl` commands are formatted correctly.
- The customer ID (e.g., "one") matches an existing customer in the registry.

## Need help?

For questions or assistance, please refer to our [online support resources](https://doc.akka.io/support/index.html).

## Deploy

To deploy your service, install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/akka-cli/index.html), and configure a Docker Registry to upload your Docker image.

Update the `dockerImage` property in the `pom.xml`, and refer to [Configuring Registries](https://doc.akka.io/operations/container-registries.html) for instructions on making your Docker image available to Akka.

Finally, use the [Akka Console](https://console.akka.io) to create a project. Deploy your service by packaging and publishing the Docker image through `mvn deploy`, then deploy the image via the `akka` CLI.

## Conclusion

Congratulations, you've successfully built and interacted with a customer registry service using Akka. This project demonstrates the power of Event Sourced Entity and View components in responsive applications. You’ve learned how to create, retrieve, update, and query customer information.

## Next steps

Now that you've built a basic customer registry, take your Akka skills to the next level:

1. **Expand the service**: Add features such as deleting customers or more complex query capabilities.
2. **Explore other Akka components**: Dive deeper into Akka's ecosystem to enhance your application.
3. **Join the community**: Visit the [Support page](https://doc.akka.io/support/index.html) to find resources where you can connect with other Akka developers and expand your knowledge.

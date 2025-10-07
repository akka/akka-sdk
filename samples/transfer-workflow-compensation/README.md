# Build a Funds Transfer Workflow Between Two Wallets

This guide demonstrates how to create a simple workflow for transferring funds between two wallets. It includes a compensation mechanism that handles scenarios where a deposit fails during a transfer, ensuring the system maintains consistency.

## Prerequisites

- A [Akka account](https://console.akka.io/register)
- Java 21 (we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/))
- [Apache Maven](https://maven.apache.org/install.html)
- [Docker Engine](https://docs.docker.com/get-started/get-docker/)
- [`curl` command-line tool](https://curl.se/download.html)

## Concepts

### Designing

To understand the Akka concepts behind this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

### Developing

This project demonstrates the use of Workflow and Event Sourced Entity components. For more information, see [Developing Services](https://doc.akka.io/java/index.html).

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

This command will start your Akka service and a companion Akka Runtime.

## Steps

### 1. Create wallet 'a'

Create wallet 'a' with an initial balance of 100:

```shell
curl -i -X POST http://localhost:9000/wallet/a/create/100
```

### 2. Create wallet 'b'

Create wallet 'b' with an initial balance of 100:

```shell
curl -i -X POST http://localhost:9000/wallet/b/create/100
```

### 3. Withdraw from wallet 'a'

Attempt to withdraw 110 from wallet 'a':

```shell
curl -i -X POST http://localhost:9000/wallet/a/withdraw/110
```

**Note**: This request results in an HTTP 400 Bad Request response due to insufficient balance in wallet 'a'.

### 4. Check wallet balances

Get wallet 'a' current balance:

```shell
curl http://localhost:9000/wallet/a
```

Get wallet 'b' current balance:

```shell
curl http://localhost:9000/wallet/b
```

### 5. Initiate transfer

Start a transfer of 10 from wallet 'a' to wallet 'b':

```shell
curl http://localhost:9000/transfer/1 \
  -X POST \
  --header "Content-Type: application/json" \
  --data '{"from": "a", "to": "b", "amount": 10}'
```

### 6. Check transfer status

Get the current state of the transfer:

```shell
curl http://localhost:9000/transfer/1
```

## Run integration tests

To run the integration tests located in `src/test/java`:

```shell
mvn verify
```

## Troubleshooting

If you encounter issues, ensure that:

- The Akka service is running and accessible on port 9000.
- Your `curl` commands are formatted correctly.
- The wallet IDs ('a' and 'b') match the ones you created.

## Need help?

For questions or assistance, please refer to our [online support resources](https://doc.akka.io/support/index.html).

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/reference/cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy transfer-workflow transfer-workflow-compensation:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

## Conclusion

Congratulations, you've successfully implemented a workflow between two wallets using Akka. This project demonstrates the power of Workflow and Event Sourced Entity components in managing complex transactions.

## Next steps

Now that you've built a basic transfer workflow, consider these next steps:

1. **Study the compensation mechanism**: Examine `TransferWorkflow.java` and `TransferWorkflowIntegrationTest.java` to understand how compensating actions are implemented when the deposit step fails after a successful withdrawal.
2. **Explore other Akka components**: Dive deeper into Akka's ecosystem to enhance your application.
3. **Join the community**: Visit the [Support page](https://doc.akka.io/support/index.html) to find resources where you can connect with other Akka developers and expand your knowledge.

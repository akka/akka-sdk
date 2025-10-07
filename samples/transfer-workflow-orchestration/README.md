# Build a Funds Transfer Workflow Between Two Wallets

This guide demonstrates how to create a simple workflow for transferring funds between two wallets. The 
`WalletService` is an external service that manages wallets. The main focus of this sample is to demonstrate the 
orchestration part of the `Workflow` component and the human in the loop aspect (accepting the transfer when the 
amount is high). Other aspects like error handling, retry strategy, compensation are covered in the [documentation]
(https://doc.akka.io/java/workflows.html#_error_handling).  


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

This project demonstrates the use of Workflow component. For more information, see [Developing Services](https://doc.
akka.io/java/index.html).

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

This command will start the service and create two wallets, 'a' and 'b', with an initial balance of 10000 each. The 
service will listen on port 9000.

## Steps

### 1. Initiate transfer

Start a transfer of 10 from wallet 'a' to wallet 'b':

```shell
curl http://localhost:9000/transfer/1 \
  -X POST \
  --header "Content-Type: application/json" \
  --data '{"from": "a", "to": "b", "amount": 10}'
```

### 2. Check transfer status

Get the current state of the transfer:

```shell
curl http://localhost:9000/transfer/1
```

### 3. Initiate transfer that requires human intervention

Start a transfer of 1001 from wallet 'a' to wallet 'b':

```shell
curl http://localhost:9000/transfer/2 \
  -X POST \
  --header "Content-Type: application/json" \
  --data '{"from": "a", "to": "b", "amount": 1001}'
```

### 4. Check transfer status

```shell
curl http://localhost:9000/transfer/2
```

### 5. Accept transfer

```shell
curl  -X POST http://localhost:9000/transfer/2/accept
```

### 6. Check transfer status

```shell
curl http://localhost:9000/transfer/2
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

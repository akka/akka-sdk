# Workflow transfer sample

A simple workflow example of funds transfer between two wallets.

Used for code snippets in the Workflow documentation.

## Designing

To understand the Akka SDK concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/java/development-process.html) in the documentation.

## Developing

This project demonstrates the use of Key Value Entity and View components.
To understand more about these components, see [Developing services](https://docs.kalix.io/services/)
and in particular the [Java section](https://docs.kalix.io/java/)

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

To start your service locally, run:

```shell
mvn compile exec:java
```

This command will start your Akka service and a companion Akka Runtime.

### Exercising the transfer

With your Akka service running, any defined endpoints should be available at `http://localhost:9000`.
The ACL settings in `TransferWorkflowSetup.java` are very permissive. It has `Acl.Principal.ALL` which allows any traffic from the internet. More info at `TransferWorkflowSetup.java`.

With your Akka service running, any defined endpoints should be available at `http://localhost:9000`.

Create wallet `a` with an initial balance

```shell
curl -i -X POST http://localhost:9000/wallet/a/create/100
```

Create wallet `b` with an initial balance

```shell
curl -i -X POST http://localhost:9000/wallet/b/create/100
```

Withdraw from wallet `a`

```shell
curl -i -X POST http://localhost:9000/wallet/a/withdraw/110
```

Get wallet `a` current balance

```shell
curl http://localhost:9000/wallet/a
```

Get wallet `b` current balance

```shell
curl http://localhost:9000/wallet/b
```

Start transfer from wallet `a` to wallet `b`

```shell
curl http://localhost:9000/transfer/1 \
  -X POST \
  --header "Content-Type: application/json" \
  --data '{"from": "a", "to": "b", "amount": 10}'
```

Get transfer state

```shell
curl http://localhost:9000/transfer/1
```

## Running integration tests

The integration tests in `src/it` are added by setting `it` as test source directory.
To run the Integration Tests in `src/it/java` use

```shell
mvn verify -Pit
```

## Deploying

To deploy your service, install the `kalix` CLI as documented in
[Install Kalix](https://docs.kalix.io/kalix/install-kalix.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Akka SDK.

Finally, you can use the [Kalix Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy kalix:deploy` which
will conveniently package, publish your docker image, and deploy your service to Kalix, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `kalix` CLI.

# ${artifactId}

## Designing

To understand the Akka concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Developing

This project contains the skeleton to create an Akka service. To understand more about these components, see [Developing services](https://doc.akka.io/java/index.html). Examples can be found [here](https://doc.akka.io/samples/index.html).

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

When running an Akka service locally.

To start your service locally, run:

```shell
mvn compile exec:java
```

This command will start your Akka service. With your Akka service running, the endpoint it's available at:

```shell
curl http://localhost:9000/hello
```

## Deploying

To deploy your service, install the `akka` CLI as documented in
[Install Akka CLI](https://doc.akka.io/akka-cli/index.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://doc.akka.io/operations/projects/container-registries.html)
for more information on how to make your docker image available to Akka.

Finally, you can use the [Akka Console](https://console.kalix.io)
to create a project and then deploy your service into the project by first packaging and publishing the docker image through `mvn deploy` and then deploying the image through the `akka` CLI.

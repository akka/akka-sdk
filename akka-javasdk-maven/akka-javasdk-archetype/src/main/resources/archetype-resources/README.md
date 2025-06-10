# ${artifactId}

## Designing

To understand the Akka concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Developing

This project contains the skeleton to create an Akka service. To understand more about these components, see [Developing services](https://doc.akka.io/java/index.html). Examples can be found [here](https://doc.akka.io/java/samples.html).

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

When running an Akka service locally.

This sample is using OpenAI. Other AI models can be configured, see [Agent model provider](https://doc.akka.io/java/agents.html#_model).

Set your [OpenAI API key](https://platform.openai.com/api-keys) as an environment variable:

[source,command line]
----
export OPENAI_API_KEY=your-openai-api-key
----

To start your service locally, run:

```shell
mvn compile exec:java
```

This command will start your Akka service. With your Akka service running, the endpoint it's available at:

```shell
curl http://localhost:9000/hello
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
akka service deploy ${artifactId} ${artifactId}:tag-name --push \
  --env OPENAI_API_KEY=your-openai-api-key
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

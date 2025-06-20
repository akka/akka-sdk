# Hello world agent

This sample uses an agent and LLM to generate greetings in different languages. It illustrates how the agent maintains contextual history in a session memory.

This sample is explained in [Author your first agentic service](https://doc.akka.io/getting-started/author-your-first-service.html).

To understand the Akka concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

This project contains the skeleton to create an Akka service. To understand more about these components, see [Developing services](https://doc.akka.io/java/index.html).

Use Maven to build your project:

```shell
mvn compile
```

When running an Akka service locally.

This sample is using OpenAI. Other AI models can be configured, see [Agent model provider](https://doc.akka.io/java/agents.html#_model).

Set your [OpenAI API key](https://platform.openai.com/api-keys) as an environment variable:

- On Linux or macOS:
  ```shell
  export OPENAI_API_KEY=your-openai-api-key
  ```

- On Windows (command prompt):
  ```shell
  set OPENAI_API_KEY=your-openai-api-key
  ```

To start your service locally, run:

```shell
mvn compile exec:java
```

This command will start your Akka service. With your Akka service running, the endpoint is available at:

```shell
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "alice", "text": "Hello, I am Alice"}'
```

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/reference/cli/index.html).

Set up secret containing OpenAI API key:

```shell
akka secret create generic openai-api --literal key=$OPENAI_API_KEY
```

Deploy the service using the image tag from above `mvn install` and the secret:

```shell
akka service deploy helloworld-agent helloworld-agent:tag-name --push \
  --secret-env OPENAI_API_KEY=openai-api/key
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html) for more information.

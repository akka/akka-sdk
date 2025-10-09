# Evaluation playground

This sample provides a playground for trying out the built-in evaluator agents.

---

### Secure Repository Token

To build you need to set up a token in one of two ways:

1. Download the [Akka CLI](https://doc.akka.io/operations/cli/installation.html), run `akka code token` and follow the instructions.
2. Set up the token manually as described [here](https://account.akka.io/token).

---

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
  
Or change the `application.conf` file to use a different model provider.

To start your service locally, run:

```shell
mvn compile exec:java
```

This command will start your Akka service. With your Akka service running, the endpoint is available at:

```shell
curl -i -XPOST --location "http://localhost:9000/eval/summarization" \
  --header "Content-Type: application/json" \
  --data '{"document": "...", "summary": "..." }'
```

```shell
curl -i -XPOST --location "http://localhost:9000/eval/hallucination" \
  --header "Content-Type: application/json" \
  --data '{"query": "...", "referenceText": "...", "answer": "..." }'
```

```shell
curl -i -XPOST --location "http://localhost:9000/eval/toxicity" \
  --header "Content-Type: text/plain" \
  --data 'I like fish'
```

```shell
curl -i -XPOST --location "http://localhost:9000/prompt/session123" \
  --header "Content-Type: application/json" \
  --data '{"systemMessage": "You are an entertaining comedian", "userMessage": "Tell me a dad joke"}'
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

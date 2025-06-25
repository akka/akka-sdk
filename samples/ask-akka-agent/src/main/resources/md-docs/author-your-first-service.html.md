<!-- <nav> -->
- [Akka](../index.html)
- [Getting Started](index.html)
- [Author your first agentic service](author-your-first-service.html)

<!-- </nav> -->

# Author your first agentic service

## <a href="about:blank#_introduction"></a> Introduction

Akka is a framework, runtime, and memory store for autonomous, adaptive agentic systems. Akka is delivered as an SDK and platform that can execute on any infrastructure, anywhere.

![Akka Agentic Platform](../concepts/_images/akka-agentic-platform.png)


Developers create services built with Akka components that - when deployed - become agentic systems. Services can be tested locally or within a Continuous Integration/Continuous Delivery (CI/CD) practice using a Testkit that is available with each Akka component. Your services are compiled into a binary that includes the Akka Runtime which enables your services to self-cluster for scale and resilience. Akka clusters are able to execute on any infrastructure whether bare metal, Kubernetes, Docker or edge. You can optionally deploy your services into an Akka-managed cloud environment which automates most Day 2 concerns. We support Serverless, Bring Your Own Cloud (BYOC), and Self-Hosted cloud-hosted environments.

| Product | Where To Start |
| --- | --- |
| Akka Orchestration | Akka provides a durable execution engine which automatically captures state at every step, and in the event of failure, can pick up exactly where they left off. No lost progress, no orphaned processes, and no manual recovery required.

You implement orchestration by creating an Akka service with the [Workflow](../java/workflows.html) component. |
| Akka Agents | Akka provides a development framework and runtime for agents. Agents can be stateful (durable memory included) or stateless. Agents can be invoked by other Akka components or run autonomously. Agents can transact with embedded tools, MCP servers, or any 3rd party data source with 100s of Akka connectors.

You implement an agent by creating an Akka service with the [Agent](../java/agents.html) component.

You implement a tool in a regular Java class or embedded within the [Agent](../java/agents.html) component.

You implement an MCP server with the [MCP Endpoint](../java/mcp-endpoints.html) component.

You implement APIs that can front an agent with the [HTTP Endpoint](../java/http-endpoints.html) and [gRPC Endpoint](../java/grpc-endpoints.html) components. |
| Akka Memory | Akka provides an in-memory, durable store for stateful data. Stateful data can be scoped to a single agent, or made available system-wide. Stateful data is persisted in an embedded event store that tracks incremental state changes, which enables recovery of system state (resilience) to its last known modification. State is automatically sharded and rebalanced across Akka nodes running in a cluster to support elastic scaling to terabytes of memory. State can also be replicated across regions for failover and disaster recovery.

Short-term (traced and episodic) memory is included transparently within the [Agent](../java/agents.html) component.

You implement long-term memory with the [Event Sourced Entity](../java/event-sourced-entities.html) and [Key Value Entity](../java/key-value-entities.html) components.

You implement propagations of cross-system state with the [View](../java/views.html) component. Views implement the Command Query Responsibility Segregation (CQRS) pattern. |
| Akka Streaming | Akka provides a continuous stream processing engine which can synthesize, aggregate, and analyze windows of data without receiving a terminating event. Data streams can be sourced from other Akka services or a 3rd party messaging broker or coming in through an Akka Endpoint. Your services can either store intermediate processing results into *Akka Memory* or trigger commands to other Akka components that take action on the data.

You produce events to a message broker with the [Producer](../java/consuming-producing.html#_event_producer) annotation.

You create a continuous incoming stream of events with the [HTTP Endpoint](../java/http-endpoints.html) or the [gRPC Endpoint](../java/grpc-endpoints.html) components.

You create a stream processor to analyze and act against a stream of data with the [Consumer](../java/consuming-producing.html) component. |

## <a href="about:blank#_composability"></a> Composability

The services you build with Akka components are composable, which can be combined to design agentic, transactional, analytics, edge, and digital twin systems. You can create services with one component or many. Let Akka unlock your distributed systems artistry!

![Akka Agentic Platform](../concepts/_images/component-composition.png)


In this guide, you will:

- Set up your development environment.
- Clone a simple project that follows the recommended [onion architecture](../concepts/architecture-model.html#_architecture).
- Explore a basic AI Agent that acts as a creative greeter.
- Explore a basic HTTP Endpoint to interact with the agent.
- Add a request body to the Endpoint.
- Run your service locally.
- Explore the local console to observe your running service.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- Git
- [OpenAI API key](https://platform.openai.com/api-keys)
Akka has support for many AI providers, and this sample is using OpenAI.

You can run this sample without an OpenAI API key, but it will be more fun if you have one. Sign up for free at [platform.openai.com/api-keys](https://platform.openai.com/api-keys). If you don’t provide an API key, it will use a predefined response instead of using AI.

## <a href="about:blank#clone_sample"></a> Clone the sample project

1. From a command line, clone the [Github Repository](https://github.com/akka-samples/helloworld-agent) in a convenient location:

```command
git clone https://github.com/akka-samples/helloworld-agent.git
```
2. Navigate to the new project directory.
3. Open it in your preferred IDE / Editor.

## <a href="about:blank#_explore_the_agent"></a> Explore the Agent

An *Agent* interacts with an AI model and maintains contextual history in a session memory.

1. Open the `src/main/java/com/example/application/HelloWorldAgent.java` file.
The *Agent* is implemented with:

HelloWorldAgent.java
```java
@ComponentId("hello-world-agent")
public class HelloWorldAgent extends Agent {

 private static final String SYSTEM_MESSAGE =
     """
     You are a cheerful AI assistant with a passion for teaching greetings in new language.
     
     Guidelines for your responses:
     - Start the response with a greeting in a specific language
     - Always append the language you're using in parenthesis in English. E.g. "Hola (Spanish)"
     - The first greeting should be in English
     - In subsequent interactions the greeting should be in a different language than
       the ones used before
     - After the greeting phrase, add one or a few sentences in English
     - Try to relate the response to previous interactions to make it a meaningful conversation
     - Always respond with enthusiasm and warmth
     - Add a touch of humor or wordplay when appropriate
     - At the end, append a list of previous greetings
     """.stripIndent();


  public Effect<String> greet(String userGreeting) {
    if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isEmpty()) {
      return effects()
          .reply("I have no idea how to respond, someone didn't give me an API key");
    }

    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(userGreeting)
        .thenReply();
  }
}
```
The system message provides system-level instructions to the AI model that defines its behavior and context. The system message acts as a foundational prompt that establishes the AI’s role, constraints, and operational parameters. It is processed before user messages and helps maintain consistent behavior throughout the interactions.

The user message represents the specific query, instruction, or input that will be processed by the model to generate a response.

## <a href="about:blank#_explore_the_http_endpoint"></a> Explore the HTTP Endpoint

An *Endpoint* is a component that creates an externally accessible API. Endpoints are how you expose your services to the outside world. Endpoints can have different protocols, such as HTTP and gRPC.

HTTP Endpoint components make it possible to conveniently define such APIs accepting and responding in JSON, or dropping down to lower level APIs for ultimate flexibility in what types of data is accepted and returned.

1. Open the `src/main/java/com/example/api/HelloWorldEndpoint.java` file.
The *Endpoint* is implemented with:

HelloWorldEndpoint.java
```java
/**
 * This is a simple Akka Endpoint that uses an agent and LLM to generate
 * greetings in different languages.
 */
// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint()
public class HelloWorldEndpoint {
  public record Request(String user, String text) {}

  private final ComponentClient componentClient;

  public HelloWorldEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/hello")
  public String hello(Request request) {
    return componentClient
        .forAgent()
        .inSession(request.user)
        .method(HelloWorldAgent::greet)
        .invoke(request.text);
  }
}
```
The `ComponentClient` is the way to call the agent or other components. The agent may participate in a session, which is used for the agent’s memory and can also be shared between multiple agents that are collaborating on the same goal.

This Endpoint exposes an HTTP POST operation on `/hello`.

You can also see that there is an *Access Control List* (ACL) on this Endpoint that allows all traffic from the Internet. Without this ACL the service would be unreachable, but you can be very expressive with these ACLs.

## <a href="about:blank#_run_locally"></a> Run locally

Set your [OpenAI API key](https://platform.openai.com/api-keys) as an environment variable:

Linux or macOS
```command
export OPENAI_API_KEY=your-openai-api-key
```
Windows 10+
```command
set OPENAI_API_KEY=your-openai-api-key
```
Start your service locally:

```command
mvn compile exec:java
```
Once successfully started, any defined Endpoints become available at `localhost:9000` and you will see an INFO message that the Akka Runtime has started.

Your "Hello World" service is now running.

In another shell, you can now use `curl` to send requests to this Endpoint.

```command
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "alice", "text": "Hello, I am Alice"}'
```
Which will reply with an AI-generated greeting, such as:

```none
Hello (English)! So great to meet you, Alice! I'm here to add some zest to our conversation with
greetings from around the world. Let's have some fun learning them together! Feel free to ask about
anything else too!

Previous greetings:
- Hello (English)
```
Try it a few more times with different text messages, for example:

```command
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "alice", "text": "I live in New York"}'
```
The AI-generated reply might be:

```none
Bonjour (French)! Ah, New York, the city that never sleeps! It's almost like you need a coffee the
size of the Eiffel Tower to keep up with it. What's your favorite thing about living in such a vibrant
city?

Previous greetings:
- Hello (English)
- Bonjour (French)
```

```command
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "alice", "text": "I like the botanical garden"}'
```

```none
¡Hola (Spanish)! The botanical garden in New York must be a refreshing oasis amidst the hustle and
bustle of the city. It's like taking a nature-themed vacation with just a subway ride! Do you have
a favorite plant or flower that you like to see there?

Previous greetings:
- Hello (English)
- Bonjour (French)
- ¡Hola (Spanish)
```

|  | What just happened?

The greetings will be in different languages each time. The AI model itself is stateless, so it wouldn’t know what languages it had used previously unless we included that information in each request to the model. Akka Agents automatically track context using **session memory**. In this case, the Agent is able to remember all the past messages and languages that were used in this session.

Here we use the user `alice` as the session identifier. Give it a try to change the user field in the HTTP request, and you will see that it starts over without previous knowledge about Alice or the used languages. |

## <a href="about:blank#_change_the_agent_prompt"></a> Change the agent prompt

In this section, you will modify the instructions for the agent and see how it changes behavior. Open the `HelloWorldAgent.java` file and edit the `SYSTEM_MESSAGE`. For example, you can add the following to the guidelines:

HelloWorldAgent.java
```java
- Include some interesting facts
```
Restart the service and use curl again:

```command
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "blackbeard", "text": "Ahoy there, matey! My name is Blackbeard"}'
```
Does it recognize the pirate greeting and include some facts about Blackbeard?

Something like:

```none
Hello, Blackbeard! (English)

What a fantastic name you have! It’s not every day I get to chat with a legendary pirate.
So tell me, do you sail the high seas or do you prefer to dock at the local coffee shop
for a pirate-themed chai latte?

Previous greetings:
1. Hello (English)

Did you know that the famous pirate Blackbeard has a fascinating history? He was known for
his fearsome appearance, often lighting slow-burning fuses in his beard during battles to
create an intimidating aura! Arrr!
```

## <a href="about:blank#_explore_the_local_console"></a> Explore the local console

The Akka local console is a web-based tool that comes bundled with the Akka CLI. It provides a convenient way to view and interact with your running service.

### <a href="about:blank#_install_the_akka_cli"></a> Install the Akka CLI

Starting the local console requires using the Akka CLI.

Install the Akka CLI:

|  | In case there is any trouble with installing the CLI when following these instructions, please check [Install the Akka CLI](../operations/cli/installation.html). |
Linux Download and install the latest version of `akka`:

```bash
curl -sL https://doc.akka.io/install-cli.sh | bash
```
macOS The recommended approach to install `akka` on macOS, is using [brew](https://brew.sh/)

```bash
brew install akka/brew/akka
```
Windows
1. Download the latest version of `akka` from [https://downloads.akka.io/latest/akka_windows_amd64.zip](https://downloads.akka.io/latest/akka_windows_amd64.zip)
2. Extract the zip file and move `akka.exe` to a location on your `%PATH%`.
Verify that the Akka CLI has been installed successfully by running the following to list all available commands:

```command
akka help
```

### <a href="about:blank#_start_the_local_console"></a> Start the local console

1. Start the local console.

```bash
akka local console
```

```bash
⠸ Waiting for services to come online...

────────────────────────────────────────────────────────────
Local console: http://localhost:9889
(use Ctrl+C to quit)
```
2. Once the console and service is running, you will see a message like this:

```bash
───────────────────────────────────────────────────────────────────────
│ SERVICE                      │ STATE    │ ADDRESS                   |
───────────────────────────────────────────────────────────────────────
│ helloworld-agent             │ Running  │ localhost:9000            │
───────────────────────────────────────────────────────────────────────
Local console: http://localhost:9889
(use Ctrl+C to quit)
```
3. You can then access the local console in your browser at:

[http://localhost:9889](http://localhost:9889/)
4. Navigate to your service’s Endpoint, which will be available [here](http://localhost:9889/services/helloworld-agent/components/com.example.api.HelloWorldEndpoint).

![hello world local console](_images/hello-world-local-console.png)


You can also see the details of the session in the `SessionMemoryEntity`.

![hello world session memory](_images/hello-world-session-memory.png)


This is a simple Hello World service, so there isn’t much to see here yet. However, as you build more complex services, the console will become a more valuable tool for monitoring and debugging.

## <a href="about:blank#_next_steps"></a> Next steps

Now that you have a basic service running, it’s time to learn more about building real services in Akka. See [multi-agent activity planner](planner-agent/index.html) to build a more realistic application.

<!-- <footer> -->
<!-- <nav> -->
[Getting Started](index.html) [AI Planner Part 1: The activity agent](planner-agent/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
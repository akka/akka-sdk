# Hello world agent

This sample uses an agent and LLM to generate greetings in different languages. It illustrates how the agent maintains contextual history in a session memory.

This sample is explained in [Author your first agentic service](https://doc.akka.io/getting-started/author-your-first-service.html).

To understand the Akka concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

This project contains the skeleton to create an Akka service. To understand more about these components, see [Developing services](https://doc.akka.io/sdk/index.html).

---

### Secure Repository Token

Building requires a secure repository token, which is set up as part of [Akka CLI](https://doc.akka.io/getting-started/quick-install-cli.html)'s `akka code init` command.

If you still need to configure your system with the token there are two additional ways:

1. Use the Akka CLI's `akka code token` command and follow the instructions.
2. Set up the token manually as described [here](https://account.akka.io/token).

---

Use Maven to build your project:

```shell
mvn compile
```

When running an Akka service locally.

This sample is using OpenAI. Other AI models can be configured, see [Agent model provider](https://doc.akka.io/sdk/agents.html#_model).

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
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "alice", "text": "Hello, I am Alice"}'
```

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/operations/cli/installation.html).

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

---

## Spec-Driven Development (SDD)

Spec-Driven Development (SDD) is an AI-assisted coding paradigm where structured, natural-language specifications serve as the executable source of truth. Instead of writing code directly, developers use tools like GitHub Spec Kit to define architectural intent and guide AI agents through a strict, multi-phase implementation pipeline. By iterating on these living specifications rather than the raw codebase, this project rapidly generates features while maintaining strict alignment with system constraints.

### Specification artifacts

The `specs/` directory contains the SDD artifacts for this application:

- `specs/001-hello-world-agent/spec.md` — Feature specification (what and why)
- `specs/001-hello-world-agent/plan.md` — Implementation plan (architecture and components)
- `specs/001-hello-world-agent/tasks.md` — Implementation tasks (step-by-step checklist)
- `specs/001-hello-world-agent/research.md` — Research decisions
- `specs/001-hello-world-agent/contracts/http-api.md` — HTTP API contract
- `specs/001-hello-world-agent/quickstart.md` — Setup and curl examples

### Speckit commands

The following `/speckit` slash commands are available in Claude Code for iterating on specifications and adding new features:

| Command | Description |
|---------|-------------|
| `/speckit.specify <description>` | Create or update a feature specification from a natural language description |
| `/speckit.clarify` | Identify underspecified areas in the spec and ask targeted clarification questions |
| `/speckit.plan` | Generate an implementation plan from the specification |
| `/speckit.tasks` | Generate actionable, dependency-ordered tasks from the plan |
| `/speckit.analyze` | Cross-artifact consistency and quality analysis across spec, plan, and tasks |
| `/speckit.implement` | Execute the implementation plan by processing tasks in tasks.md |
| `/speckit.checklist` | Generate a custom checklist for the current feature |
| `/speckit.constitution` | Create or update the project constitution |

### Exercise: Re-implement from specs

As an exercise, you can re-implement this application entirely from the specifications:

1. Remove all source files: `rm -rf src/`
2. Uncheck all tasks in `specs/001-hello-world-agent/tasks.md` (reset checkboxes to `- [ ]`)
3. Run `/speckit.implement` in Claude Code to generate the implementation from the specs

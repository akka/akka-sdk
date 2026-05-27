<!-- <nav> -->
- [Akka](../index.html)
- [Getting started & Tutorials](index.html)
- [Spec-first greeting agent](spec-your-first-agent.html)

<!-- </nav> -->

# Build your first agent with Spec-Driven Development

## <a href="about:blank#_introduction"></a> Introduction

In this guide, you will:

- Verify your development environment
- Specify the "hello world" agent, following the pattern described in the [Spec-Driven Development](../sdk/spec-driven-development.html) documentation.
- Explore a basic AI Agent that acts as a creative greeter.
- Explore a basic HTTP Endpoint to interact with the agent.
- Run your service locally with the `/akka.build` agent command.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- An AI coding assistant like Claude Code to build and implement the specification
- [OpenAI API key](https://platform.openai.com/api-keys)
Akka has support for many AI providers, and this sample code uses OpenAI. Sign up for free at [platform.openai.com/api-keys](https://platform.openai.com/api-keys). Note that the *application code* uses OpenAI, not necessarily your AI coding assistant.

|  | Before proceeding, you **MUST** set the `OPENAI_API_KEY` environment variable and make it available to child processes (on Mac/Linux, that means using `export`) before running `akka specify` or starting your coding assistant in the terminal. |

### <a href="about:blank#_additional_prerequisites_for_those_without_an_ai_marketplace"></a> Additional prerequisites for those without an AI marketplace

If you are not using the Akka plugin (which installs these automatically), you will need:

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- Git
- [Akka CLI](quick-install-cli.html)

## <a href="about:blank#_install_the_claude_code_plugin"></a> Install the Claude Code Plugin

If you’re using Claude Code, then you can take advantage of the plugin marketplace to install the [Akka specification plugin](https://github.com/akka/ai-marketplace), which will also automatically install and verify the Akka CLI for you.

With Claude Code running within the directory you plan to use for your new project, issue the following commands:

```none
/plugin marketplace add akka/ai-marketplace
/plugin install akka@ai-marketplace
/reload-plugins
/akka:setup
```
You should now have everything you need to easily use Spec-Driven Development to build Akka applications, including the CLI. A typical output from Claude Code looks as follows (your output will vary slightly):

```none
/akka:setup — Complete

    Java 25 (Temurin)      ✓ installed
    Maven 3.9.11           ✓ installed
    Akka CLI dev-3.0.53    ✓ installed
    Akka download token    ⚠ could not verify (dot-directory restriction)
    Docker                 ✓ available
    MCP capabilities       ✓ all available (sdd, build, local, platform, git)
    SDK version            ✓ 3.5.16 (latest)
    Project scaffolded     ✓ com.example:akka-ssd-test-19
    Akka context docs      ✓ 203 files
    AI keys                ⏭ deferred (not needed for standard development)

  One thing to verify: I couldn't check ~/.m2/settings.xml due to the dot-directory permission restriction. If you haven't previously run `akka code token`, you'll need to do so before Maven can download Akka SDK dependencies. You can run it with `! akka code token` — it will open a browser for a free Akka account login.

  IMPORTANT: The MCP server configuration (.mcp.json) was created during setup. Claude Code only loads MCP servers at session start, so you need to restart. Use `claude --resume` to restart without losing your session context. After restarting, the Akka MCP tools will be available.

  Ready to go! After restarting, run /akka:specify to start building your first feature.
```
As the instructions say, you’ll need to restart Claude Code in order to load the newly installed MCP server from the Akka CLI.

|  | From now on, any time you see `/akka.{command}` in the documentation referring to using Claude Code *without* the marketplace plugin, you should substitute the `/akka:{command}` plugin-based alternative. |

|  | Not using Claude Code? Akka supports 30 different AI assist utilities. Visit [Spec-Driven Development](../sdk/spec-driven-development.html) for details on how to configure each. |
Now you’re ready to initialize a specification.

## <a href="about:blank#_initialize_a_new_spec_driven_project"></a> Initialize a new spec-driven project

If you’re using the Claude Code Akka marketplace plugin, you can skip this step and go directly to specifying the main feature.

If you’re using the CLI to start a new project using Spec-Driven Development, we use the `akka specify init` command. You have a few options here:

- Type `akka specify init helloworld-agent` to create a new directory called `helloworld-agent`
- Manually create your own directory, `cd` into that directory, and run `akka specify init .`
This will copy the default Akka template files, give you a chance to add your own additional constitution (which you can skip for this exercise), and set up an empty Akka service project with a Maven `pom.xml` file. It will also set the directory up as a git repository.

Once you’ve successfully initialized the project, you can start Claude Code in your terminal. This guide doesn’t cover using the web app.

## <a href="about:blank#_specify_the_main_feature"></a> Specify the main feature

To specify the main feature of the application, we use `/akka.specify` (`/akka:specify` in the Claude Code plugin). The following prompt shows this in action. If you’re feeling adventurous, you can experiment with changing the prompt to see how it affects the final results. Copy and paste this `/akka.specify` (or `/akka:specify`) command into your agent session:

| **/akka.specify** main feature - *The greeter agent generates greetings in different languages using an LLM. The consumer of the agent supplies a name and some greeting text in their native language. The agent will then respond with a friendly greeting in English. Each subsequent message sent by a given user will result in a greeting in a randomly chosen language that hasn’t yet been used in that agent session.* |
After submitting that prompt, you will be asked for permission to run a number of MCP tools. These relate to setting up a new git branch and creating the first specification file (you’re likely to be asked for permission to overwrite the spec file). If you typed the prompt exactly as above, then you will have a new branch called `001-main-feature`.

When using Claude, you will get a summary of the specification as the assistant understands it. It might look like the following:

```none
Branch: 001-greeter-agent
  Spec file: specs/001-greeter-agent/spec.md
  Checklist: specs/001-greeter-agent/checklists/requirements.md — all items pass

  What the spec covers:

  - User Story 1 (P1): First greeting always in English, personalized with the user's name
  - User Story 2 (P1): Subsequent greetings in randomly chosen, non-repeating languages per
  session
  - User Story 3 (P2): Session isolation — independent language tracking across sessions
  - 7 functional requirements, 4 success criteria, 4 edge cases

  No clarification questions needed — the feature description was clear enough to make
  reasonable assumptions (documented in the Assumptions section).
```
If this summary is different than what you expected or intended, then you should re-run the specify command with a new specification prompt.

Under normal circumstances you would use `/akka.clarify` to fill in potential gaps in the specification. For a very simple 1-feature application like this, we can skip that step.

## <a href="about:blank#_create_a_plan"></a> Create a plan

Once we’ve got a specification we’re pleased with, we need to create an implementation plan. This plan tells the coding assistant *how* to implement the specification. Here is where we dictate the technical architecture and requirements.

Copy and paste this slash command into your Claude session (use `/akka:plan` if you’re using the marketplace plugin):

| **/akka.plan** The greeter application is a single RESTful endpoint that exposes a `/greet` route. This accepts a JSON payload with the `user` and `text` fields, which are then incorporated into the user message sent to the OpenAI model. This service has no authentication or other guardrails. Session history for agent conversations is to be implemented using the default and provided agent session support in the SDK. The system prompt for the greeter agent should include the following points without modification:

* You are a cheerful AI assistant with a passion for teaching greetings in new languages

* Start the response with a greeting in a specific language

* Always append the language you’re using in parenthesis in English. E.g. "Hola (Spanish)"

* The first greeting should be in English

* In subsequent interactions the greeting should be in a different language than the ones used before

* After the greeting phrase, add one or a few sentences in English

* Try to relate the response to previous interactions to make it a meaningful conversation

* Always respond with enthusiasm and warmth

* Add a touch of humor or wordplay when appropriate

* At the end, append a list of previous greetings

This implementation should result in a single agent, the `HelloWorldAgent`, and a single endpoint, the `HelloWorldEndpoint`. There are no domain objects nor are there any entity components. The application should obtain its model target configuration from the standard Akka SDK model provider configuration. If insufficient configuration is provided, then the endpoint request should fail with a 500 error code. |
As usual with AI, the results will vary. Here’s an example of a summary of the plan that we might get with Claude Code:

```none
Branch: 001-greeter-agent

  Generated Artifacts

  ┌───────────────────────────────────────────────┬──────────────────────────────────────┐
  │                     File                      │               Purpose                │
  ├───────────────────────────────────────────────┼──────────────────────────────────────┤
  │ specs/001-greeter-agent/plan.md               │ Implementation plan with component   │
  │                                               │ design                               │
  ├───────────────────────────────────────────────┼──────────────────────────────────────┤
  │                                               │ Decisions on session memory, model   │
  │ specs/001-greeter-agent/research.md           │ config, session ID strategy, error   │
  │                                               │ handling                             │
  ├───────────────────────────────────────────────┼──────────────────────────────────────┤
  │ specs/001-greeter-agent/data-model.md         │ Data model (API records only, no     │
  │                                               │ entities)                            │
  ├───────────────────────────────────────────────┼──────────────────────────────────────┤
  │ specs/001-greeter-agent/contracts/http-api.md │ HTTP API contract for POST /greet    │
  ├───────────────────────────────────────────────┼──────────────────────────────────────┤
  │ specs/001-greeter-agent/quickstart.md         │ Build, run, and test instructions    │
  └───────────────────────────────────────────────┴──────────────────────────────────────┘

  Architecture Summary

  - HelloWorldAgent — single Akka Agent with the verbatim system prompt, using SDK session
  memory and config-driven model provider
  - HelloWorldEndpoint — POST /greet accepting {"user": "...", "text": "..."}, using user as
   session ID, returning 500 on model config failure
  - No domain objects, no entities, no views — pure agent + endpoint
  - Tests — agent unit test with TestModelProvider, endpoint integration test with
  httpClient

  Constitution Check

  All four principles pass: Akka SDK First, Design Principles, Test Coverage, Simplicity.
```
Remember that this is just the plan - *no code has been written yet.*

This plan looks good and the architecture is exactly what we wanted, so now we can convert this plan into an itemized set of working tasks.

## <a href="about:blank#_generate_tasks"></a> Generate tasks

To generate the tasks from this plan, run `/akka.tasks` (or `/akka:tasks` when using the plugin)

Here’s a sample summary output from generating tasks. Note that it has analyzed which tasks can be done in parallel.

```none
Summary

  ┌───────────────────────────┬──────────────────────────────────────────┐
  │          Metric           │                  Value                   │
  ├───────────────────────────┼──────────────────────────────────────────┤
  │ Total tasks               │ 8                                        │
  ├───────────────────────────┼──────────────────────────────────────────┤
  │ Phase 1 (Setup)           │ 1 task — application.conf                │
  ├───────────────────────────┼──────────────────────────────────────────┤
  │ Phase 2 (Foundational)    │ 1 task — HelloWorldAgent                 │
  ├───────────────────────────┼──────────────────────────────────────────┤
  │ Phase 3 (US1 & US2 - MVP) │ 3 tasks — endpoint + 2 tests             │
  ├───────────────────────────┼──────────────────────────────────────────┤
  │ Phase 4 (US3)             │ 1 task — session isolation test          │
  ├───────────────────────────┼──────────────────────────────────────────┤
  │ Phase 5 (Polish)          │ 2 tasks — README + quickstart validation │
  └───────────────────────────┴──────────────────────────────────────────┘

  Parallel Opportunities

  - T004 (agent test) and T005 (endpoint integration test) can run in parallel after T003
  - T007 (README) and T008 (quickstart) can run in parallel

  MVP Scope

  User Stories 1 & 2 (Phase 3) — after just 5 tasks (T001–T005), the service is fully
  functional with a tested greeting flow.
```
With the plan and tasks ready to go, we can move on to implementation. It’s worth noting that the actual writing of the code is one of the smaller parts of the process, and is done at the end of the feature definition, not the beginning.

## <a href="about:blank#_implement_the_agent"></a> Implement the agent

Now it’s time to generate some code. Run the `/akka.implement` (or `/akka:implement` with the plugin) command and allow it to proceed with any operations for which it requests permission.

During implementation it is not unusual to see your AI assistant get a number of things wrong. Code may fail to compile and tests might not pass. We should try and leave the agent alone and let it try and fix the problems on its own before we intervene. Only if it looks like the agent is stuck (>5 attempts to fix the same thing) should we step in and provide instructions to solve the problem. This implementation is simple enough that errors and failure loops are unlikely.

If you’re not happy with the generated results, consider re-running the implement phase with a different model and/or effort level.

Here’s an example summary of an implementation phase:

```none
Summary

  ┌──────┬────────┬──────────────────────────────────────────────────────────────────────┐
  │ Task │ Status │                                 File                                 │
  ├──────┼────────┼──────────────────────────────────────────────────────────────────────┤
  │ T001 │ Done   │ src/main/resources/application.conf                                  │
  ├──────┼────────┼──────────────────────────────────────────────────────────────────────┤
  │ T002 │ Done   │ src/main/java/com/example/application/HelloWorldAgent.java           │
  ├──────┼────────┼──────────────────────────────────────────────────────────────────────┤
  │ T003 │ Done   │ src/main/java/com/example/api/HelloWorldEndpoint.java                │
  ├──────┼────────┼──────────────────────────────────────────────────────────────────────┤
  │ T004 │ Done   │ src/test/java/com/example/application/HelloWorldAgentTest.java       │
  ├──────┼────────┼──────────────────────────────────────────────────────────────────────┤
  │ T005 │ Done   │ src/test/java/com/example/api/HelloWorldEndpointIntegrationTest.java │
  ├──────┼────────┼──────────────────────────────────────────────────────────────────────┤
  │ T006 │ Done   │ (session isolation test in T005's file)                              │
  ├──────┼────────┼──────────────────────────────────────────────────────────────────────┤
  │ T007 │ Done   │ README.md                                                            │
  ├──────┼────────┼──────────────────────────────────────────────────────────────────────┤
  │ T008 │ Done   │ Build verified via mvn verify                                        │
  └──────┴────────┴──────────────────────────────────────────────────────────────────────┘

  All 4 tests pass (1 agent unit + 3 endpoint integration). mvn verify succeeds.
```
If you can, resist the temptation to go look at the code right now. Exercise the application and make sure it does what you want before looking at the code. If the app fails to meet core requirements, then the code is all going to be thrown away anyway as you’ll re-do the `plan` or `specify` phases.

It’s also a recommended practice to not commit this feature branch back to `main` until you’ve exercised the final result.

If we’re satisfied with these results, we can move on to the next step.

## <a href="about:blank#_exercise_the_agent_api"></a> Exercise the agent API

Exercising the agent API is easy. At this point, run `/akka.build` (or `/akka:build`). This will recompile your project, run tests, and issue a sample command to the RESTful API endpoint.

If you can verify that no previous instances of your service are running and that no stale processes are running on port `9000`, this will save time by not making your AI assistant do that check.

```none
Local Build Results

  ┌───────────────┬─────────────────────────────────────────────────────┐
  │     Step      │                       Status                        │
  ├───────────────┼─────────────────────────────────────────────────────┤
  │ Compilation   │ PASS                                                │
  ├───────────────┼─────────────────────────────────────────────────────┤
  │ Tests         │ 4 passed, 0 failed                                  │
  ├───────────────┼─────────────────────────────────────────────────────┤
  │ Local service │ Running on localhost:9000                           │
  └───────────────┴─────────────────────────────────────────────────────┘

  Endpoint available: POST http://localhost:9000/greet

  You can test it with:

  curl -X POST http://localhost:9000/greet \
    -H "Content-Type: application/json" \
    -d '{"user": "Maria", "text": "Buenos dias, soy Maria"}'

  Note: You'll need OPENAI_API_KEY set for live LLM responses. Without it, the request will
  return a 500 error as designed.

  Next steps: Iterate with /akka.implement or ship with /akka.deploy.
```
We shouldn’t have to leave the Claude session for any reason.

## <a href="about:blank#_next_steps"></a> Next steps

Next we move on to a more involved tutorial that uses multiple components to create a multi-agent trip planning system. We also encourage you to take a look at the various samples and tutorials and see if you can work backwards from the implementation to come up with the specification and then go through a spec-driven flow.

The more you go through the spec-driven process, the better and more precise you’ll find your specs.

If you want to see how the code-first version of this tutorial unfolds, see [Build your first agent](author-your-first-service.html).

<!-- <footer> -->
<!-- <nav> -->
[Getting started & Tutorials](index.html) [Hello world agent](author-your-first-service.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
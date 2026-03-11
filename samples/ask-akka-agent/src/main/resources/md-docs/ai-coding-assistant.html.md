<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Using an AI coding assistant](ai-coding-assistant.html)

<!-- </nav> -->

# Using an AI coding assistant

AI coding assistants can increase your productivity when developing Akka services. This guide will give you some practical hints of how to setup Akka knowledge and how to prompt the AI assistant. We are using [Claude code](https://docs.claude.com/en/docs/claude-code/overview), [Qodo](https://www.qodo.ai/), [Cursor](https://www.cursor.com/) and [IntelliJ IDEA](https://www.jetbrains.com/help/idea/ai-assistant-in-jetbrains-ides.html) as examples of such coding assistants, but the techniques are applicable for other tools as well.

Some key benefits of using an AI coding assistant:

- **Scaffolding:** Quickly create a foundational structure for your Akka services, ready to be filled with business logic.
- **Faster learning:** Accelerate your understanding and application of Akka concepts.
- **Code comprehension:** Get an overview explanation of existing codebases or sample applications.
- **Debugging:** Get assistance in identifying and resolving issues.
- **Test generation:** Rapidly generate tests to ensure code correctness.
In summary, we will look at the following:

1. Akka documentation in LLM-friendly format
2. Configure your AI assistant (Cursor, Qodo, etc.) to use this documentation
3. Include relevant sample code as additional context
4. Use our coding guidelines template for better code generation
5. Follow the prompt examples for common Akka development tasks

## <a href="about:blank#_why_doesnt_ai_know_about_latest_akka"></a> Why doesn’t AI know about latest Akka?

The LLMs have been trained on web content that didn’t include the latest documentation of the Akka SDK. If you ask it questions about Akka it will answer based on the knowledge it was trained on, which most certainly was about the Akka libraries. Some assistants will try to use web search to retrieve the latest information, but that is typically not enough and not an efficient way for a coding assistant. For example, if you ask:

```none
What are the core components of Akka?
```
The AI response will look something like this…​

```none
Akka is a toolkit for building highly concurrent, distributed,
and resilient message-driven applications...
1. Actor System ...
2. Actors ...
...
```
This is correct for the Akka libraries but not helpful when developing with the Akka SDK.

We need to give the LLM knowledge about the latest Akka documentation.

## <a href="about:blank#_llm_friendly_documentation"></a> LLM-friendly documentation

In addition to human-readable HTML, the Akka documentation is also published in markdown format that an LLM can understand in a good and efficient way. Each page has a corresponding `.md` page, for example [event-sourced-entities.html.md](https://doc.akka.io/sdk/event-sourced-entities.html.md).

The markdown documentation is published according to the widely used standard proposal [llmstxt](https://llmstxt.org/):

- [llms.txt](https://doc.akka.io/llms.txt) - website index
- [llms-full.txt](https://doc.akka.io/llms-full.txt) - full, concatenated, documentation
- [llms-ctx.txt](https://doc.akka.io/llms-ctx.txt) - full documentation without the optional parts of llms.txt
- [llms-ctx-full.txt](https://doc.akka.io/llms-ctx-full.txt) - full documentation including the optional parts of llms.txt

## <a href="about:blank#_setup_ai_assistant_to_use_the_akka_documentation"></a> Setup AI assistant to use the Akka documentation

We need to make the AI coding assistant aware of the latest Akka documentation. The Akka CLI will help you with this. [Install the Akka CLI](../getting-started/quick-install-cli.html) and run:

```command
akka code init
```

1. Select an example, such as the "Hello world agent".
2. The CLI will download the Akka documentation and place it in the `akka-context` directory.
3. Select which AI coding assistant to use. The CLI will describe the additional steps of how to configure the assistant.
Make sure that you download the latest documentation regularly to make use of documentation improvements and new features. This can be done with:

```command
akka code context-update .
```
The `akka-context` documentation bundle is also available as [akka-docs-md.zip](../sdk/_attachments/akka-docs-md.zip) if you prefer that.

Add `akka-context` to your `.gitignore` file, if you use git.

If your AI coding assistant isn’t included in the Akka CLI you can use the [AGENTS.md](https://agents.md/) option in the Akka CLI, or download it from [Akka AGENTS.md](../_attachments/AGENTS.md).

### <a href="about:blank#_notes_about_claude_code"></a> Notes about Claude code

For [Claude code](https://www.anthropic.com/claude-code) the `CLAUDE.md` from the Akka CLI includes the detailed instructions from `AGENTS.md`. The reason for the separation into two files is that you should be able to download updated versions of `AGENTS.md` and have your custom instructions in `CLAUDE.md` remain unchanged.

This `CLAUDE.md` also defines an iterative generation workflow. You can modify or remove that if you prefer another way of working with Claude.

### <a href="about:blank#_notes_about_copilot"></a> Notes about Copilot

If you are using [Github Copilot](https://github.com/features/copilot) as a standalone editor or [as a CLI](https://docs.github.com/en/copilot/concepts/agents/about-copilot-cli), then it should automatically detect the context from the files used during code initialization. If it is not using the `akka-context` directory, then you can manually add this directory by attaching it to context in your copilot chat, either with the paperclip (📎) icon in the IDE or with the `#` prefix in either app.

### <a href="about:blank#_notes_about_cursor"></a> Notes about Cursor

An alternative to the local `akka-context` documentation is to use documentation from a custom website, and include relevant information to the LLM by similarity search of that content.

You can point it directly to `https://doc.akka.io/llms-full.txt`, which is already in LLM-friendly markdown format.

You find the settings for custom documentation in: Cursor Settings > Features > Docs

In the chat window it is important that you include the Akka documentation as context. Type `@Docs` - tab, and select the custom Akka docs that you added in the settings.

### <a href="about:blank#_notes_about_qodo"></a> Notes about Qodo

In the chat window it is important that you include the Akka documentation as context. Use `@` to include the `akka-context` folder.

### <a href="about:blank#_notes_about_intellij_idea_ai_assistant"></a> Notes about IntelliJ IDEA AI assistant

For the AI assistant in IntelliJ IDEA you can download the [llms-ctx.txt](https://doc.akka.io/llms-ctx.txt) file and place it in the root of the project directory. The AI assistant will include relevant information to the LLM.

Add `llms-ctx.txt` to your `.gitignore` file, if you use git.

It is important that you include the Akka documentation as context by enabling `Codebase` in the chat window.

Make sure that you download the latest documentation regularly to make use of documentation improvements and new features.

### <a href="about:blank#_notes_about_gemini_cli"></a> Notes about Gemini CLI

For Gemini CLI you can use `akka code init` and select Claude as coding assistant. Then rename the file `CLAUDE.md` to `GEMINI.md`.

If Gemini CLI doesn’t make use of the documentation in `akka-context` directory it can be because it is listed in `.gitignore`. Then you have to remove `akka-context` from `.gitignore`.

### <a href="about:blank#_notes_about_openai_codex"></a> Notes about OpenAI Codex

If Codex doesn’t make use of the documentation in `akka-context` directory it can be because it is listed in `.gitignore`. Then you have to remove `akka-context` from `.gitignore`.

## <a href="about:blank#_verify_that_it_works"></a> Verify that it works

To verify that the assistant now knows about Akka, we can ask the question again:

```none
What are the core components of Akka?
```
it should answer with something like

```none
1. Event Sourced Entities ...
2. Key Value Entities ...
3. HTTP Endpoints ...
...
```

## <a href="about:blank#_include_sample_source_code"></a> Include sample source code

Even though the documentation includes comprehensive code snippets it can be good to include the full source code of one or a few samples. This makes it easier for the coding assistant to follow the same structure as the sample.

1. Pick one or a few samples from [Additional samples](../getting-started/samples.html), which are relevant to what you are developing. If you are just getting started learning Akka you can pick the Shopping Cart sample.
2. Clone the sample GitHub repository. Pull latest if you have already cloned the repository before.
3. Copy the source code to a folder `akka-context/` in your development project, e.g. `akka-context/travel-agent/src`.
4. Add `akka-context/` to your `.gitignore` file, if you use git.
Include the samples (`akka-context/`) as context in the chat window.

Make sure that you pull the latest samples regularly to make use of improvements and new features.

## <a href="about:blank#_coding_guidelines"></a> Coding guidelines

The coding assistant will generate more accurate code if we give it some detailed instructions. We have prepared such [guidelines](ai-coding-assistant-guidelines.html) that you can use as a template. You find even more detailed instructions in [AGENTS.md](../_attachments/AGENTS.md)

For some assistants you can define instructions in configuration settings or files like `AGENTS.md` or `CLAUDE.md`.

If your AI coding assistant doesn’t support that, you can include the guidelines directly in the chat session prompt like this:

```none
Don't generate any code yet, but remember the following guidelines and use them when writing code in this project.

<paste guidelines>
```
You can copy-paste the guidelines from [ai-coding-assistant-guidelines.html.md](https://doc.akka.io/sdk/ai-coding-assistant-guidelines.html.md)

## <a href="about:blank#_prompt_examples"></a> Prompt examples

Here are some examples of prompts that you can use as templates when giving instruction to the coding assistant.

### <a href="about:blank#_general_advise"></a> General advise

- Develop incrementally and don’t ask for too much at the same time.
- Compile and test after each step using `mvn test` or `mvn verify`. Fix compilation errors and test failures before proceeding too far.
- Commit the changes often so that you can compare and revert if something goes wrong.
- Be precise in the instructions and make corrections by further instructions if it doesn’t generate what you want.
- Even with custom docs, AI might still occasionally "hallucinate" or provide slightly off answers. It’s important to include time for human review in the development loop.
- Make sure that the AI does not introduce security vulnerabilities. You are still responsible for the generated code.
- Some things are just easier with ordinary IDE tooling, such as simple refactoring.

### <a href="about:blank#_entities"></a> Entities

```none
Create a credit card entity, use the shopping cart sample as template.
```
That will probably generate an event sourced entity, but you can be more specific by saying "event sourced entity" or "key value entity."

To matches your business domain you should be more precise when it comes to what to include in the domain objects. Start small, and iterate.

```none
Let's add a unit test for the entity
```
Ensure it uses the `EventSourcedTestKit`, which is described in the coding guidelines.

### <a href="about:blank#_endpoints"></a> Endpoints

```none
Add an http endpoint for the entity
```

```none
Add example curl commands for the endpoint to the readme
```

```none
Add an integration test for the endpoint
```
Ensure it uses the integration test is using the `httpClient` of the `TestKitSupport`, which is described in the coding guidelines.

### <a href="about:blank#_views"></a> Views

```none
Add a View that lists brief credit card information given a cardholder name
```

```none
Add an integration test for the view
```

```none
Include the endpoint for the view in the existing CreditCardEndpoint
```

```none
add example curl commands for that in the readme
```

### <a href="about:blank#_workflow"></a> Workflow

```none
Create a Workflow that transfers money from an external bank service to the credit card. It should have the following steps:
- withdraw
- deposit
- compensate-withdraw

The transitions for a transfer:
- starts with the bank withdrawal
- if withdrawal was successful it continues with the credit card deposit
- if the deposit fails for some reason it should return the money to the bank account in the compensate-withdraw step
```

### <a href="about:blank#_runtime_errors"></a> Runtime errors

If you see an error message when running the application or tests you can try to ask the assistant for help finding the bug. Paste the error message in the chat question.

<!-- <footer> -->
<!-- <nav> -->
[Developer best practices](dev-best-practices.html) [Running a local cluster](local-cluster.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
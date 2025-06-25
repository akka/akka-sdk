<!-- <nav> -->
- [Akka](../index.html)
- [Getting Started](index.html)
- [Samples](samples.html)

<!-- </nav> -->

# Samples

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |
Samples are available that demonstrate important patterns and abstractions. These can be cloned from their respective repositories. Please refer to the `README` file in each repository for setup and usage instructions.

AI multi-agent system that suggests real-world activities Demonstrating how to build a multi-agent system using Akka and an LLM model. A workflow manages the user query process, handling the sequential steps of agent selection, plan creation, execution, and summarization.

[Step-by-step guide](planner-agent/index.html)

[Github Repository](https://github.com/akka-samples/multi-agent),
Level: Intermediate

AI agent that performs a RAG workflow Illustrates how to create embeddings for vector databases, how to consume LLMs and maintain conversation history, use RAG to add knowledge to fixed LLMs, and expose it all as a streaming service. It uses MongoDB Atlas and OpenAI.

[Step-by-step guide](ask-akka-agent/index.html)

[Github Repository](https://github.com/akka-samples/ask-akka-agent),
Level: Intermediate

AI agent that creates personalized travel itineraries Illustrates reliable interaction with an LLM using a workflow. Entities are used for durable state of user preferences and generated trips.

[Github Repository](https://github.com/akka-samples/travel-agent),
Level: Beginner

AI agent that leverages an LLM to process medical discharge summaries It assigns tags to the summaries, while also enabling human verification and comparative analysis. Interactions from a workflow with an agent using OpenAI LLM.

[Github Repository](https://github.com/akka-samples/medical-tagging-agent), Level: Intermediate

AI agent that creates release notes summaries Every time there is a release from set up GitHub repositories. Interactions with Anthropic Claude from an agent and using tools to retrieve detailed information from GitHub. Entities are used for storing release summaries. Timed action looks for new releases periodically and creates the summary using the LLM.

[Github Repository](https://github.com/akka-samples/changelog-agent), Level: Intermediate

Agentic workflow for customer service The real-estate customer service agent is demonstrating how to combine Akka features with an LLM model. It illustrates an agentic workflow for customer service. It processes incoming real-estate inquiries, analyzes the content to extract details, provides follow-up when needed and saves the collected information for future reference.

[Github Repository](https://github.com/akka-samples/real-estate-cs-agent), Level: Intermediate

Trip booking agent using tools This app represents an agency that searches for flights and accommodations. It’s composed by an LLM (Anthropic) using Spring AI and tools to find flights, accommodations and sending mails.

[Github Repository](https://github.com/akka-samples/trip-agent), Level: Intermediate

Analyze sensor data by an agent AI agent that uses an LLM to analyze data from fitness trackers, medical records and other sensors. Integration with Fitbit and MongoDB Atlas.

[Github Repository](https://github.com/akka-samples/healthcare-agent), Level: Intermediate

Shopping cart microservice Shows a very simple microservice implementing a shopping cart with an event-sourced entity.

[Step-by-step guide](build-and-deploy-shopping-cart.html)

[Github Repository](https://github.com/akka-samples/shopping-cart-quickstart), Level: Beginner

A customer registry microservice Shows Entities and query capabilities with a View. [customer-registry-quickstart.zip](../java/_attachments/customer-registry-quickstart.zip), Level: Intermediate

Involve multiple Entities in a transaction through a Workflow A funds transfer workflow between two wallets.

[Github Repository](https://github.com/akka-samples/transfer-workflow-compensation), Level: Intermediate

A choreography saga for user registration A user registration service implemented as a Choreography Saga.

[Github Repository](https://github.com/akka-samples/choreography-saga-quickstart), Level: Advanced

Akka Chess a complete, resilient, automatically scalable, event-sourced chess game

[Github Repository](https://github.com/akka-samples/akka-chess), Level: Advanced

It is also possible to deploy a pre-built sample project in [the Akka console](https://console.akka.io/), eliminating the need for local development.
<!-- <footer> -->
<!-- <nav> -->
[AI Planner Part 6: Dynamic orchestration](planner-agent/dynamic-team.html) [Understanding](../concepts/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
<!-- <nav> -->
- [Akka](../index.html)
- [Tutorials](index.html)
- [Additional samples](samples.html)

<!-- </nav> -->

# Additional samples

|  | **New to Akka? Start here:**

Use the [Build your first agent](author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |
In addition to our getting started guides and SDK reference material, we also have many samples available that demonstrate important patterns and abstractions. Full sources for the samples can be downloaded with `akka code init` or cloned from their respective GitHub repositories. Please refer to the `README` file in each repository for setup and usage instructions.

It is also possible to deploy a pre-built sample project in [the Akka console](https://console.akka.io/), eliminating the need for local development.
| Sample | Orchestration | Agents | Memory | Streaming |
| [Hello World](about:blank#_hello_world) |  | ✅ | ✅ |  |
| [Hello World (Kotlin)](about:blank#_hello_world_kotlin) |  | ✅ | ✅ |  |
| [Multi-agent planning and execution](about:blank#_multi_agent_planning_and_execution) | ✅ | ✅ | ✅ | ✅ |
| [Chat agent with RAG](about:blank#_chat_agent_with_rag) |  | ✅ | ✅ | ✅ |
| [Travel planning agent](about:blank#_travel_planning_agent) |  | ✅ |  |  |
| [Medical discharge summary tagging](about:blank#_medical_discharge_summary_tagging) | ✅ | ✅ |  |  |
| [IoT sensor monitoring](about:blank#_iot_sensor_monitoring) |  | ✅ | ✅ | ✅ |
| [Release note summarizer](about:blank#_release_note_summarizer) | ✅ | ✅ |  |  |
| [Customer service chat workflow](about:blank#_customer_service_chat_workflow) | ✅ | ✅ | ✅ |  |
| [Trip booking with tools](about:blank#_trip_booking_with_tools) | ✅ | ✅ | ✅ | ✅ |
| [Multi-source health sensor analysis](about:blank#_multi_source_health_sensor_analysis) |  | ✅ | ✅ | ✅ |
| [Akka Chess](about:blank#_akka_chess) |  |  |  |  |
| [Shopping cart](about:blank#_shopping_cart) | ✅ |  |  |  |
| [Customer data store](about:blank#_customer_data_store) |  |  |  |  |
| [Wallet transfer](about:blank#_wallet_transfer) | ✅ |  |  |  |
| [Wallet transfer with failure compensation](about:blank#_wallet_transfer_with_failure_compensation) | ✅ |  |  |  |
| [Choreographed user signup flow](about:blank#_choreographed_user_signup_flow) |  |  |  |  |

## <a href="about:blank#_hello_world"></a> Hello World

This sample uses an agent and LLM to generate greetings in different languages. It illustrates how the agent maintains contextual history in a session memory.

**Concepts**: *Agent*, *LLM*
**Level**: *Beginner*
[Step-by-step guide](author-your-first-service.html)
[GitHub Repository](https://github.com/akka-samples/helloworld-agent)

## <a href="about:blank#_hello_world_kotlin"></a> Hello World (Kotlin)

This sample uses an agent and LLM to generate greetings in different languages. It illustrates how the agent maintains contextual history in a session memory. This sample is built with Kotlin

**Concepts**: *Agent*, *LLM*
**Level**: *Beginner*
[GitHub Repository](https://github.com/akka-samples/helloworld-agent-kotlin)

## <a href="about:blank#_multi_agent_planning_and_execution"></a> Multi-agent planning and execution

This sample models an outdoor activities planner. It demonstrates how to build a multi-agent system using Akka and an LLM model. A workflow manages the user query process, handling the sequential steps of agent selection, plan creation, execution, and summarization.

**Concepts**: *Agent*, *Workflow*, *Multi-agent*, *LLM*
**Level**: *Intermediate*
[Step-by-step guide](planner-agent/index.html)
[GitHub Repository](https://github.com/akka-samples/multi-agent)

## <a href="about:blank#_chat_agent_with_rag"></a> Chat agent with RAG

This sample illustrates how to use a batching workflow to create embeddings for vector databases, how to consume LLMs and maintain conversation history, use RAG to add knowledge to fixed LLMs, and expose it all as a streaming service. It uses MongoDB Atlas and OpenAI.

**Concepts**: *Agent*, *RAG*, *Vector database*
**Level**: *Intermediate*
[Step-by-step guide](ask-akka-agent/index.html)
[GitHub Repository](https://github.com/akka-samples/ask-akka-agent)

## <a href="about:blank#_travel_planning_agent"></a> Travel planning agent

This sample illustrates reliable interaction with an LLM using a workflow. Entities are used for durable state of user preferences and generated trips.

**Concepts**: *Agent*, *Agent Memory*, *Workflow*, *Entity*
**Level**: *Beginner*
[GitHub Repository](https://github.com/akka-samples/travel-agent)

## <a href="about:blank#_medical_discharge_summary_tagging"></a> Medical discharge summary tagging

This sample illustrate the use of LLMs and prompts to summarize activities. It assigns tags to the medical discharge summaries, while also enabling human verification and comparative analysis. Interactions are from a workflow with an agent using the OpenAI API with configurable model choice.

**Concepts**: *Agent*, *Summarization*, *Workflow*, *Entity*, *OpenAI*, *Human in the loop*
**Level**: *Intermediate*
[GitHub Repository](https://github.com/akka-samples/medical-tagging-agent)

## <a href="about:blank#_iot_sensor_monitoring"></a> IoT sensor monitoring

This sample is a temperature monitoring system that collects, aggregates, and analyzes temperature data from IoT sensors. The system uses AI to generate insights about temperature trends and anomalies across different locations. Collects and aggregates temperature data using Key Value Entities. OpenAI is used for anomaly and trend detection.

**Concepts**: *Agent*, *IoT*, *Trend analysis*, *Anomaly detection*, *Entity*, *OpenAI*
**Level**: *Intermediate*
[GitHub Repository](https://github.com/akka-samples/temperature-monitoring-agent)

## <a href="about:blank#_release_note_summarizer"></a> Release note summarizer

This sample is designed to run every time there is a release from configured GitHub repositories. It interacts with Anthropic Claude from an agent and uses tools to retrieve detailed information from GitHub. Entities are used for storing release summaries. A timed action looks for new releases periodically and creates the summary using the LLM.

**Concepts**: *Agent*, *Entity*, *Timed Action*, *Anthropic Claude*, *Tools*, *Summarization*
**Level**: *Intermediate*
[GitHub Repository](https://github.com/akka-samples/changelog-agent)

## <a href="about:blank#_customer_service_chat_workflow"></a> Customer service chat workflow

The real-estate customer service agent demonstrates how to combine Akka features with an LLM model. It illustrates an agentic workflow for customer service. It processes incoming real-estate inquiries, analyzes the content to extract details, provides follow-up when needed and saves the collected information for future reference.

**Concepts**: *Agent*, *Workflow*, *Analysis*, *Detail extraction*, *Human in the loop*, *Agent Memory*
**Level**: *Intermediate*
[GitHub Repository](https://github.com/akka-samples/real-estate-cs-agent)

## <a href="about:blank#_trip_booking_with_tools"></a> Trip booking with tools

This app represents a travel agency that searches for flights and accommodations. It’s composed by an LLM (Anthropic) using Spring AI and AI tools to find flights, accommodations and sending mails.

**Concepts**: *Agent*, *Tools*, *Anthropic*, *Spring AI*, *Workflow*
[GitHub Repository](https://github.com/akka-samples/trip-agent)

## <a href="about:blank#_multi_source_health_sensor_analysis"></a> Multi-source health sensor analysis

This sample illustrates an AI agent that uses an LLM and multiple tools to analyze data from fitness trackers, medical records and other sensors. It integrates with Fitbit and MongoDB Atlas.

**Concepts**: *Agent*, *Analysis*, *Integrations*, *Vector database*
**Level**: *Intermediate*
[GitHub Repository](https://github.com/akka-samples/healthcare-agent)

## <a href="about:blank#_shopping_cart"></a> Shopping cart

This sample illustrates the basics of building a shopping cart with entities, views, and other core Akka components. There is also a step-by-step guide that gradually adds features to a core cart.

**Concepts**: *Entity*, *Events*, *HTTP Endpoint*
**Level**: *Beginner*
[Step-by-step guide](shopping-cart/build-and-deploy-shopping-cart.html)
[GitHub Repository](https://github.com/akka-samples/shopping-cart-quickstart)

## <a href="about:blank#_customer_data_store"></a> Customer data store

This sample illustrates the use of entities and query capabilities with a view. It shows a simple set of traditional queries and the standard create, update, and delete mutations through events.

**Concepts**: *Entity*, *View*, *HTTP Endpoint*
**Level**: Intermediate
[GitHub Repository](https://github.com/akka-samples/event-sourced-customer-registry)

## <a href="about:blank#_wallet_transfer"></a> Wallet transfer

This example illustrates a funds transfer workflow between two wallets, where the workflow orchestrates the interaction with an external service to perform the transfer.

**Concepts**: *Transactions*, *Workflow*
**Level**: *Intermediate*
[GitHub Repository](https://github.com/akka-samples/transfer-workflow-orchestration)

## <a href="about:blank#_wallet_transfer_with_failure_compensation"></a> Wallet transfer with failure compensation

This example illustrates a funds transfer workflow between two wallets. It also illustrates how to set up workflow *compensation* in order to self-heal work when one or more steps in the workflow fail.

**Concepts**: *Transactions*, *Workflow*, *Entity*
**Level**: *Intermediate*
[GitHub Repository](https://github.com/akka-samples/transfer-workflow-compensation)

## <a href="about:blank#_choreographed_user_signup_flow"></a> Choreographed user signup flow

This example is a user registration service where the user’s new signup flow implemented as a choreography saga.

**Concepts**: *Choreography*, *Saga*, *Workflow*
**Level**: *Advanced*
[GitHub Repository](https://github.com/akka-samples/choreography-saga-quickstart)

## <a href="about:blank#_akka_chess"></a> Akka Chess

This example represents a complete, resilient, automatically scalable, event-sourced chess game. We will be adding a guided tour that will include adding an agent to act as a non-player "brain" that plays chess in real time against human players.

**Concepts**: *Embedded UI*, *Entity*, *Workflow*, *View*, *Timed Action*
**Level**: *Advanced*
[GitHub Repository](https://github.com/akka-samples/akka-chess)

<!-- <footer> -->
<!-- <nav> -->
[Authenticated user-specific lookup](shopping-cart/addview.html) [Understanding](../concepts/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
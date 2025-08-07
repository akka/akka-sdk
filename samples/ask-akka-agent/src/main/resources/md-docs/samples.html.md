<!-- <nav> -->
- [Akka](../index.html)
- [Getting Started](index.html)
- [Additional Samples](samples.html)

<!-- </nav> -->

# Additional Samples

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |
Samples are available that demonstrate important patterns and abstractions. Full sources for the samples can be downloaded with `akka code init` or cloned from their respective GitHub repositories. Please refer to the `README` file in each repository for setup and usage instructions.

It is also possible to deploy a pre-built sample project in [the Akka console](https://console.akka.io/), eliminating the need for local development.
## <a href="about:blank#_multi_agent_ai_activity_suggestion_system"></a> Multi-agent AI activity suggestion system

This sample demonstrates how to build a multi-agent system using Akka and an LLM model. A workflow manages the user query process, handling the sequential steps of agent selection, plan creation, execution, and summarization.

**Concepts**: *Agent*, *Workflow*, *Multi-agent*, *LLM*
**Level**: *Intermediate*
[Step-by-step guide](planner-agent/index.html)
[Github Repository](https://github.com/akka-samples/multi-agent)

## <a href="about:blank#_rag_workflow_ai_agent"></a> RAG workflow AI agent

This sample illustrates how to create embeddings for vector databases, how to consume LLMs and maintain conversation history, use RAG to add knowledge to fixed LLMs, and expose it all as a streaming service. It uses MongoDB Atlas and OpenAI.

**Concepts**: *Agent*, *RAG*, *Vector database*
**Level**: *Intermediate*
[Step-by-step guide](ask-akka-agent/index.html)
[Github Repository](https://github.com/akka-samples/ask-akka-agent)

## <a href="about:blank#_personalized_travel_itinerary_creation_agent"></a> Personalized travel itinerary creation agent

This sample illustrates reliable interaction with an LLM using a workflow. Entities are used for durable state of user preferences and generated trips.

**Concepts**: *Agent*, *Agent Memory*, *Workflow*, *Entity*
**Level**: *Beginner*
[Github Repository](https://github.com/akka-samples/travel-agent)

## <a href="about:blank#_medical_discharge_summarizing_agent"></a> Medical discharge summarizing agent

This sample illustrate the use of LLMs and prompts to summarize activities. It assigns tags to the medical discharge summaries, while also enabling human verification and comparative analysis. Interactions are from a workflow with an agent using the OpenAI API with configurable model choice.

**Concepts**: *Agent*, *Summarization*, *Workflow*, *Entity*, *OpenAI*, *Human in the loop*
**Level**: *Intermediate*
[Github Repository](https://github.com/akka-samples/medical-tagging-agent)

## <a href="about:blank#_iot_sensor_monitoring_agent"></a> IoT sensor monitoring agent

This sample is a temperature monitoring system that collects, aggregates, and analyzes temperature data from IoT sensors. The system uses AI to generate insights about temperature trends and anomalies across different locations. Collects and aggregates temperature data using Key Value Entities. OpenAI is used for anomaly and trend detection.

**Concepts**: *Agent*, *IoT*, *Trend analysis*, *Anomaly detection*, *Entity*, *OpenAI*
**Level**: *Intermediate*
[Github Repository](https://github.com/akka-samples/temperature-monitoring-agent)

## <a href="about:blank#_release_note_summary_generation_agent"></a> Release note summary generation agent

This sample is designed to run every time there is a release from configured GitHub repositories. It interacts with Anthropic Claude from an agent and uses tools to retrieve detailed information from GitHub. Entities are used for storing release summaries. A timed action looks for new releases periodically and creates the summary using the LLM.

**Concepts**: *Agent*, *Entity*, *Timed Action*, *Anthropic Claude*, *Tools*, *Summarization*
**Level**: *Intermediate*
[Github Repository](https://github.com/akka-samples/changelog-agent)

## <a href="about:blank#_agentic_customer_service_workflow"></a> Agentic customer service workflow

The real-estate customer service agent demonstrates how to combine Akka features with an LLM model. It illustrates an agentic workflow for customer service. It processes incoming real-estate inquiries, analyzes the content to extract details, provides follow-up when needed and saves the collected information for future reference.

**Concepts**: *Agent*, *Workflow*, *Analysis*, *Detail extraction*, *Human in the loop*, *Agent Memory*
**Level**: *Intermediate*
[Github Repository](https://github.com/akka-samples/real-estate-cs-agent)

## <a href="about:blank#_tool_using_trip_booking_agent"></a> Tool using trip booking agent

This app represents a travel agency that searches for flights and accommodations. It’s composed by an LLM (Anthropic) using Spring AI and AI tools to find flights, accommodations and sending mails.

**Concepts**: *Agent*, *Tools*, *Anthropic*, *Spring AI*, *Workflow*
[Github Repository](https://github.com/akka-samples/trip-agent)

## <a href="about:blank#_agent_sensor_data_analysis"></a> Agent sensor data analysis

This sample illustrates an AI agent that uses an LLM to analyze data from fitness trackers, medical records and other sensors. It integrates with Fitbit and MongoDB Atlas.

**Concepts**: *Agent*, *Analysis*, *Integrations*, *Vector database*
**Level**: *Intermediate*
[Github Repository](https://github.com/akka-samples/healthcare-agent)

## <a href="about:blank#_shopping_cart_microservice"></a> Shopping cart microservice

This sample shows a very simple microservice implementing a shopping cart with an event-sourced entity.

**Concepts**: *Entity*, *Events*, *HTTP Endpoint*
**Level**: *Beginner*
[Step-by-step guide](build-and-deploy-shopping-cart.html)
[Github Repository](https://github.com/akka-samples/shopping-cart-quickstart)

## <a href="about:blank#_customer_registry_microservice"></a> Customer registry microservice

This sample illustrates the use of entities and query capabilities with a view. This example shows a simple set of traditional queries and data mutations through events.

**Concepts**: *Entity*, *View*, *HTTP Endpoint*
**Level**: Intermediate
[customer-registry-quickstart.zip](../java/_attachments/customer-registry-quickstart.zip)

## <a href="about:blank#_external_service_orchestration_with_a_workflow"></a> External service orchestration with a Workflow

This example illustrates a funds transfer workflow between two wallets, where the workflow orchestrates the interaction with an external service to perform the transfer.

**Concepts**: *Transactions*, *Workflow*
**Level**: *Intermediate*
[Github Repository](https://github.com/akka-samples/transfer-workflow-orchestration)

## <a href="about:blank#_multiple_entity_orchestration_with_a_workflow"></a> Multiple Entity orchestration with a Workflow

This example illustrates a funds transfer workflow between two wallets.

**Concepts**: *Transactions*, *Workflow*, *Entity*
**Level**: *Intermediate*
[Github Repository](https://github.com/akka-samples/transfer-workflow-compensation)

## <a href="about:blank#_user_registration_with_a_choreography_saga"></a> User registration with a choreography saga

This example is a user registration service implemented as a choreography saga.

**Concepts**: *Choreography*, *Saga*, *Workflow*
**Level**: *Advanced*
[Github Repository](https://github.com/akka-samples/choreography-saga-quickstart)

## <a href="about:blank#_akka_chess"></a> Akka Chess

This example represents a complete, resilient, automatically scalable, event-sourced chess game.

**Concepts**: *Embedded UI*, *Entity*, *Workflow*, *View*, *Timed Action*
**Level**: *Advanced*
[Github Repository](https://github.com/akka-samples/akka-chess)

<!-- <footer> -->
<!-- <nav> -->
[Evaluation on changes](planner-agent/eval.html) [Understanding](../concepts/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
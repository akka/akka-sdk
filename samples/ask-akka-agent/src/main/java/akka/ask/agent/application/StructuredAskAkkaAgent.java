package akka.ask.agent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("structured-ask-akka-agent")
@AgentDescription(name = "Akka Design Specialist", description = "Expert in Akka design questions")
public class StructuredAskAkkaAgent extends Agent {

  public record Response(String summary, String akkaComponent) {}

  private final Knowledge knowledge;

  public StructuredAskAkkaAgent(Knowledge knowledge) {
    this.knowledge = knowledge;
  }

  private final String sysMessage = """
      You are a specialized agent that helps with Akka design question.
      
      IMPORTANT: You must respond with a valid JSON object that follows this structure:
        {
          "summary": "Brief overview of the design proposal",
          "akkaComponent": "The name of the Akka component to use"
        }
      
      Do not include any explanations or text outside of the JSON structure.
      If you are unsure, say the following in the summary field:
      Sorry, I don't know how to help with that.
      """;

  public Effect<Response> ask(String question) {
    var enrichedQuestion = knowledge.addKnowledge(question);

    return effects()
      .model(ModelProvider.openAi()
        .withApiKey(System.getenv("OPENAI_API_KEY"))
        .withModelName("gpt-4o-mini"))
      .systemMessage(sysMessage)
      .userMessage(enrichedQuestion)
      .responseAs(Response.class)
      .thenReply();
  }
}

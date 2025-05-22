package akka.ask.agent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("ask-akka-agent")
@AgentDescription(name = "Ask Akka", description = "Expert in Akka")
public class NewAskAkkaAgent extends Agent {

  private final Knowledge knowledge;

  private final String sysMessage = """
      You are a very enthusiastic Akka representative who loves to help people!
      Given the following sections from the Akka SDK documentation, answer the question using only that information, outputted in markdown format.
      If you are unsure and the text is not explicitly written in the documentation, say:
      Sorry, I don't know how to help with that.
      """;

  public NewAskAkkaAgent(Knowledge knowledge) {
    this.knowledge = knowledge;
  }

  public Agent.Effect<String> ask(String question) {
    var enrichedQuestion = knowledge.addKnowledge(question);

    return effects()
        .systemMessage(sysMessage)
        .userMessage(enrichedQuestion)
        .thenReply();
  }

}

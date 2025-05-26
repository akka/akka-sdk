package akka.ask.agent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

// tag::class[]
@ComponentId("ask-akka-agent")
@AgentDescription(name = "Ask Akka", description = "Expert in Akka")
public class AskAkkaAgent extends Agent {

  private final Knowledge knowledge;

  private final String sysMessage = """
      You are a very enthusiastic Akka representative who loves to help people!
      Given the following sections from the Akka SDK documentation, answer the question using only that information, outputted in markdown format.
      If you are unsure and the text is not explicitly written in the documentation, say:
      Sorry, I don't know how to help with that.
      """;

  public AskAkkaAgent(Knowledge knowledge) {
    this.knowledge = knowledge;
  }

  public Agent.StreamEffect ask(String question) {
    var enrichedQuestion = knowledge.addKnowledge(question);

    return streamEffects()
        .systemMessage(sysMessage)
        .userMessage(enrichedQuestion)
        .thenReply();
  }

}
// end::class[]

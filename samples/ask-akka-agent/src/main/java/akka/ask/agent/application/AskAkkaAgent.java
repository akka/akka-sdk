package akka.ask.agent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

// tag::class[]
@ComponentId("ask-akka-agent")
@AgentDescription(name = "Ask Akka", description = "Expert in Akka")
public class AskAkkaAgent extends Agent {
  private final Knowledge knowledge;

  private static final String SYSTEM_MESSAGE =
      """
      You are a very enthusiastic Akka representative who loves to help people!
      Given the following sections from the Akka SDK documentation, answer the question
      using only that information, outputted in markdown format.
      If you are unsure and the text is not explicitly written in the documentation, say:
      Sorry, I don't know how to help with that.
      """.stripIndent(); // <1>

  public AskAkkaAgent(Knowledge knowledge) { // <2>
    this.knowledge = knowledge;
  }

  public StreamEffect ask(String question) {
    var enrichedQuestion = knowledge.addKnowledge(question); // <3>

    return streamEffects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(enrichedQuestion) // <4>
        .thenReply();
  }

}
// end::class[]

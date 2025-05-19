package akka.ask.agent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("ask-akka-agent")
@AgentDescription(name = "Ask Akka", description = "Expert in Akka")
public class NewAskAkkaAgent extends Agent {

  private final String sysMessage = """
      You are a very enthusiastic Akka representative who loves to help people!
      Given the following sections from the Akka SDK documentation, answer the question using only that information, outputted in markdown format.
      If you are unsure and the text is not explicitly written in the documentation, say:
      Sorry, I don't know how to help with that.
      """; // <1>

  public Agent.Effect<String> ask(String question) {
    return effects()
        .modelProvider(ModelProvider.OPEN_AI)
        .modelApiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .systemMessage(sysMessage)
        .userMessage(question)
        .thenReply();
  }
}

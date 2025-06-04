package demo.multiagent.application.agents;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import demo.multiagent.domain.AgentResponse;

import java.util.Collection;
import java.util.stream.Collectors;

@ComponentId("summarizer-agent")
@AgentDescription(
    name = "Summarizer",
    description = "An agent that creates a summary from responses provided by other agents")
public class Summarizer extends Agent {
  public record Request(String originalQuery, Collection<AgentResponse> agentsResponses) {}

  private String buildSystemMessage(String userQuery) {
    return  """
        You will receive the original query and a message generate by different other agents.
      
        Your task is to build a new message using the message provided by the other agents.
        You are not allowed to add any new information, you should only re-phrase it to make
        them part of coherent message.
      
        The message to summarize will be provided between single quotes.
      
        ORIGINAL USER QUERY:
        %s
      """.formatted(userQuery);
  }

  public Agent.Effect<String> summarize(Request request) {
    var allResponses = request.agentsResponses.stream()
        .map(AgentResponse::response)
        .filter(response -> response != null && !response.isEmpty())
        .collect(Collectors.joining(" "));

    return effects()
        .systemMessage(buildSystemMessage(request.originalQuery))
        .userMessage("Summarize the following message: '" + allResponses + "'")
        .thenReply();
  }

}

package patterns;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "traffic-agent")
public class TrafficAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You are ..
    """.stripIndent();

  public Effect<String> query(String message) {
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(message).thenReply();
  }
}

package agent_guide.part1;

// tag::all[]
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "activity-agent") // <1>
public class ActivityAgent extends Agent { // <2>

  private static final Logger logger = LoggerFactory.getLogger(ActivityAgent.class);

  private static final String SYSTEM_MESSAGE =
    """
    You are an activity agent. Your job is to suggest activities in the
    real world. Like for example, a team building activity, sports, an
    indoor or outdoor game, board games, a city trip, etc.
    """.stripIndent();

  public Effect<String> query(String message) { // <3>
    logger.info("Invoked with: {}", message);
    return effects()
      .systemMessage(SYSTEM_MESSAGE) // <4>
      .userMessage(message) // <5>
      .thenReply();
  }
}
// end::all[]

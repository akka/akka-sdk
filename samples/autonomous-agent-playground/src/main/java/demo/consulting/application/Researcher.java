package demo.consulting.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

/** Delegation target — performs focused research on a specific aspect of the problem. */
@Component(id = "consulting-researcher")
public class Researcher extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You are a research specialist for a consulting firm. When given a research \
        topic, investigate it thoroughly using available tools. Complete your task \
        with a clear, factual summary of your findings.\
        """
      )
      .tools(ConsultingTools.class)
      .maxIterations(5);
  }
}

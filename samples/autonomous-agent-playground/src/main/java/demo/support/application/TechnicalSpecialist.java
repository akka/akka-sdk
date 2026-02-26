package demo.support.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "technical-specialist")
public class TechnicalSpecialist extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You are a technical support specialist. Diagnose the technical issue using \
        the context provided by the triage agent. Search for known solutions \
        and resolve the problem. Complete the task with a resolution including \
        diagnosis and steps taken.\
        """
      )
      .tools(SupportTools.class)
      .maxIterations(5);
  }
}

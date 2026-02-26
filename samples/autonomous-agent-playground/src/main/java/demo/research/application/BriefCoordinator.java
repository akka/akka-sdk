package demo.research.application;

import static akka.javasdk.agent.autonomous.AutonomousAgent.agent;
import static akka.javasdk.agent.autonomous.AutonomousAgent.delegation;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "brief-coordinator")
public class BriefCoordinator extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You coordinate research briefs. When given a topic, delegate research and \
        analysis to specialist agents. Once you receive their results, synthesise \
        the findings and complete the task with the result.\
        """
      )
      .capability(
        delegation(
          agent(Researcher.class, "Research a topic and find key facts"),
          agent(Analyst.class, "Analyse a topic and produce insights")
        )
      )
      .maxIterations(10);
  }
}

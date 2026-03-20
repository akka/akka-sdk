package demo.devteam.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "project-lead", description = "Leads software projects by coordinating a team")
public class ProjectLead extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Deliver completed software projects with all features implemented and tested.")
        .capabilities(
            canAcceptTasks(ProjectTasks.PLAN).maxIterationsPerTask(40),
            canLeadTeam(teamMember(Developer.class).maxInstances(3)));
  }
}

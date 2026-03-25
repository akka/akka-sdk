package demo.devteam.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.agent.autonomous.capability.TeamLeadership;
import akka.javasdk.agent.autonomous.capability.TeamLeadership.TeamMember;
import akka.javasdk.annotations.Component;

@Component(
  id = "project-lead",
  description = "Leads software projects by coordinating a team"
)
public class ProjectLead extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal("Deliver completed software projects with all features implemented and tested.")
      .capability(TaskAcceptance.of(ProjectTasks.PLAN).maxIterationsPerTask(40))
      .capability(TeamLeadership.of(TeamMember.of(Developer.class).maxInstances(3)));
  }
}

package demo.devteam.application;

import static akka.javasdk.agent.autonomous.AutonomousAgent.agent;
import static akka.javasdk.agent.autonomous.AutonomousAgent.member;
import static akka.javasdk.agent.autonomous.AutonomousAgent.team;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.devteam.application.ProjectTasks;

@Component(id = "team-lead")
public class TeamLead extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(ProjectTasks.PLAN)
      .instructions(
        """
        You lead a development team. Your workflow spans multiple iterations — \
        do NOT try to do everything at once.

        ITERATION 1 — Setup:
        1. Create a team and add developers (up to 3).
        2. Break the project into tasks using addTask with agentType="developer" \
        and templateParams for feature and requirements.
        3. STOP here. The developers need time to work.

        ITERATIONS 2+ — Monitor:
        4. Check getTeamStatus to see task progress.
        5. If tasks are still in progress, STOP and wait for the next iteration.

        WHEN ALL TASKS COMPLETE:
        6. Compile results into a summary ProjectResult.
        7. Disband the team, then complete your task.

        IMPORTANT: Each iteration is separated by a pause that gives developers \
        time to work. Do not poll status repeatedly in the same iteration.\
        """
      )
      .capability(
        team(member(agent(Developer.class, "A developer who can write and test code")).max(3))
      )
      .maxIterations(40);
  }
}

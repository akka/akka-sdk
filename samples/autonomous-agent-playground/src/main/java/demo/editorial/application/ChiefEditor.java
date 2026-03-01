package demo.editorial.application;

import static akka.javasdk.agent.autonomous.AutonomousAgent.agent;
import static akka.javasdk.agent.autonomous.AutonomousAgent.externalInput;
import static akka.javasdk.agent.autonomous.AutonomousAgent.member;
import static akka.javasdk.agent.autonomous.AutonomousAgent.team;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.editorial.application.EditorialTasks;

/**
 * Chief editor — demonstrates team + external input.
 *
 * <p>Forms a team of writers, assigns sections, compiles the output, then requests editorial
 * approval (via requestDecision) before finalising the publication.
 */
@Component(id = "chief-editor")
public class ChiefEditor extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(EditorialTasks.PUBLICATION)
      .instructions(
        """
        You are the chief editor of a publication. Your workflow spans multiple \
        iterations — do NOT try to do everything at once.

        ITERATION 1 — Setup:
        1. Create a team and add 2 writers.
        2. Add writing tasks to the shared task list — one per section \
           (e.g. "Write the introduction about X", "Write the analysis section about X").
        3. STOP here. The writers need time to work.

        ITERATIONS 2+ — Monitor:
        4. Check getTeamStatus to see if writing tasks have been completed.
        5. If tasks are still in progress, STOP and wait for the next iteration.

        WHEN ALL TASKS COMPLETE:
        6. Compile the writers' work into a cohesive publication.
        7. Use requestDecision to ask for editorial approval before publishing. \
           Include a summary of the compiled content in your request.
        8. If approved, disband the team, then complete your task with the Publication. \
           If rejected, revise based on feedback and request approval again.

        IMPORTANT: Each iteration is separated by a pause that gives writers \
        time to work. Do not poll status repeatedly in the same iteration.\
        """
      )
      .tools(EditorialTools.class)
      .capability(
        team(
          member(
            agent(Writer.class, "A writer who researches and writes content sections")
          ).max(2)
        )
      )
      .capability(externalInput())
      .maxIterations(30);
  }
}

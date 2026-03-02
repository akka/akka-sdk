package demo.debate.application;

import static akka.javasdk.agent.autonomous.AutonomousAgent.agent;
import static akka.javasdk.agent.autonomous.AutonomousAgent.member;
import static akka.javasdk.agent.autonomous.AutonomousAgent.team;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.debate.application.DebateTasks;

/**
 * Debate moderator — demonstrates team with messaging-driven collaboration.
 *
 * <p>Forms a team of debaters and assigns positions. Unlike the devteam sample where agents work
 * independently from a task list, here the messaging IS the work — debaters actively challenge each
 * other via sendMessage.
 */
@Component(id = "debate-moderator")
public class DebateModerator extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(DebateTasks.DEBATE)
      .instructions(
        """
        You moderate a structured debate. Your workflow spans multiple iterations — \
        do NOT try to do everything at once.

        ITERATION 1 — Setup:
        1. Create a team and add 2 debaters.
        2. Add exactly 2 tasks to the shared task list — one "Argue FOR: [topic]" \
           and one "Argue AGAINST: [topic]". Be specific about the position.
        3. Send each debater a message assigning them their position.
        4. STOP here. Do not check status yet — the debaters need time to work.

        ITERATIONS 2+ — Monitor:
        5. Check getTeamStatus to see if tasks have been completed.
        6. If tasks are still in progress, STOP and wait for the next iteration. \
           Do NOT send repeated messages asking for updates.
        7. When both tasks are completed, read any messages from the debaters.

        FINAL — Synthesise:
        8. Compile the debaters' arguments into a balanced DebateResult — \
           capturing the strongest arguments from each side.
        9. Disband the team, then complete your task with the synthesis.

        IMPORTANT: Each iteration is separated by a pause that gives debaters \
        time to work. Do not poll status repeatedly in the same iteration.\
        """
      )
      .tools(DebateTools.class)
      .capability(
        team(
          member(
            agent(
              Debater.class,
              "A debater who argues a position and engages peers via messaging"
            )
          ).max(2)
        )
      )
      .maxIterations(30);
  }
}

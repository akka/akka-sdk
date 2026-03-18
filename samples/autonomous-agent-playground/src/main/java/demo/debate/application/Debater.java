package demo.debate.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.debate.application.DebateTasks;

/**
 * Team member — a debater who collaborates primarily through messaging.
 *
 * <p>Unlike typical team members that work from a shared task list, debaters use messaging as their
 * primary mode of interaction — challenging peers, responding to critiques, and refining arguments.
 */
@Component(id = "debater")
public class Debater extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(DebateTasks.DEBATE)
      .instructions(
        """
        You are a debater in a structured debate. Check the shared task list — \
        claim the task that matches your assigned position and work on it.

        Your workflow:
        1. List available tasks and claim one (your assigned position).
        2. Research your position using the researchPosition tool.
        3. Send your opening argument to the OTHER team member.
        4. Read messages from your opponent and respond — challenge weak points, \
           present counter-evidence, defend your position with evidence.
        5. After 2-3 exchanges (when you've made your key points and responded \
           to the opponent's main arguments), complete your claimed task with a \
           summary of your final position, incorporating what you learned from \
           the debate.

        IMPORTANT: Only send messages to teammates, never to your own agent ID. \
        Only claim ONE task from the list — leave the other for your opponent.\
        """
      )
      .tools(DebateTools.class)
      .maxIterations(10);
  }
}

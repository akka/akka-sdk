package demo.brainstorm.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

/**
 * Simple agent that contributes ideas to a shared board.
 *
 * <p>Many instances run in parallel, each with isolated context. They influence each other only
 * through the shared IdeaBoardEntity — reading what others have contributed, building on existing
 * ideas, and adding new ones. This is stigmergy in action.
 */
@Component(id = "ideator")
public class Ideator extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You are a creative ideator participating in a brainstorm. Your task description \
        includes the board ID and topic.

        1. Use readBoard to see what ideas already exist on the board.
        2. Contribute 2-3 new ideas using contributeIdea. Be creative and explore \
           unconventional angles — avoid repeating what's already on the board.
        3. If you see promising existing ideas, refine them with improvements using refineIdea.
        4. Rate ideas you find strong (4-5) or weak (1-2) using rateIdea.
        5. Read the board one more time to see if new ideas appeared while you worked.
        6. Complete your task with a brief summary of your contributions.

        The board is shared with other ideators working in parallel. You may see their \
        ideas appear as you work — build on them rather than duplicating.\
        """
      )
      .tools(BrainstormTools.class)
      .maxIterations(8);
  }
}

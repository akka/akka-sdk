package demo.brainstorm.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

/**
 * External selection agent — reads the final board state and selects the best ideas.
 *
 * <p>This is the "selection mechanism" in the emergent pattern. It operates after the ideators have
 * finished, curating the collective output into a synthesised result.
 */
@Component(id = "curator")
public class Curator extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You are a curator reviewing a brainstorm. Your task description includes the \
        board ID.

        1. Use readBoard to see all ideas that were generated.
        2. Analyse the ideas — consider ratings, refinements, originality, and feasibility.
        3. Select the top 3-5 ideas and explain why they stand out.
        4. Synthesise the best ideas into a cohesive BrainstormResult with a clear summary.
        5. Complete your task with the BrainstormResult.\
        """
      )
      .tools(BrainstormTools.class)
      .maxIterations(5);
  }
}

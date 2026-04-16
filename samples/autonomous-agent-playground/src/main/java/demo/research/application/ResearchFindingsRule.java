package demo.research.application;

import akka.javasdk.agent.task.TaskRule;

/** Validates that research findings include at least one cited source. */
public class ResearchFindingsRule implements TaskRule<ResearchFindings> {

  @Override
  public Result onComplete(ResearchFindings findings) {
    if (findings.sources() == null || findings.sources().isEmpty()) {
      return new Result.Rejected(
        "sources must not be empty — research findings must cite sources"
      );
    }
    return new Result.Accepted();
  }
}

package demo.research.application;

import akka.javasdk.agent.task.Task;

public class ResearchTasks {

  // prettier-ignore
  public static final Task<ResearchBrief> BRIEF = Task
    .define("Brief")
    .description("Produce a research brief on a given topic")
    .resultConformsTo(ResearchBrief.class);

  // prettier-ignore
  public static final Task<ResearchFindings> FINDINGS = Task
    .define("Findings")
    .description("Research a topic and produce factual findings")
    .resultConformsTo(ResearchFindings.class);

  // prettier-ignore
  public static final Task<AnalysisReport> ANALYSIS = Task
    .define("Analysis")
    .description("Analyse a topic and produce a trend analysis report")
    .resultConformsTo(AnalysisReport.class);
}

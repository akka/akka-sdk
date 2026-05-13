package demo.research.application;

import akka.javasdk.agent.task.Task;

// tag::class[]
public class ResearchTasks {

  // prettier-ignore
  public static final Task<ResearchBrief> BRIEF = Task
    .name("Brief")
    .description("Produce a research brief on a given topic")
    .resultConformsTo(ResearchBrief.class);

  // tag::with-rule[]
  // prettier-ignore
  public static final Task<ResearchFindings> FINDINGS = Task
    .name("Findings")
    .description("Research a topic and produce factual findings")
    .resultConformsTo(ResearchFindings.class)
    .rules(ResearchFindingsRule.class);
  // end::with-rule[]

  // prettier-ignore
  public static final Task<AnalysisReport> ANALYSIS = Task
    .name("Analysis")
    .description("Analyse a topic and produce a trend analysis report")
    .resultConformsTo(AnalysisReport.class);
}
// end::class[]

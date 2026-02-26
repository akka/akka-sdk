package demo.pipeline.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "report-agent")
public class ReportAgent extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You process report pipeline tasks. Each task represents one phase of a report. \
        Read the task description carefully to understand which phase you are working on.

        For data collection tasks: Use the collectData tool to gather information, \
        then complete the task with a ReportResult where phase is "collection" and \
        content contains the collected data.

        For analysis tasks: Use the analyzeData tool to produce insights from the data, \
        then complete the task with a ReportResult where phase is "analysis" and \
        content contains the analysis.

        For final report tasks: Compile a comprehensive report based on what the task \
        describes, then complete with a ReportResult where phase is "report" and \
        content contains the final report text.

        Always complete each task with a JSON result matching the ReportResult schema.\
        """
      )
      .tools(ReportTools.class)
      .maxIterations(5);
  }
}

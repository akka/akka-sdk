package demo.pipeline.application;

import akka.javasdk.annotations.FunctionTool;

/** Tools for the report pipeline phases. */
public class ReportTools {

  @FunctionTool(description = "Collect data on a given topic. Returns raw data for analysis.")
  public String collectData(String topic) {
    return (
      "Collected data for '" +
      topic +
      "': " +
      "Market size: $4.2B (2025), projected $12.8B by 2028. " +
      "Key players: AlphaCorp (32% share), BetaInc (21%), GammaTech (15%). " +
      "Growth drivers: automation demand, cost reduction, regulatory compliance. " +
      "Risks: talent shortage, integration complexity, data privacy concerns."
    );
  }

  @FunctionTool(
    description = "Analyze collected data and produce insights. Takes raw data as input, returns analysis."
  )
  public String analyzeData(String data) {
    return (
      "Analysis of data: " +
      "The market shows strong growth trajectory with a CAGR of 45%. " +
      "AlphaCorp's dominant position is threatened by BetaInc's rapid innovation. " +
      "The automation demand driver aligns with broader industry trends. " +
      "Recommendation: focus on compliance-ready solutions to capture regulated markets."
    );
  }
}

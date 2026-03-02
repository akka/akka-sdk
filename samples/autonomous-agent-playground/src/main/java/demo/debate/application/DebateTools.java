package demo.debate.application;

import akka.javasdk.annotations.FunctionTool;

/** Tools for the debate sample. */
public class DebateTools {

  @FunctionTool(description = "Research a position on a topic to find supporting evidence.")
  public String researchPosition(String topic, String stance) {
    return (
      "Research for '" +
      stance +
      "' position on '" +
      topic +
      "': " +
      "Found 3 supporting studies and 2 counter-arguments. " +
      "Key evidence: empirical data from 2024-2025 shows measurable impact. " +
      "Expert opinions are divided but lean towards this position. " +
      "Historical precedent from similar domains supports this stance."
    );
  }

  @FunctionTool(description = "Evaluate the strength of an argument.")
  public String evaluateArgument(String argument) {
    return (
      "Evaluation of argument: Logical coherence: strong. Evidence support: moderate — relies" +
      " on recent data that may need longer validation. Counter-argument vulnerability:" +
      " the main weakness is the assumption of stable conditions. Overall strength:" +
      " 7/10."
    );
  }
}

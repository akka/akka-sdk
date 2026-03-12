package demo.consulting.application;

import akka.javasdk.annotations.FunctionTool;

public class ConsultingTools {

  @FunctionTool(description = "Perform a preliminary assessment of a client problem. Returns a text assessment of the problem's nature and scope.")
  public String assessProblem(String problemDescription) {
    return "Preliminary assessment for '" + problemDescription + "': " +
        "This problem involves " + problemDescription.toLowerCase() + ". " +
        "Key areas to evaluate: scope, stakeholder impact, and timeline.";
  }

  @FunctionTool(description = "Check if a problem exceeds standard consulting scope and needs escalation to senior expertise. Returns COMPLEX or STANDARD classification.")
  public String checkComplexity(String assessment) {
    var lower = assessment.toLowerCase();
    if (lower.contains("regulatory") || lower.contains("compliance") ||
        lower.contains("m&a") || lower.contains("merger") || lower.contains("acquisition")) {
      return "COMPLEX: Recommend escalation to senior consultant. " +
          "This problem involves high-stakes regulatory or strategic concerns requiring senior expertise.";
    }
    return "STANDARD: Can be handled with research and analysis. " +
        "This problem is within standard consulting scope.";
  }
}

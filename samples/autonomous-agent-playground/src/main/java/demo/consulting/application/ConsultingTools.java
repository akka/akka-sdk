package demo.consulting.application;

import akka.javasdk.annotations.FunctionTool;

/** Domain tools for the consulting sample. */
public class ConsultingTools {

  @FunctionTool(description = "Perform a preliminary assessment of a client problem.")
  public String assessProblem(String problemDescription) {
    return (
      "Preliminary assessment for '" +
      problemDescription +
      "': " +
      "Complexity: moderate. Involves integration challenges and process redesign. " +
      "Estimated scope: 3-6 months. Key risks: legacy system dependencies, " +
      "change management resistance."
    );
  }

  @FunctionTool(
    description = "Check if a problem exceeds standard consulting scope and needs escalation."
  )
  public String checkComplexity(String assessment) {
    // Simulate: problems mentioning "regulatory" or "merger" are too complex
    if (
      assessment.toLowerCase().contains("regulatory") ||
      assessment.toLowerCase().contains("merger")
    ) {
      return (
        "COMPLEX: This problem involves regulatory or M&A considerations " +
        "that exceed standard consulting scope. Recommend escalation to senior consultant."
      );
    }
    return (
      "STANDARD: This problem is within standard consulting scope. " +
      "Can be handled with research and analysis."
    );
  }
}

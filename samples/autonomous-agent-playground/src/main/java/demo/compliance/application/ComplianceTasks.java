package demo.compliance.application;

import akka.javasdk.agent.task.Task;

public class ComplianceTasks {

  public static final Task<ComplianceReport> REVIEW = Task.of(
    "Compliance review",
    ComplianceReport.class
  );
}

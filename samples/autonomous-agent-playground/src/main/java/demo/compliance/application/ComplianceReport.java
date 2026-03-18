package demo.compliance.application;

public record ComplianceReport(
  String riskLevel,
  String findings,
  String decision,
  boolean approvedByOfficer
) {}

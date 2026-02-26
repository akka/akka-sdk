package demo.support.application;

import akka.javasdk.annotations.FunctionTool;

/** Domain tools for support agents. */
public class SupportTools {

  @FunctionTool(description = "Look up a customer by ID and return their account information")
  public String lookupCustomer(String customerId) {
    return (
      "Customer " +
      customerId +
      ": Premium plan, active since 2024, billing email: customer@example.com"
    );
  }

  @FunctionTool(description = "Search the knowledge base for solutions to a support issue")
  public String searchKnowledge(String query) {
    return (
      "Knowledge base results for '" +
      query +
      "': " +
      "1. Check billing settings in account dashboard. " +
      "2. Contact billing team for invoice disputes. " +
      "3. Technical issues should be escalated to engineering."
    );
  }
}

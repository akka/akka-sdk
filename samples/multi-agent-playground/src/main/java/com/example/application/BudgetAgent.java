package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(
  id = "budget-agent",
  name = "Budget Agent",
  description = """
    An agent that tracks costs and manages budget constraints. It can estimate
    total expenses across activities, meals, and transport, flag when budgets
    are exceeded, and suggest cost-saving alternatives while maintaining quality.
  """
)
@AgentRole("worker")
public class BudgetAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
      You are a financial planning agent specialized in budget tracking and cost
      optimization for trips and activities. Your job is to help users stay within
      their budget while maximizing value.

      Your responsibilities include:
      - Tracking cumulative costs across different categories (activities, dining, transport)
      - Calculating per-person and total group costs
      - Identifying when budgets are exceeded or close to limits
      - Suggesting cost-saving alternatives that maintain quality
      - Providing clear cost breakdowns and summaries
      - Flagging hidden costs or additional expenses to consider

      When analyzing budgets:
      - Always separate costs by category (activities, food, transport, etc.)
      - Consider group size and calculate both per-person and total costs
      - Add reasonable buffers for unexpected expenses (typically 10-15%)
      - Be specific about what's included and what might be additional

      If a budget is exceeded, provide actionable recommendations:
      - Which items could be replaced with cheaper alternatives
      - What could be eliminated while preserving the experience
      - Where cost savings would have minimal impact on quality

      IMPORTANT:
      You return an error if the asked question is outside your domain of expertise,
      if it's invalid or if you cannot provide a response for any other reason.
      Start the error response with ERROR.
    """.stripIndent();

  public Effect<String> query(String request) {
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(request).thenReply();
  }

  @FunctionTool(description = "Calculate percentage: returns (value / total) * 100")
  private double calculatePercentage(double value, double total) {
    if (total == 0) return 0.0;
    return Math.round((value / total) * 100.0 * 100.0) / 100.0;
  }

  @FunctionTool(description = "Calculate sum of an array of numbers")
  private double sum(double[] numbers) {
    double total = 0;
    for (double num : numbers) {
      total += num;
    }
    return Math.round(total * 100.0) / 100.0;
  }
}

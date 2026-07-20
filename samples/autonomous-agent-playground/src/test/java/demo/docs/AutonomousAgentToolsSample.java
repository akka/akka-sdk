/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.docs;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.*;

import demo.consulting.application.FactCheckAgent;
import demo.helloworld.application.Answer;
import demo.research.application.ResearchTasks;
import demo.research.application.Researcher;
import demo.support.application.BillingSpecialist;

/**
 * Code snippets for the AutonomousAgentTools section of the autonomous-agent testing
 * documentation. Never executed; compiled so the examples stay in step with the testkit.
 */
class AutonomousAgentToolsSample {

  void taskLifecycleTools() {
    // tag::task-lifecycle-tools[]
    // Complete a task: pass a result object matching the task's result type
    completeTask(new Answer("2 plus 2 equals 4.", 100));

    // Complete a task with raw JSON (must be a valid JSON object)
    completeTaskJson("{\"answer\":\"2 plus 2 equals 4.\",\"confidence\":100}");

    // Fail a task with a reason
    failTask("Not enough information to proceed.");
    // end::task-lifecycle-tools[]
  }

  void coordinationTools() {
    // tag::handoff-delegation-tools[]
    // Hand off the current task to another agent
    handoffTo(BillingSpecialist.class, "Customer has billing dispute");

    // Delegate a subtask to a worker agent
    delegateTo(ResearchTasks.FINDINGS, Researcher.class, "Research quantum computing");

    // Delegate to a request-based agent
    delegateTo(FactCheckAgent.class, "{\"claim\":\"Carbon emissions reduced by 40%\"}");
    // end::handoff-delegation-tools[]
  }
}

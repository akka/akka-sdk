/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.inheritance;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.workflow.Workflow;

/**
 * Components whose command handlers (and a workflow step) are declared on a base class, to verify
 * the descriptor factories discover handlers inherited from a super class. Method bodies return
 * null since the descriptors are only built, never invoked, in these tests.
 */
public class InheritedHandlerModels {

  // --- Event Sourced Entity ---

  public abstract static class BaseEsEntity extends EventSourcedEntity<Integer, String> {
    public Effect<String> inheritedIncrease(Integer value) {
      return null;
    }

    public ReadOnlyEffect<Integer> inheritedGet() {
      return null;
    }
  }

  @Component(id = "inheriting-es")
  public static class InheritingEsEntity extends BaseEsEntity {
    public Effect<String> ownCommand(String cmd) {
      return null;
    }

    public Integer applyEvent(String event) {
      return 0;
    }
  }

  // --- Key Value Entity ---

  public abstract static class BaseKvEntity extends KeyValueEntity<Integer> {
    public Effect<String> inheritedSet(Integer value) {
      return null;
    }

    public ReadOnlyEffect<Integer> inheritedGet() {
      return null;
    }
  }

  @Component(id = "inheriting-kv")
  public static class InheritingKvEntity extends BaseKvEntity {
    public Effect<String> ownSet(Integer value) {
      return null;
    }
  }

  // --- Timed Action ---

  public abstract static class BaseTimedAction extends TimedAction {
    public Effect inheritedCommand(String msg) {
      return null;
    }
  }

  @Component(id = "inheriting-action")
  public static class InheritingTimedAction extends BaseTimedAction {
    public Effect ownCommand() {
      return null;
    }
  }

  // --- Agent (must have exactly one command handler, here only the inherited one) ---

  public abstract static class BaseAgent extends Agent {
    public Effect<String> inheritedQuery(String question) {
      return null;
    }
  }

  @Component(id = "inheriting-agent")
  public static class InheritingAgent extends BaseAgent {}

  // --- Workflow (command handlers and a step declared on the base class) ---

  public record StepInput(String value) {}

  public abstract static class BaseWorkflow extends Workflow<String> {
    public Effect<String> inheritedStart(String cmd) {
      return null;
    }

    public ReadOnlyEffect<String> inheritedGet() {
      return null;
    }

    public StepEffect inheritedStep(StepInput input) {
      return null;
    }
  }

  @Component(id = "inheriting-workflow")
  public static class InheritingWorkflow extends BaseWorkflow {
    public Effect<String> ownCommand(String cmd) {
      return null;
    }
  }
}

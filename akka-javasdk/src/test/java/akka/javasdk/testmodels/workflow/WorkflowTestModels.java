/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.workflow;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.StepName;
import akka.javasdk.workflow.Workflow;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.concurrent.CompletableFuture;

public class WorkflowTestModels {

  @ComponentId("transfer-workflow")
  public static class TransferWorkflow extends Workflow<WorkflowState> {

    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }

    public StepEffect depositStep() {
      return null;
    }

    @StepName("withdraw")
    public StepEffect withdrawStep() {
      return null;
    }

    public Effect<WorkflowState> getState() {
      return null;
    }
  }

  public static class TransferWorkflowSealedInterface extends Workflow<WorkflowState> {

    public record Deposit(Long amount) {}

    public record Withdraw(Long amount) {}

    public sealed interface Transaction {}

    public record CreditTransaction(Long amount) implements Transaction {}

    public record DebitTransaction(Long amount) implements Transaction {}

    public StepEffect depositStep(Deposit deposit) {
      return null;
    }

    public StepEffect withdrawStep(Withdraw withdraw) {
      return null;
    }

    public StepEffect runTransaction(Transaction transaction) {
      return null;
    }
  }

  public static class TransferWorkflowUnannotatedInterface extends Workflow<WorkflowState> {

    public interface Transaction {}

    public record CreditTransaction(Long amount) implements Transaction {}

    public record DebitTransaction(Long amount) implements Transaction {}

    public StepEffect runTransaction(Transaction transaction) {
      return null;
    }
  }

  public static class TransferWorkflowAnnotatedInterface extends Workflow<WorkflowState> {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
      @JsonSubTypes.Type(value = CreditTransaction.class, name = "A"),
      @JsonSubTypes.Type(value = DebitTransaction.class, name = "B")
    })
    public interface Transaction {}

    public record CreditTransaction(Long amount) implements Transaction {}

    public record DebitTransaction(Long amount) implements Transaction {}

    public StepEffect runTransaction(Transaction transaction) {
      return null;
    }
  }

  public static class TransferWorkflowAnnotatedAbstractClass extends Workflow<WorkflowState> {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
      @JsonSubTypes.Type(value = CreditTransaction.class, name = "A"),
      @JsonSubTypes.Type(value = DebitTransaction.class, name = "B")
    })
    public abstract static class Transaction {}

    public static class CreditTransaction extends Transaction {}

    public static class DebitTransaction extends Transaction {}

    public StepEffect runTransaction(Transaction transaction) {
      return null;
    }
  }

  public static class TransferWorkflowUnannotatedAbstractClass extends Workflow<WorkflowState> {

    public abstract static class Transaction {}

    public static class CreditTransaction extends Transaction {}

    public static class DebitTransaction extends Transaction {}

    public StepEffect runTransaction(Transaction transaction) {
      return null;
    }
  }

  public static class TransferWorkflowWithPrimitives extends Workflow<WorkflowState> {

    public StepEffect longStep(long longVal) {
      return null;
    }

    public StepEffect intStep(int intVal) {
      return null;
    }

    public StepEffect boolStep(boolean boolVal) {
      return null;
    }

    public StepEffect shortStep(short shortVal) {
      return null;
    }

    public StepEffect charStep(char charVal) {
      return null;
    }

    public StepEffect floatStep(float floatVal) {
      return null;
    }

    public StepEffect doubleStep(double doubleVal) {
      return null;
    }
  }

  // same as above, but using legacy API ----------------------

  public static class TransferWorkflowSealedInterfaceLegacy extends Workflow<WorkflowState> {

    public record Deposit(Long amount) {}

    public record Withdraw(Long amount) {}

    public sealed interface Transaction {}

    public record CreditTransaction(Long amount) implements Transaction {}

    public record DebitTransaction(Long amount) implements Transaction {}

    @Override
    public WorkflowDef<WorkflowState> definition() {
      return workflow().addStep(depositStep()).addStep(withdrawStep()).addStep(runTransaction());
    }

    public Step depositStep() {
      return step("deposit")
          .call(Deposit.class, __ -> Done.getInstance())
          .andThen(() -> effects().end());
    }

    public Step withdrawStep() {
      return step("widthdraw")
          .asyncCall(Withdraw.class, __ -> CompletableFuture.completedFuture(Done.getInstance()))
          .andThen(Done.class, __ -> effects().end());
    }

    public Step runTransaction() {
      return step("run-transaction")
          .call(Transaction.class, __ -> Done.getInstance())
          .andThen(() -> effects().end());
    }
  }

  public static class TransferWorkflowAnnotatedInterfaceLegacy extends Workflow<WorkflowState> {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
      @JsonSubTypes.Type(value = CreditTransaction.class, name = "A"),
      @JsonSubTypes.Type(value = DebitTransaction.class, name = "B")
    })
    public interface Transaction {}

    public record CreditTransaction(Long amount) implements Transaction {}

    public record DebitTransaction(Long amount) implements Transaction {}

    @Override
    public WorkflowDef<WorkflowState> definition() {
      return workflow().addStep(runTransaction());
    }

    public Step runTransaction() {
      return step("run-transaction")
          .call(Transaction.class, __ -> Done.getInstance())
          .andThen(Done.class, __ -> effects().end());
    }
  }

  public static class TransferWorkflowUnannotatedInterfaceLegacy extends Workflow<WorkflowState> {

    public interface Transaction {}

    public record CreditTransaction(Long amount) implements Transaction {}

    public record DebitTransaction(Long amount) implements Transaction {}

    @Override
    public WorkflowDef<WorkflowState> definition() {
      return workflow().addStep(runTransaction());
    }

    public Step runTransaction() {
      return step("run-transaction")
          .call(Transaction.class, __ -> Done.getInstance())
          .andThen(() -> effects().end());
    }
  }

  public static class TransferWorkflowAnnotatedAbstractClassLegacy extends Workflow<WorkflowState> {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
      @JsonSubTypes.Type(value = CreditTransaction.class, name = "A"),
      @JsonSubTypes.Type(value = DebitTransaction.class, name = "B")
    })
    public abstract static class Transaction {}

    public static class CreditTransaction extends Transaction {}

    public static class DebitTransaction extends Transaction {}

    @Override
    public WorkflowDef<WorkflowState> definition() {
      return workflow().addStep(runTransaction());
    }

    public Step runTransaction() {
      return step("run-transaction")
          .call(Transaction.class, __ -> Done.getInstance())
          .andThen(Done.class, __ -> effects().end());
    }
  }

  public static class TransferWorkflowUnannotatedAbstractClassLegacy
      extends Workflow<WorkflowState> {

    public abstract static class Transaction {}

    public static class CreditTransaction extends Transaction {}

    public static class DebitTransaction extends Transaction {}

    @Override
    public WorkflowDef<WorkflowState> definition() {
      return workflow().addStep(runTransaction());
    }

    public Step runTransaction() {
      return step("run-transaction")
          .call(Transaction.class, __ -> Done.getInstance())
          .andThen(Done.class, __ -> effects().end());
    }
  }
}

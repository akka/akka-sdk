/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

public record TransferState(Transfer transfer, String lastStep, boolean finished, boolean accepted) {

  public TransferState(Transfer transfer, String lastStep) {
    this(transfer, lastStep, false, false);
  }

  public TransferState withLastStep(String lastStep) {
    return new TransferState(transfer, lastStep, finished, accepted);
  }

  public TransferState asAccepted() {
    return new TransferState(transfer, lastStep, finished, true);
  }

  public TransferState asFinished() {
    return new TransferState(transfer, lastStep, true, accepted);
  }
}

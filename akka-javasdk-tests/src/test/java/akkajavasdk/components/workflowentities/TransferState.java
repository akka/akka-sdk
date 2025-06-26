/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

public record TransferState(Transfer transfer, String lastStep, boolean finished, boolean accepted) {

  public static final TransferState EMPTY = new TransferState(null, null, false, false);

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

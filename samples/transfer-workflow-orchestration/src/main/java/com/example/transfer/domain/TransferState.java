package com.example.transfer.domain;

import static com.example.transfer.domain.TransferState.TransferStatus.STARTED;

public record TransferState(String transferId, Transfer transfer, TransferStatus status) {

  public enum TransferStatus {
    STARTED, WITHDRAW_SUCCEEDED, COMPLETED, WAITING_FOR_ACCEPTANCE, TRANSFER_ACCEPTANCE_TIMED_OUT
  }

  public static TransferState create(String transferId, Transfer transfer) {
    return new TransferState(transferId, transfer, STARTED);
  }

  public TransferState withStatus(TransferStatus newStatus) {
    return new TransferState(transferId, transfer, newStatus);
  }
}

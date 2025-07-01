package com.example.transfer.domain;

import java.util.UUID;

import static com.example.transfer.domain.TransferState.TransferStatus.STARTED;

public record TransferState(String transferId, Transfer transfer, TransferStatus status, String withdrawId,
                            String depositId) {

  public record Transfer(String from, String to, int amount) {
  }

  public enum TransferStatus {
    STARTED, WITHDRAW_FAILED, WITHDRAW_SUCCEEDED, DEPOSIT_FAILED, COMPLETED, COMPENSATION_COMPLETED, WAITING_FOR_ACCEPTANCE, TRANSFER_ACCEPTANCE_TIMED_OUT, REQUIRES_MANUAL_INTERVENTION
  }

  public static TransferState create(String transferId, Transfer transfer) {
    // commandIds must be the same for every attempt, that's why we keep them as a part of the state
    String withdrawId = UUID.randomUUID().toString();
    String depositId = UUID.randomUUID().toString();
    return new TransferState(transferId, transfer, STARTED, withdrawId, depositId);
  }

  public TransferState withStatus(TransferStatus newStatus) {
    return new TransferState(transferId, transfer, newStatus, withdrawId, depositId);
  }
}

package com.example.transfer.domain;

public sealed interface FraudDetectionResult {

  record TransferVerified(TransferState.Transfer transfer) implements FraudDetectionResult {
  }

  record TransferRejected(TransferState.Transfer transfer) implements FraudDetectionResult {
  }

  record TransferRequiresManualAcceptation(TransferState.Transfer transfer) implements FraudDetectionResult {
  }
}

package com.example.transfer.application;

import com.example.transfer.domain.TransferState;

import static com.example.transfer.application.FraudDetectionService.FraudDetectionResult.ACCEPTED;
import static com.example.transfer.application.FraudDetectionService.FraudDetectionResult.MANUAL_ACCEPTANCE_REQUIRED;

public class FraudDetectionService {

  public enum FraudDetectionResult{
    ACCEPTED, MANUAL_ACCEPTANCE_REQUIRED
  }

  public FraudDetectionResult detectFrauds(TransferState.Transfer transfer) {
    if (transfer.amount() > 1000) {
      return MANUAL_ACCEPTANCE_REQUIRED;
    } else {
      return ACCEPTED;
    }
  }
}

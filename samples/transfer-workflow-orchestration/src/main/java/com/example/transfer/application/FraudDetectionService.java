package com.example.transfer.application;

import static com.example.transfer.application.FraudDetectionService.FraudDetectionResult.ACCEPTED;
import static com.example.transfer.application.FraudDetectionService.FraudDetectionResult.MANUAL_ACCEPTANCE_REQUIRED;

import com.example.transfer.domain.Transfer;

public class FraudDetectionService {

  public enum FraudDetectionResult {
    ACCEPTED,
    MANUAL_ACCEPTANCE_REQUIRED,
  }

  public FraudDetectionResult check(Transfer transfer) {
    if (transfer.amount() > 1000) {
      return MANUAL_ACCEPTANCE_REQUIRED;
    } else {
      return ACCEPTED;
    }
  }
}

/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public sealed interface FraudDetectionResult {

  final class TransferVerified implements FraudDetectionResult{
    public final Transfer transfer;

    @JsonCreator
    public TransferVerified(@JsonProperty("transfer") Transfer transfer) {
      this.transfer = transfer;
    }
  }

  final class TransferRejected implements FraudDetectionResult {
    public final Transfer transfer;

    @JsonCreator
    public TransferRejected(@JsonProperty("transfer") Transfer transfer) {
      this.transfer = transfer;
    }
  }

  final class TransferRequiresManualAcceptation implements FraudDetectionResult {
    public final Transfer transfer;

    @JsonCreator
    public TransferRequiresManualAcceptation(@JsonProperty("transfer") Transfer transfer) {
      this.transfer = transfer;
    }
  }
}

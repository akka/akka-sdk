package com.example.transfer.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.transfer.domain.TransferState;

import java.util.Collection;

@ComponentId("transfer-view")
public class TransferView extends View {

  public record TransferEntry(String id, String status) {}

  public record TransferEntries(Collection<TransferEntry> entries) {}

  @Query("SELECT * as entries FROM transfers WHERE status = 'COMPLETED'")
  public QueryEffect<TransferEntries> getAllCompleted() {
    return queryResult();
  }

  @Consume.FromWorkflow(TransferWorkflow.class)
  public static class TransferUpdater extends TableUpdater<TransferEntry> {

    public Effect<TransferEntry> onUpdate(TransferState transferState) {
      var id = updateContext().eventSubject().orElse("");
      return effects().updateRow(new TransferEntry(id, transferState.status().name()));
    }
  }
}

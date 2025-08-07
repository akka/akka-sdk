/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.workflowentities.TransferState;
import akkajavasdk.components.workflowentities.TransferWorkflow;
import java.util.Collection;

@ComponentId("transfer-view")
public class TransferView extends View {

  public record TransferEntry(String id, boolean finished) {}

  public record TransferEntries(Collection<TransferEntry> entries) {}

  @Query("SELECT * as entries FROM transfers")
  public QueryEffect<TransferEntries> getAll() {
    return queryResult();
  }

  @Consume.FromWorkflow(TransferWorkflow.class)
  public static class TransferUpdater extends TableUpdater<TransferEntry> {

    public Effect<TransferEntry> onUpdate(TransferState transferState) {
      var id = updateContext().eventSubject().orElse("");
      return effects().updateRow(new TransferEntry(id, transferState.finished()));
    }

    @DeleteHandler
    public Effect<TransferEntry> onDelete() {
      return effects().deleteRow();
    }
  }
}

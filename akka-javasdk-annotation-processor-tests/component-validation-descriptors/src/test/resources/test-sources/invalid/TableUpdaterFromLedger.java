package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "table-updater-from-ledger")
public class TableUpdaterFromLedger extends View {

  @Query("SELECT * FROM interactions")
  public QueryEffect<InteractionRow> getInteractions() {
    return null;
  }

  public static class InteractionRow {
    public String interactionId;
  }

  @Consume.FromLedger
  public static class Interactions extends TableUpdater<InteractionRow> {
    public Effect<InteractionRow> onInteraction(InteractionRecord record) {
      InteractionRow row = new InteractionRow();
      row.interactionId = record.interactionId();
      return effects().updateRow(row);
    }
  }
}

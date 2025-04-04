package com.example.transfer.application;

import akka.japi.Pair;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.pattern.Patterns;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ImporterWorkflowBackgroundProcess implements Workflow.BackgroundProcess<ImporterWorkflow.ImporterWorkflowState> {

  public sealed interface MyBackgroundCommand extends Workflow.BackgroundProcessCommand {
    record StopImport() implements MyBackgroundCommand {
    }

    record Restart(ImporterWorkflow.ImporterWorkflowState importerWorkflowState) implements MyBackgroundCommand {
    }

    record Start(ImporterWorkflow.ImporterWorkflowState importerWorkflowState) implements MyBackgroundCommand {
    }
  }

  private static final Logger log = LoggerFactory.getLogger(ImporterWorkflowBackgroundProcess.class);
  private Pair<UniqueKillSwitch, CompletionStage<Integer>> streamWithKillSwitch;
  private final ComponentClient componentClient;
  private final String importerId;
  private final Materializer materializer;

  ImporterWorkflowBackgroundProcess(ComponentClient componentClient, String importerId, Materializer materializer) {
    this.componentClient = componentClient;
    this.importerId = importerId;
    this.materializer = materializer;
  }

  @Override
  public void onStart(ImporterWorkflow.ImporterWorkflowState state) {
    if (state.started() && !state.finished()) {
      startImport(state);
    } else {
      log.info("Nothing to do, import already finished or not started");
    }
  }

  private void startImport(ImporterWorkflow.ImporterWorkflowState state) {
    log.info("Starting import: " + state);
    var source = Source.range(state.offset(), 1000)
      .mapAsync(1, this::importData)
      .groupedWithin(50, Duration.ofSeconds(5))
      .mapAsync(1, grouped -> {
        Integer maxOffset = grouped.stream().max(Integer::compareTo).orElse(0); //replace with .last?
        return componentClient.forWorkflow(importerId)
          .method(ImporterWorkflow::recordProgress) //when replaced with this::recordProgress, it doesn't compile
          .invokeAsync(maxOffset)
          .thenApply(__ -> maxOffset);
      });

    this.streamWithKillSwitch =
      source
        .viaMat(KillSwitches.single(), Keep.right())
        .toMat(Sink.last(), Keep.both())
        .run(materializer);

    streamWithKillSwitch.second().whenComplete((done, error) -> {
      if (error != null) {
        log.error("Error during import: " + error.getMessage());
      } else {
        log.info("Import completed successfully");
      }
    });
  }

  @Override
  public void onStop() {
    log.info("Stopping import");
    if (streamWithKillSwitch != null) {
      streamWithKillSwitch.first().shutdown();
    }
  }

  private CompletionStage<Integer> importData(int offset) {
    log.info("Importing data from offset: " + offset);
    return Patterns.after(Duration.ofSeconds(2), materializer.system(), () -> CompletableFuture.completedFuture(offset));
  }

  @Override
  public void send(Workflow.BackgroundProcessCommand command) {
    if (command instanceof MyBackgroundCommand myBackgroundCommand) {
      switch (myBackgroundCommand) {
        case MyBackgroundCommand.Start start -> {
          if (streamWithKillSwitch == null || streamWithKillSwitch.second().toCompletableFuture().isDone()) {
            startImport(start.importerWorkflowState());
          } else {
            log.info("Import already started");
          }
        }
        case MyBackgroundCommand.Restart restart -> {
          log.info("Restarting import");
          streamWithKillSwitch.first().shutdown();
          startImport(restart.importerWorkflowState());
        }
        case MyBackgroundCommand.StopImport stopImport -> {
          log.info("Stopping import");
          streamWithKillSwitch.first().shutdown();
        }
      }
    }
  }
}


package com.example.transfer.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import akka.stream.Materializer;
import com.example.transfer.application.ImporterWorkflowBackgroundProcess.MyBackgroundCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static akka.Done.done;

@ComponentId("importer")
public class ImporterWorkflow extends Workflow<ImporterWorkflow.ImporterWorkflowState> {

  private static final Logger log = LoggerFactory.getLogger(ImporterWorkflow.class);
  private final Materializer materializer;
  private final ComponentClient componentClient;
  private final String importerId;

  public ImporterWorkflow(Materializer materializer, ComponentClient componentClient, WorkflowContext context) {
    this.materializer = materializer;
    this.componentClient = componentClient;
    this.importerId = context.workflowId();
  }

  @Override
  public Optional<BackgroundProcess<ImporterWorkflowState>> backgroundProcess() {

    return Optional.of(new ImporterWorkflowBackgroundProcess(componentClient, importerId, materializer));
  }

  public record ImporterWorkflowState(boolean started, int offset, boolean finished) {
    public ImporterWorkflowState withOffset(int offset) {
      return new ImporterWorkflowState(started, offset, finished);
    }

    public ImporterWorkflowState start() {
      return new ImporterWorkflowState(true, offset, false);
    }

    public ImporterWorkflowState stop() {
      return new ImporterWorkflowState(true, offset, true);
    }
  }

  @Override
  public ImporterWorkflowState emptyState() {
    return new ImporterWorkflowState(false, 0, false);
  }

  @Override
  public WorkflowDef<ImporterWorkflowState> definition() {
    var startImport = step("start-import")
      .asyncCall(() -> {
        currentBackgroundProcess().ifPresent(bp ->
          bp.send(new MyBackgroundCommand.Start(currentState())));
        return CompletableFuture.completedFuture(done());
      })
      .andThen(Done.class, done -> effects().pause(true));

    return workflow()
      .addStep(startImport)
      .timeout(Duration.ofMinutes(10));
  }

  public Effect<Done> startImport() {
    if (currentState().started()) {
      log.info("Import already started");
      return effects().reply(done());
    }
    return effects()
      .updateState(currentState().start())
      .transitionTo("start-import")
      .thenReply(done());
  }

  public Effect<Done> stopImport() {
    log.info("Stopping import: " + currentState());
    currentBackgroundProcess().ifPresent(bp ->
      bp.send(new MyBackgroundCommand.StopImport()));
    return effects()
      .updateState(currentState().stop())
      .pause()
      .thenReply(done());
  }

  public Effect<Done> restartImport() {
    log.info("Restarting import: " + currentState());
    currentBackgroundProcess().ifPresent(bp ->
      bp.send(new MyBackgroundCommand.Restart(currentState().start())));
    return effects()
      .updateState(currentState().start())
      .pause(true)
      .thenReply(done());
  }

  //should be private
  public Effect<Done> recordProgress(int offset) {
    log.info("Recording progress: " + offset);
    return effects()
      .updateState(currentState().withOffset(offset))
      .pause(true)
      .thenReply(done());
  }

  public Effect<ImporterWorkflowState> get() {
    return effects().reply(currentState());
  }


}

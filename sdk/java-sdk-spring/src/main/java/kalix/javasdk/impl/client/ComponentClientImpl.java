/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.client;

import kalix.javasdk.Metadata;
import kalix.javasdk.client.*;
import kalix.spring.impl.KalixClient;

import java.util.List;
import java.util.Optional;

public class ComponentClientImpl implements ComponentClient {

  private final KalixClient kalixClient;

  private Optional<Metadata> callMetadata = Optional.empty();

  public ComponentClientImpl(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public void setCallMetadata(Metadata callMetadata) {
    this.callMetadata = Optional.of(callMetadata);
  }

  public void clearCallMetadata() {
    this.callMetadata = Optional.empty();
  }

  @Override
  public ActionClient forAction() {
    return new ActionClient(kalixClient, callMetadata);
  }


  @Override
  public ValueEntityClient forValueEntity(String valueEntityId) {
    return new ValueEntityClient(kalixClient, callMetadata, valueEntityId);
  }

  @Override
  public EventSourcedEntityClient forEventSourcedEntity(String eventSourcedEntityId) {
    return new EventSourcedEntityClient(kalixClient, callMetadata, eventSourcedEntityId);
  }

  @Override
  public WorkflowClient forWorkflow() {
    return new WorkflowClient(kalixClient);
  }

  @Override
  public WorkflowClient forWorkflow(String workflowId) {
    return new WorkflowClient(kalixClient, workflowId);
  }

  @Override
  public WorkflowClient forWorkflow(String... workflowIds) {
    return new WorkflowClient(kalixClient, List.of(workflowIds));
  }

  @Override
  public ViewClient forView() {
    return new ViewClient(kalixClient, callMetadata);
  }

}

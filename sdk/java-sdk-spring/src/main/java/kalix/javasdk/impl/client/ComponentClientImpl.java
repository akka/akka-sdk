/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.client;

import kalix.javasdk.Metadata;
import kalix.javasdk.client.ActionClient;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.client.EventSourcedEntityClient;
import kalix.javasdk.client.ValueEntityClient;
import kalix.javasdk.client.ViewClient;
import kalix.javasdk.client.WorkflowClient;
import kalix.spring.impl.KalixClient;

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
    return new ValueEntityClientImpl(kalixClient.runtimeComponentClients().valueEntityClient(), callMetadata, valueEntityId, kalixClient.executionContext());
  }

  @Override
  public EventSourcedEntityClient forEventSourcedEntity(String eventSourcedEntityId) {
    return new EventSourcedEntityClientImpl(kalixClient.runtimeComponentClients().eventSourcedEntityClient(), callMetadata, eventSourcedEntityId, kalixClient.executionContext());
  }


  @Override
  public WorkflowClient forWorkflow(String workflowId) {
    return new WorkflowClientImpl(kalixClient.runtimeComponentClients().workFlowClient(), callMetadata, workflowId, kalixClient.executionContext());
  }


  @Override
  public ViewClient forView() {
    return new ViewClientImpl(kalixClient.runtimeComponentClients().viewClient(), callMetadata, kalixClient.executionContext());
  }

}

/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.NotUsed;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.javasdk.DeferredCall;
import akka.javasdk.Metadata;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.client.*;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.impl.client.MethodRefResolver;
import akka.pattern.RetrySettings;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of {@link ComponentClient} for use in unit tests.
 *
 * <p>Allows stubbing responses for component method calls so that components can be tested in
 * isolation without a running service.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * MockComponentClient mockClient = MockComponentClient.create();
 *
 * mockClient.forEventSourcedEntity("entity-1")
 *     .method(CustomerEntity::getState)
 *     .thenReturn(new Customer("Alice"));
 * }</pre>
 */
public class MockComponentClient implements ComponentClient {

  private final Map<StubKey, Object> stubs = new ConcurrentHashMap<>();

  private MockComponentClient() {}

  public static MockComponentClient create() {
    return new MockComponentClient();
  }

  @SuppressWarnings("unchecked")
  <R> R lookupStub(StubKey key) {
    Object value = stubs.get(key);
    if (value == null) {
      throw new IllegalStateException(
          "No stub registered for "
              + key.componentType
              + "('"
              + key.componentId
              + "')."
              + key.methodName
              + "(). "
              + "Use mockClient.for"
              + key.componentType
              + "('"
              + key.componentId
              + "').method(...)."
              + "thenReturn(value) to register a stub.");
    }
    return (R) value;
  }

  void registerStub(StubKey key, Object value) {
    stubs.put(key, value);
  }

  // -- ComponentClient implementation --

  @Override
  public TimedActionClient forTimedAction() {
    throw new UnsupportedOperationException(
        "MockComponentClient does not yet support TimedAction. This will be added in a future"
            + " version.");
  }

  @Override
  public KeyValueEntityClient forKeyValueEntity(String keyValueEntityId) {
    throw new UnsupportedOperationException(
        "MockComponentClient does not yet support KeyValueEntity. This will be added in a future"
            + " version.");
  }

  @Override
  public MockEventSourcedEntityClient forEventSourcedEntity(String eventSourcedEntityId) {
    return new MockEventSourcedEntityClient(this, eventSourcedEntityId);
  }

  @Override
  public WorkflowClient forWorkflow(String workflowId) {
    throw new UnsupportedOperationException(
        "MockComponentClient does not yet support Workflow. This will be added in a future"
            + " version.");
  }

  @Override
  public ViewClient forView() {
    throw new UnsupportedOperationException(
        "MockComponentClient does not yet support View. This will be added in a future version.");
  }

  @Override
  public AgentClient forAgent() {
    throw new UnsupportedOperationException(
        "MockComponentClient does not yet support Agent. This will be added in a future version.");
  }

  // -- Stub key --

  static final class StubKey {
    final String componentType;
    final String componentId;
    final String methodName;

    StubKey(String componentType, String componentId, String methodName) {
      this.componentType = componentType;
      this.componentId = componentId;
      this.methodName = methodName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof StubKey that)) return false;
      return componentType.equals(that.componentType)
          && componentId.equals(that.componentId)
          && methodName.equals(that.methodName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(componentType, componentId, methodName);
    }
  }

  // -- Mock EventSourcedEntityClient --

  public static final class MockEventSourcedEntityClient implements EventSourcedEntityClient {
    private final MockComponentClient parent;
    private final String entityId;

    MockEventSourcedEntityClient(MockComponentClient parent, String entityId) {
      this.parent = parent;
      this.entityId = entityId;
    }

    @Override
    public <T, R> MockComponentMethodRef<R> method(
        Function<T, EventSourcedEntity.Effect<R>> methodRef) {
      String methodName = MethodRefResolver.resolveMethodRef(methodRef).getName();
      StubKey key = new StubKey("EventSourcedEntity", entityId, methodName);
      return new MockComponentMethodRef<>(parent, key);
    }

    @Override
    public <T, A1, R> MockComponentMethodRef1<A1, R> method(
        Function2<T, A1, EventSourcedEntity.Effect<R>> methodRef) {
      String methodName = MethodRefResolver.resolveMethodRef(methodRef).getName();
      StubKey key = new StubKey("EventSourcedEntity", entityId, methodName);
      return new MockComponentMethodRef1<>(parent, key);
    }

    @Override
    public <T, R> ComponentStreamMethodRef<R> notificationStream(
        Function<T, NotificationPublisher.NotificationStream<R>> methodRef) {
      throw new UnsupportedOperationException(
          "MockComponentClient does not yet support notification streams.");
    }
  }

  // -- Mock method refs --

  public static final class MockComponentMethodRef<R>
      implements ComponentMethodRef<R>, ComponentInvokeOnlyMethodRef<R> {
    private final MockComponentClient parent;
    private final StubKey key;

    MockComponentMethodRef(MockComponentClient parent, StubKey key) {
      this.parent = parent;
      this.key = key;
    }

    /** Register a stubbed return value for this method. */
    public void thenReturn(R value) {
      parent.registerStub(key, value);
    }

    @Override
    public R invoke() {
      return parent.lookupStub(key);
    }

    @Override
    public CompletionStage<R> invokeAsync() {
      try {
        R value = invoke();
        return CompletableFuture.completedFuture(value);
      } catch (Exception e) {
        return CompletableFuture.failedFuture(e);
      }
    }

    @Override
    public MockComponentMethodRef<R> withMetadata(Metadata metadata) {
      return this;
    }

    @Override
    public ComponentInvokeOnlyMethodRef<R> withRetry(RetrySettings retrySettings) {
      return this;
    }

    @Override
    public ComponentInvokeOnlyMethodRef<R> withRetry(int maxRetries) {
      return this;
    }

    @Override
    public DeferredCall<NotUsed, R> deferred() {
      return new MockDeferredCall<>(NotUsed.getInstance(), invoke(), Metadata.EMPTY);
    }
  }

  public static final class MockComponentMethodRef1<A1, R>
      implements ComponentMethodRef1<A1, R>, ComponentInvokeOnlyMethodRef1<A1, R> {
    private final MockComponentClient parent;
    private final StubKey key;

    MockComponentMethodRef1(MockComponentClient parent, StubKey key) {
      this.parent = parent;
      this.key = key;
    }

    /** Register a stubbed return value for this method. */
    public void thenReturn(R value) {
      parent.registerStub(key, value);
    }

    @Override
    public R invoke(A1 arg) {
      return parent.lookupStub(key);
    }

    @Override
    public CompletionStage<R> invokeAsync(A1 arg) {
      try {
        R value = invoke(arg);
        return CompletableFuture.completedFuture(value);
      } catch (Exception e) {
        return CompletableFuture.failedFuture(e);
      }
    }

    @Override
    public MockComponentMethodRef1<A1, R> withMetadata(Metadata metadata) {
      return this;
    }

    @Override
    public ComponentInvokeOnlyMethodRef1<A1, R> withRetry(RetrySettings retrySettings) {
      return this;
    }

    @Override
    public ComponentInvokeOnlyMethodRef1<A1, R> withRetry(int maxRetries) {
      return this;
    }

    @Override
    public DeferredCall<A1, R> deferred(A1 arg) {
      return new MockDeferredCall<>(arg, invoke(arg), Metadata.EMPTY);
    }
  }

  // -- Mock DeferredCall --

  static final class MockDeferredCall<I, O> implements DeferredCall<I, O> {
    private final I message;
    private final O result;
    private final Metadata metadata;

    MockDeferredCall(I message, O result, Metadata metadata) {
      this.message = message;
      this.result = result;
      this.metadata = metadata;
    }

    @Override
    public I message() {
      return message;
    }

    @Override
    public Metadata metadata() {
      return metadata;
    }

    @Override
    public DeferredCall<I, O> withMetadata(Metadata metadata) {
      return new MockDeferredCall<>(message, result, metadata);
    }
  }
}

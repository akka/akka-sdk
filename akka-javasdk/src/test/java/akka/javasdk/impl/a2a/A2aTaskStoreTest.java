/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.Done;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatus;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.KeyValueEntityClient;
import akka.javasdk.client.WorkflowClient;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class A2aTaskStoreTest {

  private static final A2aTask TASK =
      new A2aTask(
          "task-1",
          "context-1",
          new A2aTaskStatus(A2aTaskState.WORKING, null, Instant.now()),
          null,
          List.of(),
          null);

  private static final A2aTaskPushNotificationConfig CONFIG =
      new A2aTaskPushNotificationConfig(
          null, "config-1", "task-1", "https://hooks.example.com/a2a", "token", null);

  /**
   * A component client whose blocking calls fail the test. The asynchronous calls answer the task
   * change, the push configurations and the start of a delivery, and are recorded.
   */
  private static final class AsyncOnlyClient {
    final List<String> calls = new CopyOnWriteArrayList<>();
    final List<String> blockingCalls = new CopyOnWriteArrayList<>();

    ComponentClient client() {
      return proxy(
          ComponentClient.class,
          (method, args) ->
              switch (method.getName()) {
                case "forKeyValueEntity" -> proxy(KeyValueEntityClient.class, this::methodRef);
                case "forWorkflow" -> proxy(WorkflowClient.class, this::methodRef);
                default -> throw new UnsupportedOperationException(method.getName());
              });
    }

    private Object methodRef(Method method, Object[] args) {
      Handler handler =
          (callMethod, callArgs) -> {
            var call = callMethod.getName();
            if (call.equals("invoke")) {
              blockingCalls.add(call);
              throw new AssertionError("blocking call");
            }
            if (!call.equals("invokeAsync")) throw new UnsupportedOperationException(call);
            Object arg = callArgs == null || callArgs.length == 0 ? null : callArgs[0];
            Object reply;
            if (arg == null) {
              calls.add("push configurations");
              reply = new A2aTaskExtrasEntity.State(List.of(CONFIG), null);
            } else if (arg instanceof A2aTaskEntity.Change) {
              calls.add("change");
              reply =
                  new A2aTaskEntity.Changed(
                      new A2aTaskEntity.State(TASK, "echo", null, 2, "session"), true);
            } else {
              calls.add("start delivery");
              reply = Done.getInstance();
            }
            // Completed later on another thread, as a runtime call completes
            return CompletableFuture.supplyAsync(
                () -> reply, CompletableFuture.delayedExecutor(10, TimeUnit.MILLISECONDS));
          };
      return proxy(method.getReturnType(), handler);
    }
  }

  interface Handler {
    Object handle(Method method, Object[] args);
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<?> type, Handler handler) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (p, method, args) -> handler.handle(method, args));
  }

  @Test
  void changesATaskAndStartsItsPushDeliveriesWithoutBlocking() throws Exception {
    var fake = new AsyncOnlyClient();
    var store = new A2aTaskStore(fake.client());

    var stored =
        store
            .changeAsync("task-1", A2aTaskEntity.Change.status(A2aTaskState.COMPLETED, null))
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertTrue(stored.isPresent());
    assertEquals(List.of(), fake.blockingCalls);
    assertEquals(List.of("change", "push configurations", "start delivery"), fake.calls);
  }

  @Test
  void startsThePushDeliveriesOfAChangedTaskWithoutBlocking() throws Exception {
    var fake = new AsyncOnlyClient();

    A2aPushNotifier.taskChanged(
            fake.client(), new A2aTaskEntity.State(TASK, "echo", null, 3, "session"))
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);

    assertEquals(List.of(), fake.blockingCalls);
    assertEquals(List.of("push configurations", "start delivery"), fake.calls);
  }
}

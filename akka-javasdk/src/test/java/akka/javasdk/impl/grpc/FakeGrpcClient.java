/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.grpc;

import akka.Done;
import akka.actor.ClassicActorSystemProvider;
import akka.grpc.GrpcClientSettings;
import akka.grpc.javadsl.AkkaGrpcClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/** Records the settings and request headers that GrpcClientProviderImpl passes to a client. */
public final class FakeGrpcClient implements AkkaGrpcClient {

  public final GrpcClientSettings settings;
  public final List<Map.Entry<String, String>> headers;
  private final CompletableFuture<Done> closed;

  private FakeGrpcClient(
      GrpcClientSettings settings,
      List<Map.Entry<String, String>> headers,
      CompletableFuture<Done> closed) {
    this.settings = settings;
    this.headers = headers;
    this.closed = closed;
  }

  public static FakeGrpcClient create(
      GrpcClientSettings settings, ClassicActorSystemProvider system) {
    return new FakeGrpcClient(settings, List.of(), new CompletableFuture<>());
  }

  @Override
  public AkkaGrpcClient addRequestHeader(String key, String value) {
    return new FakeGrpcClient(
        settings,
        Stream.concat(headers.stream(), Stream.of(Map.entry(key, value))).toList(),
        closed);
  }

  @Override
  public CompletionStage<Done> close() {
    closed.complete(Done.getInstance());
    return closed;
  }

  @Override
  public CompletionStage<Done> closed() {
    return closed;
  }
}

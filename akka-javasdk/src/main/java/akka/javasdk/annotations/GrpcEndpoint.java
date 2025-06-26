/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.*;

/**
 * Mark a class to be made available as a gRPC endpoint. The annotated class should extend a gRPC service interface
 * generated using Akka gRPC, be public and have a public constructor.
 * <p>
 * Annotated classes can accept the following types to the constructor:
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient}</li>
 *   <li>{@link akka.javasdk.http.HttpClientProvider}</li>
 *   <li>{@link akka.javasdk.timer.TimerScheduler}</li>
 *   <li>{@link akka.stream.Materializer}</li>
 *   <li>{@link com.typesafe.config.Config}</li>
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GrpcEndpoint {
}

/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a class to be made available as an HTTP endpoint. The annotated class should be public and have a public
 * constructor.
 * <p>
 * Annotated classes can accept the following types to the constructor:
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient}</li>
 *   <li>{@link akka.javasdk.http.HttpClientProvider}</li>
 *   <li>{@link akka.javasdk.timer.TimerScheduler}</li>
 *   <li>{@link akka.stream.Materializer}</li>
 *   <li>{@link com.typesafe.config.Config}</li>
 *   <li>{@link io.opentelemetry.api.trace.Span}</li>
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup</li>
 * </ul>
 * <p>If the annotated class extends {@link akka.javasdk.http.AbstractHttpEndpoint} the request context
 * is available without constructor injection.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpEndpoint {
    String value() default "";
}

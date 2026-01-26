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
 * Annotation to mark an endpoint method as handling HTTP WebSocket upgrade requests.
 *
 * <p>WebSockets allow bidirectional streaming text or binary communication with a client. Http
 * method will always be GET.
 *
 * <p><strong>Path Configuration:</strong> The annotation value specifies the path pattern for this
 * endpoint, which is combined with the {@link HttpEndpoint} class-level path prefix to form the
 * complete URL.
 *
 * <p><strong>Request Bodies:</strong> WebSocket annotated methods must not accept a body parameter,
 * there is no request body, instead additional data will have to be passed over the WebSocket
 * connection after the upgrade completes.
 *
 * <p><strong>Path Parameters:</strong> Use {@code {paramName}} in the path to identify the specific
 * resource to create or update. These can be combined with request body parameters.
 *
 * <p><strong>Return value:</strong> WebSocket annotated types must return {@link
 * akka.stream.javadsl.Flow} handling the stream of incoming messages, messages coming out of hte
 * flow are emitted to the client. The incoming and outgoing message type must both be the same
 * type. Supported types are {@code String} for text, {@link akka.util.ByteString} for binary
 * messages, or {@link akka.http.javadsl.model.ws.Message} for a lower level handling of the
 * protocol.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @WebSocket("/ping-pong-websocket")
 * public Flow<String, String, NotUsed> pingPong() {
 *     return Flow.of(String.class).map(incoming -> "pong: " + incoming)
 * }
 * }</pre>
 *
 * <p><strong>IMPORTANT</strong> WebSocket endpoints always work locally, but in a deployed service,
 * require additional setup. See the <a
 * href="https://doc.akka.io/operations/services/invoke-service.html#websockets">Akka SDK
 * documentation</a> for more details about setup.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebSocket {
  String value() default "";
}

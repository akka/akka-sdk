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
 * Annotation to mark a method as handling WebSocket connections.
 *
 * <p>WebSocket methods establish bidirectional streaming connections between the client and server.
 * Unlike HTTP request-response patterns, WebSocket connections remain open and allow real-time,
 * full-duplex communication.
 *
 * <p><strong>Return Type:</strong> WebSocket methods must return one of:
 *
 * <ul>
 *   <li>{@code Flow<String, String, NotUsed>} for text message streams
 *   <li>{@code Flow<ByteString, ByteString, NotUsed>} for binary message streams
 * </ul>
 *
 * The first type parameter is the incoming message type (from client), and the second is the
 * outgoing message type (to client).
 *
 * <p><strong>Path Configuration:</strong> The annotation value specifies the path pattern for this
 * WebSocket endpoint, which is combined with the {@link HttpEndpoint} class-level path prefix to
 * form the complete URL.
 *
 * <p><strong>Path Parameters:</strong> Use {@code {paramName}} in the path to extract URL segments
 * as method parameters. The parameters must match the method signature. For example, a path {@code
 * "/chat/{roomId}"} requires a method parameter {@code String roomId}.
 *
 * <p><strong>Example Usage:</strong>
 *
 * <pre>{@code
 * @HttpEndpoint("/ws")
 * @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
 * public class MyWebSocketEndpoint {
 *
 *   @WebSocket("/echo")
 *   public Flow<String, String, NotUsed> echo() {
 *     return Flow.create();  // Echo messages back
 *   }
 *
 *   @WebSocket("/chat/{roomId}")
 *   public Flow<String, String, NotUsed> chatRoom(String roomId) {
 *     // Handle chat for specific room
 *     return Flow.<String>create()
 *       .map(msg -> "Room " + roomId + ": " + msg);
 *   }
 *
 *   @WebSocket("/binary")
 *   public Flow<ByteString, ByteString, NotUsed> binaryData() {
 *     return Flow.create();  // Handle binary WebSocket messages
 *   }
 * }
 * }</pre>
 *
 * <p><strong>Connection Lifecycle:</strong> The WebSocket connection is established via HTTP
 * upgrade and remains open until either the client or server closes it. The returned Flow defines
 * how messages are processed during the connection lifetime.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebSocket {
  /**
   * The path pattern for this WebSocket endpoint, relative to the {@link HttpEndpoint} prefix.
   *
   * @return the path pattern, or empty string for the endpoint root path
   */
  String value() default "";
}

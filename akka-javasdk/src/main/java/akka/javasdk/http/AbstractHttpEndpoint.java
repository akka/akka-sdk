/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.annotation.InternalApi;

/**
 * Optional base class for HTTP endpoints providing convenient access to request context.
 *
 * <p>HTTP endpoints expose services to the outside world through RESTful APIs. They handle incoming
 * HTTP requests and can return responses in various formats including JSON, plain text, or custom
 * content types.
 *
 * <p><strong>Basic Usage:</strong>
 *
 * <pre>{@code
 * @HttpEndpoint("/api")
 * @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
 * public class MyEndpoint extends AbstractHttpEndpoint {
 *
 *   @Get("/hello/{name}")
 *   public String hello(String name) {
 *     return "Hello " + name;
 *   }
 *
 *   @Post("/users")
 *   public HttpResponse createUser(CreateUserRequest request) {
 *     // Access request context for headers, query params, etc.
 *     String userAgent = requestContext().requestHeader("User-Agent")
 *         .map(HttpHeader::value)
 *         .orElse("Unknown");
 *
 *     // Process request and return response
 *     return HttpResponses.created(new User(request.name()));
 *   }
 *
 *   @Get("/search")
 *   public List<Result> search() {
 *     // Access query parameters
 *     String query = requestContext().queryParams()
 *         .getString("q").orElse("");
 *     return performSearch(query);
 *   }
 * }
 * }</pre>
 *
 * <p><strong>HTTP Methods:</strong> Annotate methods with {@link
 * akka.javasdk.annotations.http.Get}, {@link akka.javasdk.annotations.http.Post}, {@link
 * akka.javasdk.annotations.http.Put}, {@link akka.javasdk.annotations.http.Patch}, or {@link
 * akka.javasdk.annotations.http.Delete} to handle different HTTP verbs.
 *
 * <p><strong>Path Parameters:</strong> Use {@code {paramName}} in paths to extract URL segments as
 * method parameters. Parameters can be strings, numbers, or other primitive types.
 *
 * <p><strong>Request Bodies:</strong> Accept JSON request bodies by adding parameters that Jackson
 * can deserialize. The request body parameter must come last in the parameter list when combined
 * with path parameters.
 *
 * <p><strong>Response Types:</strong> Return strings for text responses, objects for JSON
 * responses, {@link akka.http.javadsl.model.HttpResponse} for full control, or {@code
 * CompletionStage<T>} for asynchronous responses.
 *
 * <p><strong>Request Context:</strong> Extending this class provides access to {@link
 * RequestContext} via {@link #requestContext()} without requiring constructor injection. The
 * context provides access to headers, query parameters, JWT claims, and tracing information.
 *
 * <p><strong>Alternative Approach:</strong> Instead of extending this class, you can inject {@link
 * RequestContext} directly into your endpoint constructor or use dependency injection for other
 * services like {@link akka.javasdk.client.ComponentClient}.
 *
 * <p><strong>Security:</strong> Always annotate endpoints with appropriate {@link
 * akka.javasdk.annotations.Acl} annotations to control access. Without ACL annotations, no clients
 * are allowed to access the endpoint.
 */
public abstract class AbstractHttpEndpoint {

  private volatile RequestContext context;

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public final void _internalSetRequestContext(RequestContext context) {
    this.context = context;
  }

  /** Always available from request handling methods, not available from the constructor. */
  protected final RequestContext requestContext() {
    if (context == null) {
      throw new IllegalStateException(
          "The request context can only be accessed from the request handling methods of the"
              + " endpoint.");
    }
    return context;
  }
}

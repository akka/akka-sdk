package demo.ui.api;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.Location;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;

/**
 * Serves the static UI assets for the playground. Three routes:
 * - GET / → 302 to /playground (convenience landing).
 * - GET /playground/static/** → asset tree under static-resources/playground/static/.
 * - GET /playground/** → SPA fallback, returns index.html so the client-side router takes over.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class PlaygroundUiEndpoint {

  private static final String STATIC_PREFIX = "/playground/static/";

  @Get("/")
  public HttpResponse root() {
    return HttpResponse.create()
      .withStatus(StatusCodes.FOUND)
      .addHeader(Location.create("/playground"));
  }

  @Get("/playground/**")
  public HttpResponse playgroundPath(HttpRequest request) {
    var path = request.getUri().path();
    if (path.startsWith(STATIC_PREFIX)) {
      return HttpResponses.staticResource(request, STATIC_PREFIX);
    }
    // SPA fallback: any non-asset path under /playground returns index.html so the client-side
    // router can render the right view (landing, panel, run, or in-app 404 for unknown run id).
    return HttpResponses.staticResource("playground/index.html");
  }
}

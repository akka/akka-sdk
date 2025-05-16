package akka.ask.agent.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;

/**
 * This Http endpoint returns the static UI page located under
 * src/main/resources/static-resources/
 */
// tag::endpoint[]
@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class UiEndpoint {
  @Get("/")
  public HttpResponse index() {
    return HttpResponses.staticResource("index.html"); // <1>
  }
}
// end::endpoint[]

package akka.ask.indexer.api;

import akka.ask.indexer.application.RagIndexingWorkflow;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::class[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api/index")
public class IndexerEndpoint {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ComponentClient componentClient;

  public IndexerEndpoint(ComponentClient componentClient) {

    this.componentClient = componentClient;
  }

  @Post("/start")
  public HttpResponse startIndexation() {
    componentClient.forWorkflow("indexing")
      .method(RagIndexingWorkflow::start)
      .invoke();
    return HttpResponses.accepted();
  }

  @Post("/abort")
  public HttpResponse abortIndexation() {
    componentClient.forWorkflow("indexing")
      .method(RagIndexingWorkflow::abort)
      .invoke();
    return HttpResponses.accepted();
  }
}
// end::class[]

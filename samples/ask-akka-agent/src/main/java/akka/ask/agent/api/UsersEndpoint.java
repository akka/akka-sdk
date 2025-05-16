package akka.ask.agent.api;

import akka.ask.agent.application.ConversationHistoryView;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;

/**
 * Endpoint to fetch user's sessions using the ConversationHistoryView.
 */
// tag::endpoint[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api")
public class UsersEndpoint {

  private final ComponentClient componentClient;

  public UsersEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/users/{userId}/sessions/")
  public ConversationHistoryView.ConversationHistory getSession(String userId) {

    return componentClient.forView()
        .method(ConversationHistoryView::getSessionsByUser)
        .invoke(userId);
  }
}
// end::endpoint[]

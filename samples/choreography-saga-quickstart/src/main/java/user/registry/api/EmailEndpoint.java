package user.registry.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.application.UniqueEmailEntity;

import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api")
public class EmailEndpoint {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ComponentClient client;

  public EmailEndpoint(ComponentClient client) {
    this.client = client;
  }

  /**
   * This is gives access to the email state.
   */
  @Get("/emails/{address}")
  public UserEndpoint.EmailInfo getEmailInfo(String address) {
    var email = client.forKeyValueEntity(address)
        .method(UniqueEmailEntity::getState).invoke();
    var emailInfo =
        new UserEndpoint.EmailInfo(
            email.address(),
            email.status().toString(),
            email.ownerId());

    logger.info("Getting email info: {}", emailInfo);
    return emailInfo;
  }
}

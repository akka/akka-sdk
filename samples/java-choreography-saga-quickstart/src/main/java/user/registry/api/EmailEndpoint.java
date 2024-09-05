package user.registry.api;

import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.application.UniqueEmailEntity;

import java.util.concurrent.CompletionStage;

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
  public CompletionStage<UserEndpoint.EmailInfo> getEmailInfo(String address) {
    return
      client.forKeyValueEntity(address)
        .method(UniqueEmailEntity::getState).invokeAsync()
        .thenApply(email -> {
          var emailInfo =
            new UserEndpoint.EmailInfo(
              email.address(),
              email.status().toString(),
              email.ownerId());

          logger.info("Getting email info: {}", emailInfo);
          return emailInfo;
        });
  }
}

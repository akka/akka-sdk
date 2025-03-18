package com.example.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.example.application.UserActivityView;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/users")
public class UserEndpoint extends AbstractHttpEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(UserEndpoint.class);

  private final ComponentClient componentClient;

  public UserEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/{userId}/pools")
  public CompletionStage<UserActivityView.PoolSummaries> getPools(String userId) {

    var pageToken = requestContext().queryParams().getString("pageToken").orElse("");
    var limit = requestContext().queryParams().getInteger("pageLimit").orElse(20);

    logger.debug("Retrieving pools for user [{}] (token: {}, limit: {})", userId, pageToken, limit);

    var request = new UserActivityView.UserRequestWithPaging(userId, pageToken, limit);

    return componentClient.forView().method(UserActivityView::getPools).invokeAsync(request);
  }

  @Get("/{userId}/pools/{poolId}/activity")
  public CompletionStage<UserActivityView.ActivitySummary> getPoolActivity(
      String userId, String poolId) {

    logger.debug("Retrieving activity for user [{}] in pool [{}]", userId, poolId);

    var request = new UserActivityView.UserAndPoolRequest(userId, poolId);

    return componentClient.forView().method(UserActivityView::getPoolActivity).invokeAsync(request);
  }

  @Get("/{userId}/activities")
  public CompletionStage<UserActivityView.ActivitySummaries> getUserActivities(String userId) {

    var pageToken = requestContext().queryParams().getString("pageToken").orElse("");
    var limit = requestContext().queryParams().getInteger("pageLimit").orElse(20);

    logger.debug(
        "Retrieving activities for user [{}] (token: {}, limit: {})", userId, pageToken, limit);

    var request = new UserActivityView.UserRequestWithPaging(userId, pageToken, limit);

    return componentClient
        .forView()
        .method(UserActivityView::getAllActivities)
        .invokeAsync(request);
  }
}

package user.registry.api;


import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.application.UniqueEmailEntity;
import user.registry.application.UserEntity;
import user.registry.domain.UniqueEmail;
import user.registry.domain.User;
import user.registry.application.UsersByCountryView;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Controller for the user registry application.
 * This controller works as a gateway for the user service. It receives the requests from the outside world and
 * forwards them to the user service and ensure that the email address is not already reserved.
 * <p>
 * The UserEntity is protected from external access. It can only be accessed through this controller.
 */
// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api/users")
public class UserEndpoint {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ComponentClient client;

  public UserEndpoint(ComponentClient client) {
    this.client = client;
  }


  /**
   * External API representation of an email record
   */
  public record EmailInfo(String address, String status, Optional<String> ownerId) {
  }

  /**
   * External API representation of a User
   */
  public record UserInfo(String id, String name, String country, String email) {
  }

  /**
   * This is the main entry point for creating a new user.
   * <p>
   * Before creating a User, we need to reserve the email address to ensure that it is not already used.
   * The call will fail if the email address is already reserved.
   * <p>
   * If we succeed in reserving the email address, we move forward and create the user.
   */
  @Post("/{userId}")
  public CompletionStage<HttpResponse> createUser(String userId, User.Create cmd) {

    var createUniqueEmail = new UniqueEmail.ReserveEmail(cmd.email(), userId);

    logger.info("Reserving new address '{}'", cmd.email());
    // eagerly, reserving the email address
    // we want to execute this call in other to check its result
    // and decide if we can continue with the user creation
    var emailReserved =
      client
        .forKeyValueEntity(cmd.email())
        .method(UniqueEmailEntity::reserve)
        .invokeAsync(createUniqueEmail) // eager, executing it now
        .thenApply(result ->
          switch (result) {
            case UniqueEmailEntity.Result.Success s -> s;
            case UniqueEmailEntity.Result.AlreadyReserved e -> {
              logger.info("Email is already reserved '{}'", cmd.email());
              throw HttpException.badRequest("Email is already reserved '" + cmd.email() + "'");
            }
        });


    var userCreated =
      emailReserved
        .thenCompose(__ -> {
          // on successful email reservation, we create the user and return the result
          logger.info("Creating user '{}'", userId);
          return client
            .forEventSourcedEntity(userId)
            .method(UserEntity::createUser)
            .invokeAsync(cmd)
            .thenApply(result ->
            switch (result) {
              case UserEntity.Result.Success s -> HttpResponses.ok();
              case UserEntity.Result.InvalidCommand error -> {
                String msg = "Couldn't create user: " + error.msg();
                logger.info(msg);
                throw HttpException.badRequest(msg);
              }
            });
        })
        ;

    return userCreated;
  }


  @Put("/{userId}/email")
  public CompletionStage<HttpResponse> changeEmail(String userId, User.ChangeEmail cmd) {

    var createUniqueEmail = new UniqueEmail.ReserveEmail(cmd.newEmail(), userId);

    logger.info("Reserving new address '{}'", cmd.newEmail());
    // eagerly, reserving the email address
    // we want to execute this call in other to check its result
    // and decide if we can continue with the change the user's email address
    var emailReserved =
      client
        .forKeyValueEntity(cmd.newEmail())
        .method(UniqueEmailEntity::reserve)
        .invokeAsync(createUniqueEmail); // eager, executing it now

    var userCreated =
      emailReserved
        .thenCompose(__ -> {
          // on successful email reservation, we change the user's email addreess
          logger.info("Changing user's address '{}'", userId);
          return client
            .forEventSourcedEntity(userId)
            .method(UserEntity::changeEmail)
            .invokeAsync(cmd)
            .thenApply(done -> HttpResponses.ok());
        })
        .exceptionally(e -> {
          // in case of exception `callToUser` is not executed,
          // and we return an error to the caller of this method
          logger.info("Email already reserved '{}'", e.getMessage());
          throw HttpException.badRequest("Email already reserved");
        });

    return userCreated;

  }


  /**
   * This is gives access to the user state.
   */
  @Get("/{userId}")
  public CompletionStage<UserInfo> getUserInfo(String userId) {
    return
      client.forEventSourcedEntity(userId)
        .method(UserEntity::getState)
        .invokeAsync()
        .thenApply(user -> {
          var userInfo =
            new UserInfo(
              userId,
              user.name(),
              user.country(),
              user.email());

          logger.info("Getting user info: {}", userInfo);
          return userInfo;
        });
  }


  @Get("/by-country/{country}")
  public CompletionStage<UsersByCountryView.UserList> getUsersByCountry(String country) {
    return
      client.forView()
        .method(UsersByCountryView::getUserByCountry)
        .invokeAsync(country);
  }
}

package user.registry.api;


import akka.platform.javasdk.annotations.http.Endpoint;
import akka.platform.javasdk.annotations.http.Get;
import akka.platform.javasdk.annotations.http.Post;
import akka.platform.javasdk.annotations.http.Put;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.common.Done;
import user.registry.domain.UniqueEmail;
import user.registry.domain.User;
import user.registry.entity.UniqueEmailEntity;
import user.registry.entity.UserEntity;
import user.registry.views.UsersByCountryView;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Controller for the user registry application.
 * This controller works as a gateway for the user service. It receives the requests from the outside world and
 * forwards them to the user service and ensure that the email address is not already reserved.
 * <p>
 * The UniqueEmailEntity and the UserEntity are protected from external access. They can only be accessed through
 * this controller.
 */
@Endpoint("/api")
public class ApplicationController {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ComponentClient client;

  public ApplicationController(ComponentClient client) {
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
  @Post("/users/{userId}")
  public CompletionStage<Done> createUser(String userId, User.Create cmd) {

    var createUniqueEmail = new UniqueEmail.ReserveEmail(cmd.email(), userId);

    logger.info("Reserving new address '{}'", cmd.email());
    // eagerly, reserving the email address
    // we want to execute this call in other to check its result
    // and decide if we can continue with the user creation
    var emailReserved =
      client
        .forKeyValueEntity(cmd.email())
        .method(UniqueEmailEntity::reserve)
        .invokeAsync(createUniqueEmail); // eager, executing it now

    // this call is lazy and will be executed only if the email reservation succeeds
    var callToUser =
      client
        .forEventSourcedEntity(userId)
        .method(UserEntity::createUser)
        .deferred(cmd);


    var userCreated =
      emailReserved
        .thenCompose(__ -> {
          // on successful email reservation, we create the user and return the result
          logger.info("Creating user '{}'", userId);
          return callToUser.invokeAsync();
        })
        .exceptionally(e -> {
          // in case of exception `callToUser` is not executed,
          // and we return an error to the caller of this method
          logger.info("Email is already reserved '{}'", cmd.email());
          throw HttpException.badRequest("Email is already reserved '" + cmd.email() + "'");
        });

    return userCreated;
  }


  @Put("/users/{userId}/change-email")
  public CompletionStage<Done> changeEmail(String userId, User.ChangeEmail cmd) {

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

    // this call is lazy and will be executed only if the email reservation succeeds
    var callToUser =
      client
        .forEventSourcedEntity(userId)
        .method(UserEntity::changeEmail)
        .deferred(cmd);


    var userCreated =
      emailReserved
        .thenCompose(__ -> {
          // on successful email reservation, we change the user's email addreess
          logger.info("Changing user's address '{}'", userId);
          return callToUser.invokeAsync();
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
  @Get("/users/{userId}")
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


  /**
   * This is gives access to the email state.
   */
  @Get("/emails/{address}")
  public CompletionStage<EmailInfo> getEmailInfo(String address) {
    return
      client.forKeyValueEntity(address)
        .method(UniqueEmailEntity::getState).invokeAsync()
        .thenApply(email -> {
          var emailInfo =
            new EmailInfo(
              email.address(),
              email.status().toString(),
              email.ownerId());

          logger.info("Getting email info: {}", emailInfo);
          return emailInfo;
        });
  }

  @Get("/users/by-country/{country}")
  public CompletionStage<UsersByCountryView.UserList> getUsersByCountry(String country) {
    return
      client.forView()
        .method(UsersByCountryView::getUserByCountry)
        .invokeAsync(new UsersByCountryView.QueryParameters(country));
  }
}

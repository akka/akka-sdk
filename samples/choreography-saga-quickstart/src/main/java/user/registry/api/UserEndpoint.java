package user.registry.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.application.UniqueEmailEntity;
import user.registry.application.UserEntity;
import user.registry.application.UsersByCountryView;
import user.registry.domain.UniqueEmail;
import user.registry.domain.User;

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
   * External API representation of a user record
   */
  public record User(String name, String country, String email) {}

  public record ChangeEmail(String newEmail) {}

  /**
   * External API representation of an email record
   */
  public record EmailInfo(String address, String status, Optional<String> ownerId) {}

  /**
   * External API representation of a User
   */
  public record UserInfo(String id, String name, String country, String email) {}

  /**
   * This is the main entry point for creating a new user.
   * <p>
   * Before creating a User, we need to reserve the email address to ensure that it is not already used.
   * The call will fail if the email address is already reserved.
   * <p>
   * If we succeed in reserving the email address, we move forward and create the user.
   */
  @Post("/{userId}")
  public HttpResponse createUser(String userId, User user) {
    var createUniqueEmail = new UniqueEmail.ReserveEmail(user.email(), userId);

    // try reserving the email address
    // we want to execute this call in other to check its result
    // and decide if we can continue with the user creation
    reserveEmail(userId, user.email());

    // on successful email reservation, we create the user and return the result
    logger.info("Creating user '{}'", userId);
    var createResult = client
      .forEventSourcedEntity(userId)
      .method(UserEntity::createUser)
      .invoke(new UserEntity.Create(user.name(), user.country(), user.email()));

    return switch (createResult) {
      case UserEntity.Result.Success s -> HttpResponses.ok();
      case UserEntity.Result.InvalidCommand error -> {
        String msg = "Couldn't create user: " + error.msg();
        logger.info(msg);
        throw HttpException.badRequest(msg);
      }
    };
  }

  @Put("/{userId}/email")
  public HttpResponse changeEmail(String userId, ChangeEmail cmd) {
    var createUniqueEmail = new UniqueEmail.ReserveEmail(cmd.newEmail(), userId);

    // try reserving the email address
    // we want to execute this call in other to check its result
    // and decide if we can continue with the user creation
    reserveEmail(userId, cmd.newEmail());

    // on successful email reservation, we change the user's email addreess
    logger.info("Changing user's address '{}'", userId);
    client
      .forEventSourcedEntity(userId)
      .method(UserEntity::changeEmail)
      .invoke(new UserEntity.ChangeEmail(cmd.newEmail()));

    return HttpResponses.ok();
  }

  private void reserveEmail(String userId, String emailAddress) {
    var createUniqueEmail = new UniqueEmail.ReserveEmail(emailAddress, userId);

    logger.info("Reserving new address '{}'", emailAddress);
    var emailReserved = client
      .forKeyValueEntity(emailAddress)
      .method(UniqueEmailEntity::reserve)
      .invoke(createUniqueEmail);

    if (emailReserved instanceof UniqueEmailEntity.Result.AlreadyReserved e) {
      logger.info("Email is already reserved '{}'", emailAddress);
      throw HttpException.badRequest("Email is already reserved '" + emailAddress + "'");
    }
  }

  /**
   * This is gives access to the user state.
   */
  @Get("/{userId}")
  public UserInfo getUserInfo(String userId) {
    var user = client.forEventSourcedEntity(userId).method(UserEntity::getState).invoke();
    var userInfo = new UserInfo(userId, user.name(), user.country(), user.email());
    logger.info("Getting user info: {}", userInfo);
    return userInfo;
  }

  @Get("/by-country/{country}")
  public UsersByCountryView.UserEntries getUsersByCountry(String country) {
    return client.forView().method(UsersByCountryView::getUserByCountry).invoke(country);
  }
}

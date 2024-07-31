package user.registry.views;

import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.domain.UserEvent;
import user.registry.domain.UserEvent.EmailAssigned;
import user.registry.domain.UserEvent.UserWasCreated;
import user.registry.entity.UserEntity;

import java.util.List;

/**
 * A View to query users by country.
 */
@ComponentId("view-users-by-newCountry")
@Consume.FromEventSourcedEntity(value = UserEntity.class)
public class UsersByCountryView extends View<UsersByCountryView.UserView> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public record UserView(String id, String name, String country, String email) {
    public UserView withEmail(String email) {
      return new UserView(id, name, country, email);
    }
  }

  public record UserList(List<UserView> users) {
  }

  public record QueryParameters(String country) {
  }

  @Query("SELECT * AS users FROM users_by_country WHERE country = :country")
  public UserList getUserByCountry(QueryParameters params) {
    return null;
  }

  public Effect<UserView> onEvent(UserEvent evt) {
    return switch (evt) {
      case UserWasCreated created -> {
        logger.info("User was created: {}", created);
        var currentId = updateContext().eventSubject().orElseThrow();
        yield effects().updateState(new UserView(currentId, created.name(), created.country(), created.email()));
      }
      case EmailAssigned emailAssigned -> {
        logger.info("User address changed: {}", emailAssigned);
        var updatedView = viewState().withEmail(emailAssigned.newEmail());
        yield effects().updateState(updatedView);
      }
      default ->
        effects().ignore();
    };
  }
}

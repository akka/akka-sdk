package user.registry.views;

import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;
import akka.platform.javasdk.view.TableUpdater;
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
public class UsersByCountryView extends View {

  private static Logger logger = LoggerFactory.getLogger(UsersByCountryView.class);

  @Consume.FromEventSourcedEntity(value = UserEntity.class)
  public static class UsersByCountry extends TableUpdater<UserView> {
    public Effect<UserView> onEvent(UserEvent evt) {
      return switch (evt) {
        case UserWasCreated created -> {
          logger.info("User was created: {}", created);
          var currentId = updateContext().eventSubject().orElseThrow();
          yield effects().updateRow(new UserView(currentId, created.name(), created.country(), created.email()));
        }
        case EmailAssigned emailAssigned -> {
          logger.info("User address changed: {}", emailAssigned);
          var updatedView = rowState().withEmail(emailAssigned.newEmail());
          yield effects().updateRow(updatedView);
        }
        default ->
            effects().ignore();
      };
    }
  }

  public record UserView(String id, String name, String country, String email) {
    public UserView withEmail(String email) {
      return new UserView(id, name, country, email);
    }
  }

  public record UserList(List<UserView> users) { }

  public record QueryParameters(String country) { }

  @Query("SELECT * AS users FROM users_by_country WHERE country = :country")
  public QueryEffect<UserList> getUserByCountry(QueryParameters params) {
    return queryResult();
  }

}

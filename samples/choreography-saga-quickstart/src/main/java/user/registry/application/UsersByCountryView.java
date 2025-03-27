package user.registry.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.domain.UserEvent;
import user.registry.domain.UserEvent.EmailAssigned;
import user.registry.domain.UserEvent.UserWasCreated;

import java.util.List;

/**
 * A View to query users by country.
 */
@ComponentId("users-by-country")
public class UsersByCountryView extends View {

  private static Logger logger = LoggerFactory.getLogger(UsersByCountryView.class);

  @Consume.FromEventSourcedEntity(value = UserEntity.class)
  public static class UsersByCountryUpdater extends TableUpdater<UserEntry> {
    public Effect<UserEntry> onEvent(UserEvent evt) {
      return switch (evt) {
        case UserWasCreated created -> {
          logger.info("User was created: {}", created);
          var currentId = updateContext().eventSubject().orElseThrow();
          yield effects().updateRow(new UserEntry(currentId, created.name(), created.country(), created.email()));
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

  public record UserEntry(String id, String name, String country, String email) {
    public UserEntry withEmail(String email) {
      return new UserEntry(id, name, country, email);
    }
  }

  public record UserEntries(List<UserEntry> users) { }

  @Query("SELECT * AS users FROM users_by_country WHERE country = :country")
  public QueryEffect<UserEntries> getUserByCountry(String country) {
    return queryResult();
  }

}

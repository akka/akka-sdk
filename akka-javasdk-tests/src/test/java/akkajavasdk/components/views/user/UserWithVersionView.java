/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.user;

import akka.javasdk.view.TableUpdater;
import akka.javasdk.annotations.DeleteHandler;
import akkajavasdk.components.keyvalueentities.user.User;
import akkajavasdk.components.keyvalueentities.user.UserEntity;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("user_view")
public class UserWithVersionView extends View {

  private static final Logger logger = LoggerFactory.getLogger(UserWithVersionView.class);

  @Consume.FromKeyValueEntity(UserEntity.class)
  public static class Users extends TableUpdater<UserWithVersion> {

    public Effect<UserWithVersion> onChange(User user) {
      if (rowState() == null) return effects().updateRow(new UserWithVersion(user.email, 1));
      else return effects().updateRow(new UserWithVersion(user.email, rowState().version + 1));
    }

    @DeleteHandler
    public Effect<UserWithVersion> onDelete() {
      logger.info("Deleting user with email={}", rowState().email);
      return effects().deleteRow();
    }
  }

  public record QueryParameters(String email) {}

  public static QueryParameters queryParam(String email) {
    return new QueryParameters(email);
  }

  @Query("SELECT * FROM users WHERE email = :email")
  public QueryEffect<UserWithVersion> getUser(QueryParameters params) {
    return queryResult();
  }

}

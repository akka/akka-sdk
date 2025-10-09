/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.user;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.keyvalueentities.user.User;
import akkajavasdk.components.keyvalueentities.user.UserEntity;
import java.util.List;

@Component(id = "users_by_name")
public class UsersByName extends View {

  @Consume.FromKeyValueEntity(value = UserEntity.class)
  public static class Users extends TableUpdater<User> {
    @DeleteHandler
    public Effect<User> onDelete() {
      return effects().deleteRow();
    }
  }

  public record QueryParameters(String name) {}

  public record UserList(List<User> users) {}

  @Query("SELECT * AS users FROM users WHERE name = :name")
  public QueryEffect<UserList> getUsers(QueryParameters params) {
    return queryResult();
  }
}

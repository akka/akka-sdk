/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.user;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.keyvalueentities.user.User;
import akkajavasdk.components.keyvalueentities.user.UserEntity;
import java.util.List;

@Component(id = "users_by_primitives")
public class UsersByPrimitives extends View {

  @Consume.FromKeyValueEntity(UserEntity.class)
  public static class Users extends TableUpdater<UserWithByPrimitivesModel> {
    public Effect<UserWithByPrimitivesModel> onChange(User user) {
      return effects()
          .updateRow(
              new UserWithByPrimitivesModel(
                  updateContext().eventSubject().orElse(""), user.email, 123, 321, 12.3d, true));
    }
  }

  public record UserWithByPrimitivesModel(
      String id,
      String email,
      int intValue,
      long longValue,
      double doubleValue,
      boolean booleanValue) {}

  public record UserByPrimitiveList(List<UserWithByPrimitivesModel> users) {}

  @Query("SELECT * AS users FROM users WHERE email = :email")
  public QueryEffect<UserByPrimitiveList> getUserByString(String email) {
    return queryResult();
  }

  @Query("SELECT * AS users FROM users WHERE intValue = :intValue")
  public QueryEffect<UserByPrimitiveList> getUserByInt(int intValue) {
    return queryResult();
  }

  @Query("SELECT * AS users FROM users WHERE longValue = :longValue")
  public QueryEffect<UserByPrimitiveList> getUserByLong(long longValue) {
    return queryResult();
  }

  @Query("SELECT * AS users FROM users WHERE doubleValue = :doubleValue")
  public QueryEffect<UserByPrimitiveList> getUserByDouble(double doubleValue) {
    return queryResult();
  }

  @Query("SELECT * AS users FROM users WHERE booleanValue = :booleanValue")
  public QueryEffect<UserByPrimitiveList> getUserByBoolean(boolean booleanValue) {
    return queryResult();
  }

  @Query("SELECT * AS users FROM users WHERE email = ANY(:emails)")
  public QueryEffect<UserByPrimitiveList> getUserByEmails(List<String> emails) {
    return queryResult();
  }
}

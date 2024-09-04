/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.view;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Table;
import akka.javasdk.view.View;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.testmodels.eventsourcedentity.Employee;
import akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent;
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EmployeeEntity;
import akka.javasdk.testmodels.keyvalueentity.AssignedCounter;
import akka.javasdk.testmodels.keyvalueentity.AssignedCounterState;
import akka.javasdk.testmodels.keyvalueentity.Counter;
import akka.javasdk.testmodels.keyvalueentity.CounterState;
import akka.javasdk.testmodels.keyvalueentity.TimeTrackerEntity;
import akka.javasdk.testmodels.keyvalueentity.User;
import akka.javasdk.testmodels.keyvalueentity.UserEntity;

import java.util.List;
import java.util.Optional;

public class ViewTestModels {

  // common query parameter for views in this file
  public record ByEmail(String email) {
  }


  @ComponentId("users_view")
  public static class UserByEmailWithGet extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Table("users")
  public static class ViewWithoutComponentIdAnnotation extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId(" ")
  public static class ViewWithEmptyComponentIdAnnotation extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class ViewWithNoTableUpdater extends View {

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }


  @ComponentId("users_view")
  @Table("users")
  public static class ViewWithTableName extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class WrongQueryReturnType extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public User getUser(ByEmail byEmail) {
      return null;
    }
  }

  @ComponentId("users_view")
  public static class WrongHandlerSignature extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {

      public Effect<User> onUpdate(User user, String extra) {
        return effects().updateRow(user);
      }
    }

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }


  @ComponentId("users_view")
  public static class TransformedUserView extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class TransformedUserUpdater extends TableUpdater<TransformedUser> {
      public Effect<TransformedUser> onChange(User user) {
        return effects()
            .updateRow(new TransformedUser(user.lastName + ", " + user.firstName, user.email));
      }
    }

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<Optional<TransformedUser>> getUser(String email) {
      return queryResult();
    }

    public record TransformedUsers(List<TransformedUser> users) {}

    @Query("SELECT * as users FROM users WHERE email = :emails")
    public QueryEffect<TransformedUsers> getUsersByEmails(List<String> emails) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class TransformedUserViewWithDeletes extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class TransformedUserUpdater extends TableUpdater<TransformedUser> {

      public Effect<TransformedUser> onChange(User user) {
        return effects()
            .updateRow(new TransformedUser(user.lastName + ", " + user.firstName, user.email));
      }

      @DeleteHandler
      public Effect<TransformedUser> onDelete() {
        return effects().deleteRow();
      }
    }

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<TransformedUser>  getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class UserViewWithOnlyDeleteHandler extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class TransformedUserUpdater extends TableUpdater<User> {

      @DeleteHandler
      public Effect<User> onDelete() {
        return effects().deleteRow();
      }
    }

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<TransformedUser>  getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class TransformedUserViewWithMethodLevelJWT extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class TransformedUserUpdater extends TableUpdater<TransformedUser> {

      public Effect<TransformedUser> onChange(User user) {
        return effects()
            .updateRow(new TransformedUser(user.lastName + ", " + user.firstName, user.email));
      }
    }

    @Query("SELECT * FROM users WHERE email = :email")
    @JWT(
        validate = JWT.JwtMethodMode.BEARER_TOKEN,
        bearerTokenIssuer = {"a", "b"},
        staticClaims = {
            @JWT.StaticClaim(claim = "role", value = "admin"),
            @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io")
        })
    public QueryEffect<TransformedUser> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  @JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuer = {"a", "b"},
    staticClaims = {
        @JWT.StaticClaim(claim = "role", value = "admin"),
        @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io")
    })
  public static class ViewWithServiceLevelJWT extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class ViewWithoutSubscription extends View {

    public static class UserUpdater extends TableUpdater<TransformedUser> {
    }
    
    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<TransformedUser> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class ViewDuplicatedHandleDeletesAnnotations extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class TransformedUserUpdater extends TableUpdater<TransformedUser> {
      public Effect<TransformedUser> onChange(User user) {
        return effects()
            .updateRow(new TransformedUser(user.lastName + ", " + user.firstName, user.email));
      }

      @DeleteHandler
      public Effect<TransformedUser> onDelete() {
        return effects().deleteRow();
      }

      @DeleteHandler
      public Effect<TransformedUser> onDelete2() {
        return effects().deleteRow();
      }
    }

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<TransformedUser> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class ViewHandleDeletesWithParam extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class TransformedUserUpdater extends TableUpdater<TransformedUser> {
      public Effect<TransformedUser> onChange(User user) {
        return effects()
            .updateRow(new TransformedUser(user.lastName + ", " + user.firstName, user.email));
      }

      @DeleteHandler
      public Effect<TransformedUser> onDelete(User user) {
        return effects().deleteRow();
      }
    }

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<TransformedUser> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class ViewWithNoQuery extends View {}

  @ComponentId("users_view")
  public static class ViewWithTwoQueries extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UsersTable extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUserByEmail(ByEmail byEmail) {
      return queryResult();
    }

    @Query("SELECT * FROM users WHERE email = :email AND name = :name")
    public QueryEffect<User> getUserByNameAndEmail(ByNameAndEmail byEmail) {
      return queryResult();
    }
  }



  @ComponentId("employees_view")
  public static class SubscribeToEventSourcedEvents extends View {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public static class Employees extends TableUpdater<Employee> {

      public Effect<Employee> onCreated(EmployeeEvent.EmployeeCreated created) {
        return effects()
            .updateRow(new Employee(created.firstName, created.lastName, created.email));
      }

      public Effect<Employee> onUpdated(EmployeeEvent.EmployeeEmailUpdated updated) {
        return effects().ignore();
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("employees_view")
  public static class SubscribeToSealedEventSourcedEvents extends View {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public static class Employees extends TableUpdater<Employee> {

      public Effect<Employee> handle(EmployeeEvent event) {
        return switch (event) {
          case EmployeeEvent.EmployeeCreated created -> effects()
              .updateRow(new Employee(created.firstName, created.lastName, created.email));
          case EmployeeEvent.EmployeeEmailUpdated updated -> effects().ignore();
        };
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class SubscribeToEventSourcedWithMissingHandler extends View {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public static class Employees extends TableUpdater<Employee> {
      public Effect<Employee> onCreated(EmployeeEvent.EmployeeCreated created) {
        return effects()
            .updateRow(new Employee(created.firstName, created.lastName, created.email));
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }


  @ComponentId("users_view")
  public static class TypeLevelSubscribeToEventSourcedEventsWithMissingHandler extends View {

    @Consume.FromEventSourcedEntity(value = EmployeeEntity.class, ignoreUnknown = false)
    public static class Employees extends TableUpdater<Employee> {
      public Effect<Employee> onEvent(EmployeeEvent.EmployeeCreated created) {
        return effects()
            .updateRow(new Employee(created.firstName, created.lastName, created.email));
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("employees_view")
  @Acl(allow = @Acl.Matcher(service = "test"))
  public static class ViewWithServiceLevelAcl extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class Users extends TableUpdater<User> { }

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("employees_view")
  public static class ViewWithMethodLevelAcl extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class Users extends TableUpdater<User> {
    }

    @Query("SELECT * FROM users WHERE email = :email")
    @Acl(allow = @Acl.Matcher(service = "test"))
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("users_view")
  public static class UserByEmailWithCollectionReturn extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UsersTable extends TableUpdater<User> { }

    @Query(value = "SELECT * AS users FROM users WHERE name = :name")
    public QueryEffect<UserCollection> getUser(ByEmail name) {
      return queryResult();
    }
  }


  @ComponentId("users_view")
  public static class MultiTableViewValidation extends View {
    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class ViewTableWithoutTableAnnotation extends TableUpdater<User> {}

    @Table(" ")
    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class ViewTableWithEmptyTableAnnotation extends TableUpdater<User> {}

    @Consume.FromKeyValueEntity(UserEntity.class) // both here
    @Table("users")
    public static class ViewTableWithMixedLevelSubscriptions extends TableUpdater<TransformedUser> {
      public Effect<TransformedUser> onChange(User user) {
        return effects()
            .updateRow(new TransformedUser(user.lastName + ", " + user.firstName, user.email));
      }
    }
  }

  @ComponentId("multi-table-view-without-query")
  public static class MultiTableViewWithoutQuery extends View {
    @Table("users")
    public static class Users extends TableUpdater<User> {}
  }

  @ComponentId("multi-table-view-with-multiple-queries")
  public static class MultiTableViewWithMultipleQueries extends View {
    @Query("SELECT * FROM users")
    public QueryEffect<User> query1() {
      return queryResult();
    }

    @Query("SELECT * FROM users")
    public QueryEffect<User> query2() {
      return queryResult();
    }

    @Table("users")
    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class Users extends TableUpdater<User> {}
  }

  @ComponentId("multi-table-view-with-join-query")
  public static class MultiTableViewWithJoinQuery extends View {

    @Query("""
      SELECT employees.*, counters.* as counters
      FROM employees
      JOIN assigned ON assigned.assigneeId = employees.email
      JOIN counters ON assigned.counterId = counters.id
      WHERE employees.email = :email
      """)
    public QueryEffect<EmployeeCounters> get(ByEmail byEmail) {
      return queryResult();
    }

    @Table("employees")
    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public static class Employees extends TableUpdater<Employee> {
      public Effect<Employee> onCreated(EmployeeEvent.EmployeeCreated created) {
        return effects()
            .updateRow(new Employee(created.firstName, created.lastName, created.email));
      }

      public Effect<Employee> onUpdated(EmployeeEvent.EmployeeEmailUpdated updated) {
        return effects().ignore();
      }
    }

    @Table("counters")
    @Consume.FromKeyValueEntity(Counter.class)
    public static class Counters extends TableUpdater<CounterState> {}

    @Table("assigned")
    @Consume.FromKeyValueEntity(AssignedCounter.class)
    public static class Assigned extends TableUpdater<AssignedCounterState> {}
  }

  @ComponentId("multi-table-view-with-join-query")
  public static class MultiTableViewWithDuplicatedVESubscriptions extends View {

    @Query("SELECT * FROM employees")
    public QueryEffect<EmployeeCounters> get(ByEmail byEmail) {
      return queryResult();
    }

    @Table("employees")
    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public static class Employees extends TableUpdater<Employee> {
      public Effect<Employee> onEvent(EmployeeEvent.EmployeeCreated created) {
        return effects()
          .updateRow(new Employee(created.firstName, created.lastName, created.email));
      }
    }

    @Table("counters")
    @Consume.FromKeyValueEntity(Counter.class)
    public static class Counters extends TableUpdater<CounterState> {}

    @Table("assigned")
    @Consume.FromKeyValueEntity(Counter.class)
    public static class Assigned extends TableUpdater<CounterState> {
      public Effect<CounterState> onEvent(CounterState counterState) {
        return effects().ignore();
      }

      public Effect<CounterState> onEvent2(CounterState counterState) {
        return effects().ignore();
      }
    }
  }

  @ComponentId("multi-table-view-with-join-query")
  public static class MultiTableViewWithDuplicatedESSubscriptions extends View {

    @Query("SELECT * FROM users")
    public QueryEffect<EmployeeCounters> get(ByEmail byEmail) {
      return queryResult();
    }

    @Table("employees")
    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public static class Employees extends TableUpdater<Employee> {
      public Effect<Employee> onEvent(EmployeeEvent.EmployeeCreated created) {
        return effects()
          .updateRow(new Employee(created.firstName, created.lastName, created.email));
      }
    }

    @Table("counters")
    @Consume.FromKeyValueEntity(Counter.class)
    public static class Counters extends TableUpdater<CounterState> {}

    @Table("assigned")
    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public static class Assigned extends TableUpdater<Employee> {
      public Effect<Employee> onEvent(CounterState counterState) {
        return effects().ignore();
      }

      public Effect<Employee> onEvent2(CounterState counterState) {
        return effects().ignore();
      }
    }
  }

  @ComponentId("time-tracker-view")
  public static class TimeTrackerView extends View {

    @Consume.FromKeyValueEntity(TimeTrackerEntity.class)
    public static class TimeTrackers extends TableUpdater<TimeTrackerEntity.TimerState> { }

    @Query(value = "SELECT * FROM time_trackers WHERE name = :name")
    public QueryEffect<TimeTrackerEntity.TimerState> query2() {
      return queryResult();
    }
  }


  @ComponentId("employee_view")
  public static class TopicTypeLevelSubscriptionView extends View {

    @Consume.FromTopic(value = "source", consumerGroup = "cg")
    public static class Employees extends TableUpdater<Employee> {

      public Effect<Employee> onCreate(EmployeeEvent.EmployeeCreated evt) {
        return effects()
            .updateRow(new Employee(evt.firstName, evt.lastName, evt.email));
      }

      public Effect<Employee> onEmailUpdate(EmployeeEvent.EmployeeEmailUpdated eeu) {
        var employee = rowState();
        return effects().updateRow(new Employee(employee.firstName(), employee.lastName(), eeu.email));
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @ComponentId("employee_view")
  public static class TopicSubscriptionView extends View {

    @Consume.FromTopic(value = "source", consumerGroup = "cg")
    public static class Employees extends TableUpdater<Employee> {

      public Effect<Employee> onCreate(EmployeeEvent.EmployeeCreated evt) {
        return effects()
            .updateRow(new Employee(evt.firstName, evt.lastName, evt.email));
      }

      public Effect<Employee> onEmailUpdate(EmployeeEvent.EmployeeEmailUpdated eeu) {
        var employee = rowState();
        return effects().updateRow(new Employee(employee.firstName(), employee.lastName(), eeu.email));
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }
}

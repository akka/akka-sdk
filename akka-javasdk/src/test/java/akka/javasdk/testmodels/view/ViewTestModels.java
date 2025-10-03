/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.view;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Table;
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
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ViewTestModels {

  public record EveryType(
      int intValue,
      long longValue,
      float floatValue,
      double doubleValue,
      boolean booleanValue,
      String stringValue,
      Integer wrappedInt,
      Long wrappedLong,
      Float wrappedFloat,
      Double wrappedDouble,
      Boolean wrappedBoolean,
      Instant instant,
      Byte[] bytes,
      Optional<String> optionalString,
      List<String> repeatedString,
      ByEmail nestedMessage,
      AnEnum anEnum) {}

  public enum AnEnum {
    ONE,
    TWO,
    THREE
  }

  // common query parameter for views in this file
  public record ByEmail(String email) {}

  public record Recursive(String id, Recursive child) {}

  public record TwoStepRecursive(TwoStepRecursiveChild child) {}

  public record TwoStepRecursiveChild(TwoStepRecursive recursive) {}

  @Component(id = "users_view")
  public static class UserByEmailWithGet extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Table("users")
  public static class ViewWithoutComponentAnnotation extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = " ")
  public static class ViewWithEmptyComponentAnnotation extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }

  @Component(id = "oh|my|pipe")
  public static class ViewWithPipeyComponentAnnotation extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }

  @Component(id = "view_query_with_too_many_arguments")
  public static class ViewQueryWithTooManyArguments extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(String email, String sthElse) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class ViewWithLowerCaseQuery extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("select * from users where email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class ViewWithQuotedTableName extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM `üsérs tåble` WHERE email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class ViewWithNoTableUpdater extends View {

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  @Table("users")
  public static class ViewWithTableName extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class WrongQueryReturnType extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public User getUser(ByEmail byEmail) {
      return null;
    }
  }

  @Component(id = "users_view")
  public static class WrongQueryEffectReturnType extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<String> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
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

  @Component(id = "users_view")
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

  @Component(id = "users_view")
  public static class ViewWithInvalidRowType extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UserUpdater extends TableUpdater<String> {
      public Effect<String> onChange(User user) {
        return effects().updateRow(user.email);
      }
    }

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(String email) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
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
    public QueryEffect<TransformedUser> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class UserViewWithOnlyDeleteHandler extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class TransformedUserUpdater extends TableUpdater<User> {

      @DeleteHandler
      public Effect<User> onDelete() {
        return effects().deleteRow();
      }
    }

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<TransformedUser> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class UserViewWithoutTransformation extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class TransformedUserUpdater extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<TransformedUser> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class ViewWithoutSubscription extends View {

    public static class UserUpdater extends TableUpdater<TransformedUser> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<TransformedUser> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
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

  @Component(id = "users_view")
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

  @Component(id = "users_view")
  public static class ViewWithNoQuery extends View {}

  @Component(id = "users_view")
  public static class ViewWithTwoQueries extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UsersTable extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUserByEmail(ByEmail byEmail) {
      return queryResult();
    }

    @Query(value = "SELECT * FROM users WHERE email = :email AND name = :name")
    public QueryEffect<User> getUserByNameAndEmail(ByNameAndEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class ViewWithIncorrectQueries extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UsersTable extends TableUpdater<User> {}

    // stream updates but single-result effect
    @Query(value = "SELECT * FROM users WHERE email = :email", streamUpdates = true)
    public QueryEffect<User> getUserByEmail(ByEmail byEmail) {
      return queryResult();
    }

    @Query(value = "SELECT * FROM users WHERE email = :email AND name = :name")
    public QueryEffect<User> getUserByNameAndEmail(ByNameAndEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "employees_view")
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

  @Component(id = "employees_view")
  public static class SubscribeToSealedEventSourcedEvents extends View {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public static class Employees extends TableUpdater<Employee> {

      public Effect<Employee> handle(EmployeeEvent event) {
        return switch (event) {
          case EmployeeEvent.EmployeeCreated created ->
              effects().updateRow(new Employee(created.firstName, created.lastName, created.email));
          case EmployeeEvent.EmployeeEmailUpdated updated -> effects().ignore();
        };
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
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

  @Component(id = "users_view")
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

  @Component(id = "employees_view")
  @Acl(allow = @Acl.Matcher(service = "test"))
  public static class ViewWithServiceLevelAcl extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class Users extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "employees_view")
  public static class ViewWithMethodLevelAcl extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class Users extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    @Acl(allow = @Acl.Matcher(service = "test"))
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class UserByEmailWithCollectionReturn extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UsersTable extends TableUpdater<User> {}

    @Query(value = "SELECT * AS users FROM users WHERE name = :name")
    public QueryEffect<UserCollection> getUser(ByEmail name) {
      return queryResult();
    }
  }

  @Component(id = "users_view")
  public static class UserByEmailWithStreamReturn extends View {

    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UsersTable extends TableUpdater<User> {}

    @Query(value = "SELECT * AS users FROM users")
    public QueryStreamEffect<User> getAllUsers() {
      return queryStreamResult();
    }
  }

  @Component(id = "users_view")
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

  @Component(id = "multi-table-view-without-query")
  public static class MultiTableViewWithoutQuery extends View {
    @Table("users")
    public static class Users extends TableUpdater<User> {}
  }

  @Component(id = "multi-table-view-with-multiple-queries")
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

  @Component(id = "multi-table-view-with-join-query")
  public static class MultiTableViewWithJoinQuery extends View {

    @Query(
        """
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

  @Component(id = "multi-table-view-with-join-query")
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

  @Component(id = "multi-table-view-with-join-query")
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

  @Component(id = "time-tracker-view")
  public static class TimeTrackerView extends View {

    @Consume.FromKeyValueEntity(TimeTrackerEntity.class)
    public static class TimeTrackers extends TableUpdater<TimeTrackerEntity.TimerState> {}

    @Query(value = "SELECT * FROM time_trackers WHERE name = :name")
    public QueryEffect<TimeTrackerEntity.TimerState> query2() {
      return queryResult();
    }
  }

  @Component(id = "employee_view")
  public static class TopicTypeLevelSubscriptionView extends View {

    @Consume.FromTopic(value = "source", consumerGroup = "cg")
    public static class Employees extends TableUpdater<Employee> {

      public Effect<Employee> onCreate(EmployeeEvent.EmployeeCreated evt) {
        return effects().updateRow(new Employee(evt.firstName, evt.lastName, evt.email));
      }

      public Effect<Employee> onEmailUpdate(EmployeeEvent.EmployeeEmailUpdated eeu) {
        var employee = rowState();
        return effects()
            .updateRow(new Employee(employee.firstName(), employee.lastName(), eeu.email));
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  public record ById(String id) {}

  @Component(id = "recursive_view")
  public static class RecursiveViewStateView extends View {
    @Consume.FromTopic(value = "recursivetopic")
    public static class Events extends TableUpdater<Recursive> {}

    @Query("SELECT * FROM events WHERE id = :id")
    public QueryEffect<Employee> getEmployeeByEmail(ById id) {
      return queryResult();
    }
  }

  @Component(id = "all_the_field_types_view")
  public static class AllTheFieldTypesView extends View {
    @Consume.FromTopic(value = "allthetypestopic")
    public static class Events extends TableUpdater<EveryType> {}

    @Query("SELECT * FROM rows")
    public QueryStreamEffect<Employee> allRows() {
      return queryStreamResult();
    }
  }
}

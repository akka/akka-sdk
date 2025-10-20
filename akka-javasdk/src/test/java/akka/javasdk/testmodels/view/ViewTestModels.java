/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.view;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.FunctionTool;
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
import java.math.BigDecimal;
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
      AnEnum anEnum,
      BigDecimal bigDecimal) {}

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

  // Test models for @FunctionTool validation

  @Component(id = "view_with_function_tool_on_stream")
  public static class ViewWithFunctionToolOnStreamQuery extends View {
    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UsersTable extends TableUpdater<User> {}

    @Query(value = "SELECT * FROM users", streamUpdates = true)
    @FunctionTool(description = "Get all users as stream")
    public QueryStreamEffect<User> getAllUsers() {
      return queryStreamResult();
    }
  }

  @Component(id = "view_with_function_tool_on_non_query")
  public static class ViewWithFunctionToolOnNonQueryMethod extends View {
    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UsersTable extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    public QueryEffect<User> getUser(ByEmail byEmail) {
      return queryResult();
    }

    @FunctionTool(description = "Helper method")
    public String helperMethod() {
      return "helper";
    }
  }

  @Component(id = "view_with_valid_function_tool")
  public static class ViewWithValidFunctionTool extends View {
    @Consume.FromKeyValueEntity(UserEntity.class)
    public static class UsersTable extends TableUpdater<User> {}

    @Query("SELECT * FROM users WHERE email = :email")
    @FunctionTool(description = "Get user by email")
    public QueryEffect<User> getUser(ByEmail byEmail) {
      return queryResult();
    }
  }
}

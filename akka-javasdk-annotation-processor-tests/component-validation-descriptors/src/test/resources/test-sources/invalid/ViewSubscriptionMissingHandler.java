/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-subscription-missing-handler")
public class ViewSubscriptionMissingHandler extends View {

  @Query("SELECT * FROM employees")
  public QueryEffect<Employee> getEmployees() {
    return null;
  }

  public static class Employee {
    public String firstName;
    public String lastName;
    public String email;
  }

  @Consume.FromTopic("employees-topic")
  public static class Employees extends TableUpdater<Employee> {
    // Only handles EmployeeCreated, missing handler for EmployeeUpdated
    public Effect<Employee> onCreated(EmployeeCreated created) {
      return effects().updateRow(new Employee());
    }
  }

  public static class EmployeeCreated {
    public String firstName;
    public String lastName;
    public String email;
  }

  public static class EmployeeUpdated {
    public String email;
  }
}

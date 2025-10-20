/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.eventsourcedentity;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

public class EventSourcedEntitiesTestModels {

  @Component(id = "employee")
  public static class EmployeeEntity extends EventSourcedEntity<Employee, EmployeeEvent> {

    public Effect<String> createUser(CreateEmployee create) {
      return effects()
          .persist(
              new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
          .thenReply(__ -> "ok");
    }

    public Employee applyEvent(EmployeeEvent event) {
      EmployeeEvent.EmployeeCreated create = (EmployeeEvent.EmployeeCreated) event;
      return new Employee(create.firstName, create.lastName, create.email);
    }
  }
}

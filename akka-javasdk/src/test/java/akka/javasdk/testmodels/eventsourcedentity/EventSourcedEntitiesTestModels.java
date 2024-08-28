/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.eventsourcedentity;

import akka.javasdk.JsonMigration;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Migration;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.List;

public class EventSourcedEntitiesTestModels {

    public sealed interface CounterEvent {
        record IncrementCounter(int value) implements CounterEvent {
        }
        record DecrementCounter(int value) implements CounterEvent {
        }
    }

    @ComponentId("employee")
    public static class EmployeeEntity extends EventSourcedEntity<Employee, EmployeeEvent> {

        public Effect<String> createUser(CreateEmployee create) {
            return effects()
                .persist(new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
                .thenReply(__ -> "ok");
        }

        public Employee applyEvent(EmployeeEvent event) {
            EmployeeEvent.EmployeeCreated create = (EmployeeEvent.EmployeeCreated) event;
            return new Employee(create.firstName, create.lastName, create.email);
        }
    }

    @ComponentId("counter-entity")
    public static class CounterEventSourcedEntity extends EventSourcedEntity<Integer, CounterEvent> {

        @Migration(EventMigration.class)
        public record Event(String s) {
        }

        public static class EventMigration extends JsonMigration {

            public EventMigration() {
            }

            @Override
            public int currentVersion() {
                return 1;
            }

            @Override
            public List<String> supportedClassNames() {
                return List.of("additional-mapping");
            }
        }

        public ReadOnlyEffect<Integer> getInteger() {
            return effects().reply(currentState());
        }

        public Effect<Integer> changeInteger(Integer number) {
            if (number == 0) {
                return effects().reply(currentState());
            } else if (number < 0) {
                return effects().persist(new CounterEvent.DecrementCounter(number)).thenReply(newValue -> newValue);
            } else {
                return effects().persist(new CounterEvent.IncrementCounter(number)).thenReply(newValue -> newValue);
            }
        }

        @Override
        public Integer applyEvent(CounterEvent event) {
            return 0;
        }
    }



    @ComponentId("counter")
    public static class CounterEventSourcedEntityWithMethodLevelJWT extends EventSourcedEntity<Integer, CounterEvent> {

        @JWT(
            validate = JWT.JwtMethodMode.BEARER_TOKEN,
            bearerTokenIssuer = {"a", "b"})
        public ReadOnlyEffect<Integer> getInteger() {
            return effects().reply(currentState());
        }

        @JWT(
            validate = JWT.JwtMethodMode.BEARER_TOKEN,
            bearerTokenIssuer = {"c", "d"},
            staticClaims = {
                @JWT.StaticClaim(claim = "role", value = "method-admin"),
                @JWT.StaticClaim(claim = "aud", value = "${ENV}")
            })
        public ReadOnlyEffect<Integer> changeInteger(Integer number) {
            return effects().reply(number);
        }

        @Override
        public Integer applyEvent(CounterEvent event) {
            return 0;
        }
    }

    @ComponentId("counter")
    @JWT(
        validate = JWT.JwtMethodMode.BEARER_TOKEN,
        bearerTokenIssuer = {"a", "b"},
        staticClaims = {
            @JWT.StaticClaim(claim = "role", value = "admin"),
            @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io")
        })
    public static class CounterEventSourcedEntityWithServiceLevelJWT extends EventSourcedEntity<Integer, CounterEvent> {

        public ReadOnlyEffect<Integer> getInteger() {
            return effects().reply(currentState());
        }

        public ReadOnlyEffect<Integer> changeInteger(Integer number) {
            return effects().reply(number);
        }

        @Override
        public Integer applyEvent(CounterEvent event) {
            return 0;
        }
    }



    @ComponentId("counter")
    @Acl(allow = @Acl.Matcher(service = "test"))
    public static class EventSourcedEntityWithServiceLevelAcl extends EventSourcedEntity<Employee, EmployeeEvent> {


        @Override
        public Employee applyEvent(EmployeeEvent event) {
            return null;
        }
    }


    @ComponentId("counter")
    public static class EventSourcedEntityWithMethodLevelAcl extends EventSourcedEntity<Employee, EmployeeEvent> {

        @Acl(allow = @Acl.Matcher(service = "test"))
        public Effect<String> createUser(CreateEmployee create) {
            return effects()
                .persist(new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
                .thenReply(__ -> "ok");
        }

        @Override
        public Employee applyEvent(EmployeeEvent event) {
            return null;
        }
    }

    @ComponentId("counter")
    public static class InvalidEventSourcedEntityWithOverloadedCommandHandler extends EventSourcedEntity<Employee, EmployeeEvent> {

        public Effect<String> createUser(CreateEmployee create) {
            return effects()
                    .persist(new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
                    .thenReply(__ -> "ok");
        }

        public Effect<String> createUser(String email) {
            return effects()
                    .persist(new EmployeeEvent.EmployeeCreated("John", "Doe", email))
                    .thenReply(__ -> "ok");
        }

        @Override
        public Employee applyEvent(EmployeeEvent event) {
            return null;
        }
    }
}

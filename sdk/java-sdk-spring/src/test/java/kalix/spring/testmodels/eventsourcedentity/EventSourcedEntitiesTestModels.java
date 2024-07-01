/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.testmodels.eventsourcedentity;

import kalix.javasdk.JsonMigration;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.Migration;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.JWT;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.List;

public class EventSourcedEntitiesTestModels {

    public sealed interface CounterEvent {
        record IncrementCounter(int value) implements CounterEvent {
        }
        record DecrementCounter(int value) implements CounterEvent {
        }
    }

    @TypeId("employee")
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

    @TypeId("counter-entity")
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

        public Effect<Integer> getInteger() {
            return effects().reply(currentState());
        }

        public Effect<Integer> changeInteger(Integer number) {
            return effects().reply(number);
        }

        @Override
        public Integer applyEvent(CounterEvent event) {
            return 0;
        }
    }



    @TypeId("counter")
    public static class CounterEventSourcedEntityWithMethodLevelJWT extends EventSourcedEntity<Integer, CounterEvent> {

        @JWT(
            validate = JWT.JwtMethodMode.BEARER_TOKEN,
            bearerTokenIssuer = {"a", "b"})
        public Effect<Integer> getInteger() {
            return effects().reply(currentState());
        }

        @JWT(
            validate = JWT.JwtMethodMode.BEARER_TOKEN,
            bearerTokenIssuer = {"c", "d"},
            staticClaims = {
                @JWT.StaticClaim(claim = "role", value = "method-admin"),
                @JWT.StaticClaim(claim = "aud", value = "${ENV}")
            })
        public Effect<Integer> changeInteger(Integer number) {
            return effects().reply(number);
        }

        @Override
        public Integer applyEvent(CounterEvent event) {
            return 0;
        }
    }

    @TypeId("counter")
    @JWT(
        validate = JWT.JwtMethodMode.BEARER_TOKEN,
        bearerTokenIssuer = {"a", "b"},
        staticClaims = {
            @JWT.StaticClaim(claim = "role", value = "admin"),
            @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io")
        })
    public static class CounterEventSourcedEntityWithServiceLevelJWT extends EventSourcedEntity<Integer, CounterEvent> {

        public Effect<Integer> getInteger() {
            return effects().reply(currentState());
        }

        public Effect<Integer> changeInteger(Integer number) {
            return effects().reply(number);
        }

        @Override
        public Integer applyEvent(CounterEvent event) {
            return 0;
        }
    }



    @TypeId("counter")
    @Acl(allow = @Acl.Matcher(service = "test"))
    public static class EventSourcedEntityWithServiceLevelAcl extends EventSourcedEntity<Employee, EmployeeEvent> {


        @Override
        public Employee applyEvent(EmployeeEvent event) {
            return null;
        }
    }


    @TypeId("counter")
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

    @TypeId("counter")
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

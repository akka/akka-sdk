/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.testmodels.eventsourcedentity;

import kalix.javasdk.JsonMigration;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Migration;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.JWT;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.List;

public class EventSourcedEntitiesTestModels {

    @TypeId("employee")
    public static class EmployeeEntity extends EventSourcedEntity<Employee, EmployeeEvent> {

        public Effect<String> createUser(CreateEmployee create) {
            return effects()
                .emitEvent(new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
                .thenReply(__ -> "ok");
        }

        @EventHandler
        public Employee onEvent(EmployeeEvent event) {
            EmployeeEvent.EmployeeCreated create = (EmployeeEvent.EmployeeCreated) event;
            return new Employee(create.firstName, create.lastName, create.email);
        }
    }

    @TypeId("counter-entity")
    public static class CounterEventSourcedEntity extends EventSourcedEntity<Integer, Object> {

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

        @EventHandler
        public Integer receiveStringEvent(Event event) {
            return 0;
        }

        @EventHandler
        public Integer receivedIntegerEvent(Integer event) {
            return 0;
        }

        public Integer publicMethodSimilarSignature(Integer event) {
            return 0;
        }

        private Integer privateMethodSimilarSignature(Integer event) {
            return 0;
        }
    }



    @TypeId("counter")
    public static class CounterEventSourcedEntityWithMethodLevelJWT extends EventSourcedEntity<Integer, Object> {

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
    }

    @TypeId("counter")
    @JWT(
        validate = JWT.JwtMethodMode.BEARER_TOKEN,
        bearerTokenIssuer = {"a", "b"},
        staticClaims = {
            @JWT.StaticClaim(claim = "role", value = "admin"),
            @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io")
        })
    public static class CounterEventSourcedEntityWithServiceLevelJWT extends EventSourcedEntity<Integer, Object> {

        public Effect<Integer> getInteger() {
            return effects().reply(currentState());
        }

        public Effect<Integer> changeInteger(Integer number) {
            return effects().reply(number);
        }
    }

    @TypeId("counter")
    public static class ErrorDuplicatedEventsEntity extends EventSourcedEntity<Integer, Object> {

        @EventHandler
        public Integer receiveStringEvent(String event) {
            return 0;
        }

        @EventHandler
        public Integer receivedIntegerEvent(Integer event) {
            return 0;
        }

        @EventHandler
        public Integer receivedIntegerEventDup(Integer event) {
            return 0;
        }
    }

    @TypeId("counter")
    public static class ErrorWrongSignaturesEntity extends EventSourcedEntity<Integer, Object> {

        @EventHandler
        public String receivedIntegerEvent(Integer event) {
            return "0";
        }

        @EventHandler
        public Integer receivedIntegerEventAndString(Integer event, String s1) {
            return 0;
        }
    }

    @TypeId("employee")
    public static class EmployeeEntityWithMissingHandler extends EventSourcedEntity<Employee, EmployeeEvent> {

        public Effect<String> createUser(CreateEmployee create) {
            return effects()
                .emitEvent(new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
                .thenReply(__ -> "ok");
        }

        @EventHandler
        public Employee onEvent(EmployeeEvent.EmployeeCreated created) {
            return new Employee(created.firstName, created.lastName, created.email);
        }
    }

    @TypeId("employee")
    public static class EmployeeEntityWithMixedHandlers extends EventSourcedEntity<Employee, EmployeeEvent> {

        public Effect<String> createUser(CreateEmployee create) {
            return effects()
                .emitEvent(new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
                .thenReply(__ -> "ok");
        }

        @EventHandler
        public Employee onEvent(EmployeeEvent event) {
            if (event instanceof EmployeeEvent.EmployeeCreated) {
                EmployeeEvent.EmployeeCreated created = (EmployeeEvent.EmployeeCreated) event;
                return new Employee(created.firstName, created.lastName, created.email);
            } else {
                return currentState();
            }
        }

        @EventHandler
        public Employee onEmployeeCreated(EmployeeEvent.EmployeeCreated created) {
            return new Employee(created.firstName, created.lastName, created.email);
        }
    }

    @TypeId("counter")
    @Acl(allow = @Acl.Matcher(service = "test"))
    public static class EventSourcedEntityWithServiceLevelAcl extends EventSourcedEntity<Integer, Object> {

    }


    @TypeId("counter")
    public static class EventSourcedEntityWithMethodLevelAcl extends EventSourcedEntity<Integer, Object> {
        @Acl(allow = @Acl.Matcher(service = "test"))
        public Effect<String> createUser(CreateEmployee create) {
            return effects()
                .emitEvent(new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
                .thenReply(__ -> "ok");
        }
    }
}

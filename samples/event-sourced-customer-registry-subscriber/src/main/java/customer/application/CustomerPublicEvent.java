package customer.application;

import akka.javasdk.annotations.TypeName;

public sealed interface CustomerPublicEvent {
  @TypeName("customer-created")
  record Created(String email, String name) implements CustomerPublicEvent {}

  @TypeName("name-changed")
  record NameChanged(String newName) implements CustomerPublicEvent {}
}

package user.registry.domain;

import akka.javasdk.annotations.TypeName;

/**
 * It's recommended to seal the event interface.
 * As such, the runtime can detect that there are event handlers defined for each event.
 */
public sealed interface UserEvent {
  @TypeName("user-created")
  record UserWasCreated(String name, String country, String email) implements UserEvent {
  }

  @TypeName("email-assigned")
  record EmailAssigned(String newEmail) implements UserEvent {
  }

  @TypeName("email-unassigned")
  record EmailUnassigned(String oldEmail) implements UserEvent {
  }
}

package user.registry.domain;

import user.registry.domain.UserEvent.EmailAssigned;
import user.registry.domain.UserEvent.EmailUnassigned;
import user.registry.domain.UserEvent.UserWasCreated;

import java.util.List;

public record User(String name, String country, String email) {

  // user commands
  public record Create(String name, String country, String email) {
  }

  public record ChangeEmail(String newEmail) {
  }

  /**
   * Handle a command to create a new user.
   * Emits a UserWasCreated event.
   */
  static public UserEvent onCommand(Create cmd) {
    return new UserWasCreated(cmd.name, cmd.country, cmd.email);
  }

  static public User onEvent(UserWasCreated evt) {
    return new User(evt.name(), evt.country(), evt.email());
  }

  /**
   * Emits a EmailAssigned and EmailUnassigned event.
   * Emits nothing if 'changing' to the same email address.
   * <p>
   * When changing the email address, we need to emit two events:
   * one to assign the new email address and one to un-assign the old email address.
   * <p>
   * Later the UserEventsSubscriber will react to these events and update the UniqueEmailEntity accordingly.
   * The newly assigned email will be confirmed and the old email will be marked as not-in-use.
   */
  public List<UserEvent> onCommand(ChangeEmail cmd) {
    if (cmd.newEmail().equals(email))
      return List.of();
    else
      return List.of(
        new EmailAssigned(cmd.newEmail),
        new EmailUnassigned(email)
      );
  }

  public User onEvent(EmailAssigned evt) {
    return new User(name, country, evt.newEmail());
  }

}

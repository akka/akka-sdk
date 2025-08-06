package user.registry.domain;

public record User(String name, String country, String email) {
  public User withEmail(String newEmail) {
    return new User(name, country, newEmail);
  }
}

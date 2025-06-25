package customer.domain;

public record CustomerEntry(String id, String email, String name) {
  public CustomerEntry withName(String newName) {
    return new CustomerEntry(id, email, newName);
  }
}

package customer.domain;

public record CustomerEntry(String email, String name, Address address) {

  public CustomerEntry withName(String newName) {
    return new CustomerEntry(email, newName, address);
  }

  public CustomerEntry withAddress(Address newAddress) {
    return new CustomerEntry(email, name, newAddress);
  }
}

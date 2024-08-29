package customer.domain;

import customer.domain.Address;

public record CustomerRow(String email, String name, Address address) {

  public CustomerRow withName(String newName) {
    return new CustomerRow(email, newName, address);
  }

  public CustomerRow withAddress(Address newAddress) {
    return new CustomerRow(email, name, newAddress);
  }
}

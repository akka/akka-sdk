package customer.domain;

// tag::class[]

import akka.javasdk.annotations.Migration;
import akka.javasdk.annotations.TypeName;

public sealed interface CustomerEvent {

  @TypeName("internal-customer-created") // <1>
  // end::class[]
  // tag::customer-created-new[]
  @Migration(CustomerCreatedMigration.class)
    // tag::class[]
  record CustomerCreated(String email, String name, Address address) implements CustomerEvent {
  }
  // end::customer-created-new[]

  @TypeName("internal-name-changed")
    // tag::name-changed-old[]
  record NameChanged(String newName) implements CustomerEvent {
  }
  // end::name-changed-old[]

  @TypeName("internal-address-changed")
    // tag::address-changed-old[]
  record AddressChanged(Address address) implements CustomerEvent {
  }
  // end::address-changed-old[]
}
// end::class[]

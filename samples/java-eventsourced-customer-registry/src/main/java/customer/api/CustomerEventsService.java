package customer.api;

import akka.platform.javasdk.annotations.ComponentId;
import customer.domain.CustomerEvent;
import customer.domain.CustomerEvent.CustomerCreated;
import customer.domain.CustomerEvent.NameChanged;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.annotations.Consume;

// tag::producer[]
@ComponentId("customer-events-service")
@Consume.FromEventSourcedEntity(value = CustomerEntity.class) // <1>
@Produce.ServiceStream(id = "customer_events") // <2>
@Acl(allow = @Acl.Matcher(service = "*")) // <3>
public class CustomerEventsService extends Action {

  public Effect<CustomerPublicEvent> onEvent(CustomerEvent event) { // <4>
    return switch (event) {
      case CustomerCreated created -> effects()
        .reply(new CustomerPublicEvent.Created(created.email(), created.name()));
      case NameChanged nameChanged -> effects()
        .reply(new CustomerPublicEvent.NameChanged(nameChanged.newName()));
      case CustomerEvent.AddressChanged __ -> effects().ignore(); // <5>
    };
  }
}
// end::producer[]
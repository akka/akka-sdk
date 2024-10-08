package customer.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;
import customer.application.CustomerEntity;
import customer.domain.CustomerEvent;
import customer.domain.CustomerEvent.AddressChanged;
import customer.domain.CustomerEvent.CustomerCreated;
import customer.domain.CustomerEvent.NameChanged;

// tag::producer[]
@ComponentId("customer-events-service")
@Consume.FromEventSourcedEntity(CustomerEntity.class) // <1>
@Produce.ServiceStream(id = "customer_events") // <2>
@Acl(allow = @Acl.Matcher(service = "*")) // <3>
public class CustomerEvents extends Consumer {

  public Effect onEvent(CustomerEvent event) { // <4>
    return switch (event) {
      case CustomerCreated created -> effects()
        .produce(new CustomerPublicEvent.Created(created.email(), created.name()));
      case NameChanged nameChanged -> effects()
        .produce(new CustomerPublicEvent.NameChanged(nameChanged.newName()));
      case AddressChanged __ -> effects().ignore(); // <5>
    };
  }
}
// end::producer[]
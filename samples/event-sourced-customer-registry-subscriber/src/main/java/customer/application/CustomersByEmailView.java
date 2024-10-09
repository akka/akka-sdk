package customer.application;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;
import customer.domain.CustomersList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::view[]

@ComponentId("customers_by_email_view")
public class CustomersByEmailView extends View {
  // end::view[]
  private static final Logger logger = LoggerFactory.getLogger(CustomersByEmailView.class);
  // tag::view[]

  @Consume.FromServiceStream( // <1>
      service = "customer-registry", // <2>
      id = "customer_events", // <3>
      consumerGroup = "customer-by-email-view" // <4>
  )
  public static class CustomersByEmail extends TableUpdater<Customer> {
    public Effect<Customer> onEvent(CustomerPublicEvent.Created created) {
      // end::view[]
      logger.info("Received: {}", created);
      // tag::view[]
      var id = updateContext().eventSubject().get();
      return effects().updateRow(
          new Customer(id, created.email(), created.name()));
    }

    public Effect<Customer> onEvent(CustomerPublicEvent.NameChanged nameChanged) {
      // end::view[]
      logger.info("Received: {}", nameChanged);
      // tag::view[]
      var updated = rowState().withName(nameChanged.newName());
      return effects().updateRow(updated);
    }
  }

  @Query("SELECT * AS customers FROM customers_by_email WHERE email = :email")
  public QueryEffect<CustomersList> findByEmail(String email) {
    return queryResult();
  }

}
// end::view[]
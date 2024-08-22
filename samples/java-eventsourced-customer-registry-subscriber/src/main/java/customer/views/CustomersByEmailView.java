package customer.views;

import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;
import akka.platform.javasdk.view.TableUpdater;
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

  public record QueryParameters(String email) {
  }

  @Query("SELECT * AS customers FROM customers_by_email WHERE email = :email")
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  public QueryEffect<CustomersList> findByEmail(QueryParameters params) {
    return queryResult();
  }

}
// end::view[]
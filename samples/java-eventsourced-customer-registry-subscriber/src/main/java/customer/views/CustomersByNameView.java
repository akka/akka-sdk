package customer.views;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;
import akka.javasdk.view.TableUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::view[]
@ComponentId("customers_by_name")
public class CustomersByNameView extends View {
  // end::view[]
  private static final Logger logger = LoggerFactory.getLogger(CustomersByNameView.class);
  // tag::view[]

  @Consume.FromServiceStream( // <1>
      service = "customer-registry", // <2>
      id = "customer_events", // <3>
      consumerGroup = "customer-by-name-view"
  )
  public static class CustomersByName extends TableUpdater<Customer> {

    public Effect<Customer> onEvent( // <4>
                                     CustomerPublicEvent.Created created) {
      // end::view[]
      logger.info("Received: {}", created);
      // tag::view[]
      var id = updateContext().eventSubject().get();
      return effects().updateRow(
          new Customer(id, created.email(), created.name()));
    }

    public Effect<Customer> onEvent(
        CustomerPublicEvent.NameChanged nameChanged) {
      // end::view[]
      logger.info("Received: {}", nameChanged);
      // tag::view[]
      var updated = rowState().withName(nameChanged.newName());
      return effects().updateRow(updated);
    }
  }

  public record QueryParameters(String name) {
  }
  
  @Query("SELECT * as customers FROM customers_by_name WHERE name = :name")
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  public QueryEffect<CustomersList> findByName(QueryParameters params) {
    return queryResult();
  }

}
// end::view[]

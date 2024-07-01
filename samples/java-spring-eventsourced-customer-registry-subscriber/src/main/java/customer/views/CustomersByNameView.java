package customer.views;

import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::view[]
@ViewId("customers_by_name")
@Table("customers_by_name")
@Subscribe.Stream( // <1>
    service = "customer-registry", // <2>
  id = "customer_events", // <3>
  consumerGroup = "customer-by-name-view"
)
public class CustomersByNameView extends View<Customer> {
  // end::view[]
  private static final Logger logger = LoggerFactory.getLogger(CustomersByNameView.class);
  // tag::view[]

  public Effect<Customer> onEvent( // <4>
                                   CustomerPublicEvent.Created created) {
    // end::view[]
    logger.info("Received: {}", created);
    // tag::view[]
    var id = updateContext().eventSubject().get();
    return effects().updateState(
        new Customer(id, created.email(), created.name()));
  }

  public Effect<Customer> onEvent(
      CustomerPublicEvent.NameChanged nameChanged) {
    // end::view[]
    logger.info("Received: {}", nameChanged);
    // tag::view[]
    var updated = viewState().withName(nameChanged.newName());
    return effects().updateState(updated);
  }

  public record QueryParameters(String name) {
  }
  
  @Query("SELECT * as customers FROM customers_by_name WHERE name = :name")
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  public CustomersList findByName(QueryParameters params) {
    return null;
  }

}
// end::view[]

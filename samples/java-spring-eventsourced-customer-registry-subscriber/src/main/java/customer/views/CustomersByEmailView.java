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

@ViewId("customers_by_email")
@Table("customers_by_email")
@Subscribe.Stream( // <1>
  service = "customer-registry", // <2>
  id = "customer_events", // <3>
  consumerGroup = "customer-by-email-view" // <4>
)
public class CustomersByEmailView extends View<Customer> {
  // end::view[]
  private static final Logger logger = LoggerFactory.getLogger(CustomersByEmailView.class);
  // tag::view[]

  public Effect<Customer> onEvent(CustomerPublicEvent.Created created) {
    // end::view[]
    logger.info("Received: {}", created);
    // tag::view[]
    var id = updateContext().eventSubject().get();
    return effects().updateState(
      new Customer(id, created.email(), created.name()));
  }

  public Effect<Customer> onEvent(CustomerPublicEvent.NameChanged nameChanged) {
    // end::view[]
    logger.info("Received: {}", nameChanged);
    // tag::view[]
    var updated = viewState().withName(nameChanged.newName());
    return effects().updateState(updated);
  }

  public record QueryParameters(String email) {
  }

  @Query("SELECT * AS customers FROM customers_by_email WHERE email = :email")
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  public CustomersList findByEmail(QueryParameters params) {
    return null;
  }

}
// end::view[]

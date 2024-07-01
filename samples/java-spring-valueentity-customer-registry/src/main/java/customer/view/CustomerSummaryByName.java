package customer.view;

import customer.api.CustomerEntity;
import customer.api.CustomerSummary;
import customer.domain.Customer;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

@ViewId("summary_customer_by_name")
@Table("customers")
public class CustomerSummaryByName extends View<CustomerSummary> {

  public record QueryParameters(String name) { }

  @Query("SELECT * FROM customers WHERE name = :name")
  public CustomerSummary getCustomer(QueryParameters params) {
    return null;
  }
  @Subscribe.ValueEntity(CustomerEntity.class)
  public Effect<CustomerSummary> onChange(Customer customer) {
    return effects()
        .updateState(new CustomerSummary(customer.email(), customer.name()));
  }

  @Subscribe.ValueEntity(value = CustomerEntity.class, handleDeletes = true)
  public Effect<CustomerSummary> onDelete() {
    return effects()
        .deleteState();
  }

}

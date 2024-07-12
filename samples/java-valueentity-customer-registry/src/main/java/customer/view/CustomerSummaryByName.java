package customer.view;

import customer.api.CustomerEntity;
import customer.api.CustomerSummary;
import customer.domain.Customer;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ViewId;
import akka.platform.javasdk.view.View;

@ViewId("summary_customer_by_name")
@Table("customers")
public class CustomerSummaryByName extends View<CustomerSummary> {

  public record QueryParameters(String name) { }

  @Query("SELECT * FROM customers WHERE name = :name")
  public CustomerSummary getCustomer(QueryParameters params) {
    return null;
  }
  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public Effect<CustomerSummary> onChange(Customer customer) {
    return effects()
        .updateState(new CustomerSummary(customer.email(), customer.name()));
  }

  @Consume.FromKeyValueEntity(value = CustomerEntity.class, handleDeletes = true)
  public Effect<CustomerSummary> onDelete() {
    return effects()
        .deleteState();
  }

}

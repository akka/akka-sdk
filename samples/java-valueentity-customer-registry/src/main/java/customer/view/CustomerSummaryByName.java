package customer.view;

import akka.javasdk.view.TableUpdater;
import akka.javasdk.annotations.DeleteHandler;
import customer.api.CustomerEntity;
import customer.api.CustomerSummary;
import customer.domain.Customer;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;

@ComponentId("summary_customer_by_name")
public class CustomerSummaryByName extends View {

  public record QueryParameters(String name) { }

  @Query("SELECT * FROM customers WHERE name = :name")
  public QueryEffect<CustomerSummary> getCustomer(QueryParameters params) { return queryResult(); }

  @Consume.FromKeyValueEntity(value = CustomerEntity.class)
  public static class Customers extends TableUpdater<CustomerSummary> {

    public Effect<CustomerSummary> onChange(Customer customer) {
      return effects()
          .updateRow(new CustomerSummary(customer.email(), customer.name()));
    }

    @DeleteHandler
    public Effect<CustomerSummary> onDelete() {
      return effects().deleteRow();
    }
  }
}

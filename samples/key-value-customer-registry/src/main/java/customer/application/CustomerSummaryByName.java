package customer.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;

@Component(id = "customer-summary-by-name")
public class CustomerSummaryByName extends View {

  public record CustomerSummary(
    String id,
    String name,
    boolean deleted,
    boolean hasActiveOrders
  ) {
    CustomerSummary(String id, String name, boolean hasActiveOrders) {
      this(id, name, false, hasActiveOrders);
    }
    CustomerSummary asDeleted() {
      return new CustomerSummary(id, name, true, hasActiveOrders);
    }
  }

  // tag::delete[]
  @Consume.FromKeyValueEntity(value = CustomerEntity.class)
  public static class CustomersUpdater extends TableUpdater<CustomerSummary> { // <1>

    public Effect<CustomerSummary> onUpdate(Customer customer) {
      return effects()
        .updateRow(
          new CustomerSummary(updateContext().eventSubject().get(), customer.name(), false)
        );
    }

    // ...
    @DeleteHandler // <2>
    public Effect<CustomerSummary> onDelete() {
      CustomerSummary currentRow = rowState();
      if (currentRow.hasActiveOrders()) {
        // Logical delete: keep the row but mark it as deleted // <3>
        return effects().updateRow(currentRow.asDeleted());
      } else {
        // Hard delete: physically remove the row from the view // <4>
        return effects().deleteRow();
      }
    }
  }

  // end::delete[]

  // tag::projection[]
  @Query("SELECT id, name FROM customers WHERE name = :customerName") // <1>
  public QueryEffect<CustomerSummary> getCustomer(String customerName) {
    return queryResult(); // <2>
  }
  // end::projection[]

}

package customer.view;

import customer.api.CustomerEntity;
import customer.api.CustomerList;
import customer.domain.Customer;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

import java.util.List;

@ViewId("customers_by_city")
@Table("customers_by_city")
// tag::view-test[]
@Subscribe.ValueEntity(CustomerEntity.class)
public class CustomersByCity extends View<Customer> {

  public record QueryParameters(List<String> cities) {
    public static QueryParameters of(String... cities) {
      return new QueryParameters(List.of(cities));
    }
  }

  @Query("""
    SELECT * AS customers
        FROM customers_by_city
      WHERE address.city = ANY(:cities)
    """)
  public CustomerList getCustomers(QueryParameters params) {
    return null;
  }
}
// end::view-test[]

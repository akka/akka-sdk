package customer.view;

import customer.api.CustomerEntity;
import customer.api.CustomerList;
import customer.domain.Customer;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

import java.util.List;

@ComponentId("customers_by_city")
@Table("customers_by_city")
// tag::view-test[]
@Consume.FromKeyValueEntity(CustomerEntity.class)
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

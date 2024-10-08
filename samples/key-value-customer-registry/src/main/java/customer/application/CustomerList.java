package customer.application;

import customer.domain.Customer;

import java.util.Collection;

// tag::class[]
public record CustomerList(Collection<Customer> customers) { }
// end::class[]
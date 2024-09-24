package customer.application;

import customer.domain.Customer;

import java.util.Collection;

public record CustomerList(Collection<Customer> customers) { }

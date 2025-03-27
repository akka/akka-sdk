package customer.domain;

import java.util.Collection;

public record CustomerEntries(Collection<CustomerEntry> customers) {
}

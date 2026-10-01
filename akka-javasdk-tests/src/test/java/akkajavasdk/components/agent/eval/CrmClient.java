/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.eval;

import java.util.List;

/**
 * The sample service's seam to the CRM. In a real service this is the interface a production client
 * implements and the service setup provides; under test the eval suite provides a recording
 * stand-in through the TestKit's {@code DependencyProvider}.
 */
public interface CrmClient {

  /** The customer record, or a thrown {@link java.util.NoSuchElementException} if unknown. */
  Customer getCustomer(String customerId);

  /** The customer's open tickets, oldest first. Empty when they have none. */
  List<Ticket> openTickets(String customerId);
}

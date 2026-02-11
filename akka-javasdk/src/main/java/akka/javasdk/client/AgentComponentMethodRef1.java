/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.javasdk.agent.Agent;

public interface AgentComponentMethodRef1<A1, R> extends ComponentMethodRef1<A1, R> {

  Agent.AgentReply<R> invokeReply(A1 arg);
}

/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import akka.javasdk.agent.autonomous.AgentSetup
import akka.javasdk.agent.autonomous.capability.AgentCapability
import akka.javasdk.agent.autonomous.capability.Delegation
import akka.javasdk.agent.autonomous.capability.TaskAcceptance
import akka.javasdk.impl.agent.autonomous.capability.DelegationImpl
import akka.javasdk.impl.agent.autonomous.capability.TaskAcceptanceImpl

/**
 * Implicit conversions from public API types to their impl types for test assertions.
 */
trait AutonomousAgentImplSupport {

  implicit class AgentSetupOps(setup: AgentSetup) {
    def impl: AgentSetupImpl = setup.asInstanceOf[AgentSetupImpl]
  }

  implicit class TaskAcceptanceOps(acceptance: TaskAcceptance) {
    def impl: TaskAcceptanceImpl = acceptance.asInstanceOf[TaskAcceptanceImpl]
  }

  implicit class DelegationOps(delegation: Delegation) {
    def impl: DelegationImpl = delegation.asInstanceOf[DelegationImpl]
  }

  implicit class AgentCapabilityOps(capability: AgentCapability) {
    def asTaskAcceptance: TaskAcceptanceImpl = capability.asInstanceOf[TaskAcceptanceImpl]
    def asDelegation: DelegationImpl = capability.asInstanceOf[DelegationImpl]
  }
}

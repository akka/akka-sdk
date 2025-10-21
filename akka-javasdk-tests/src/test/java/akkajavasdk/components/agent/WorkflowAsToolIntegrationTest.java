/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.workflowentities.Balance;
import akkajavasdk.components.workflowentities.WalletEntity;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class WorkflowAsToolIntegrationTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(WalletManagerAgent.class, testModelProvider);
  }

  @AfterEach
  public void clearModelProviderState() {
    testModelProvider.reset();
  }

  private String newSessionId() {
    return UUID.randomUUID().toString();
  }

  /** An agent that uses WalletEntity as a tool to manage wallets. */
  @Component(
      id = "wallet-manager-agent",
      name = "WalletManagerAgent",
      description = "Agent that manages wallets using entity tools")
  public static class WalletManagerAgent extends Agent {

    public record WalletRequest(String walletId, int initialAmount) {}

    public record WalletResponse(String message, Balance balance) {}

    public Effect<WalletResponse> createAndGetBalance(WalletRequest request) {
      return effects()
          .systemMessage("You are a wallet management agent.")
          .tools(WalletEntity.class)
          .userMessage(
              "Create a new wallet with initial amount "
                  + request.initialAmount
                  + " and then get its balance")
          .responseConformsTo(Balance.class)
          .map(
              balance ->
                  new WalletResponse("Wallet created and balance retrieved successfully", balance))
          .thenReply();
    }
  }

  @Test
  public void agentShouldCallWalletEntityToCreateAndGetBalance() {
    // Setup test model to simulate LLM calling the tools
    String walletId = "wallet-789";
    int initialAmount = 1000;

    // First tool call - create wallet
    testModelProvider
        .whenMessage(s -> s.contains("Create a new wallet"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "WalletEntity_create",
                "{\"uniqueId\":\"" + walletId + "\",\"amount\":" + initialAmount + "}"));

    testModelProvider
        .whenToolResult(result -> result.name().equals("WalletEntity_create"))
        // after creating a wallet, we fetch its balance
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "WalletEntity_get", "{\"uniqueId\":\"" + walletId + "\"}"));

    // Final response after getting wallet balance
    testModelProvider
        .whenToolResult(result -> result.name().equals("WalletEntity_get"))
        // the returned value is just sent back as structured content
        // expected to be the Balance serialized as json
        .thenReply(result -> new TestModelProvider.AiResponse(result.content()));

    // Execute the agent
    var response =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(WalletManagerAgent::createAndGetBalance)
            .invoke(new WalletManagerAgent.WalletRequest(walletId, initialAmount));

    // Verify the response
    assertThat(response).isNotNull();
    assertThat(response.message()).isEqualTo("Wallet created and balance retrieved successfully");
    assertThat(response.balance).isNotNull();
    assertThat(response.balance.value).isEqualTo(initialAmount);
  }
}

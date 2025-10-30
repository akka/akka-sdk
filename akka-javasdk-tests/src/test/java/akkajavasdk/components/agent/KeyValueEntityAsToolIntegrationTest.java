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
import akkajavasdk.components.keyvalueentities.user.User;
import akkajavasdk.components.keyvalueentities.user.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class KeyValueEntityAsToolIntegrationTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(UserManagerAgent.class, testModelProvider);
  }

  @AfterEach
  public void clearModelProviderState() {
    testModelProvider.reset();
  }

  private String newSessionId() {
    return UUID.randomUUID().toString();
  }

  /** An agent that uses UserToolEntity as a tool to manage users. */
  @Component(
      id = "user-manager-agent",
      name = "UserManagerAgent",
      description = "Agent that manages users using entity tools")
  public static class UserManagerAgent extends Agent {

    public record UserRequest(String userId, String name, String email) {}

    public record UserResponse(String message, User user) {}

    public Effect<UserResponse> createAndGetUser(UserRequest request) {
      return effects()
          .systemMessage("You are a user management agent.")
          .tools(UserEntity.class)
          .userMessage(
              "Create a new user,"
                  + " with name "
                  + request.name
                  + " with email "
                  + request.email
                  + "and then get their information")
          .responseConformsTo(User.class)
          .map(user -> new UserResponse("User created and retrieved successfully", user))
          .thenReply();
    }
  }

  @Test
  public void agentShouldCallKeyValueEntityToCreateAndGetUser() {
    // Setup test model to simulate LLM calling the tools
    String userId = "user-123";
    String userName = "John Doe";
    String userEmail = "john@example.com";

    // First tool call - create user
    testModelProvider
        .whenMessage(s -> s.contains("Create a new user"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "UserEntity_createUser",
                "{\"uniqueId\":\""
                    + userId
                    + "\",\"createUser\":{\"name\":\""
                    + userName
                    + "\",\"email\":\""
                    + userEmail
                    + "\"}}"));

    testModelProvider
        .whenToolResult(result -> result.name().equals("UserEntity_createUser"))
        // after creating a user, we fetch it
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "UserEntity_getUser", "{\"uniqueId\":\"" + userId + "\"}"));

    // Final response after getting user
    testModelProvider
        .whenToolResult(result -> result.name().equals("UserEntity_getUser"))
        // the returned value is just sent back as structured content - expected to be the User
        // serialized as json
        .thenReply(result -> new TestModelProvider.AiResponse(result.content()));

    // Execute the agent
    var response =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(UserManagerAgent::createAndGetUser)
            .invoke(new UserManagerAgent.UserRequest(userId, userName, userEmail));

    // Verify the response
    assertThat(response).isNotNull();
    assertThat(response.message()).isEqualTo("User created and retrieved successfully");
    assertThat(response.user).isNotNull();
    assertThat(response.user.name).isEqualTo(userName);
    assertThat(response.user.email).isEqualTo(userEmail);
  }
}

package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.List;
import java.util.UUID;

// tag::prompt[]
@ComponentId("activity-agent")
public class ActivityAgent extends Agent {
  private static final String SYSTEM_MESSAGE = // <1>
      """
      You are an activity agent. Your job is to suggest activities in the
      real world. Like for example, a team building activity, sports, an
      indoor or outdoor game, board games, a city trip, etc.
      """.stripIndent();

  public Effect<String> query(String message) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE) // <2>
        .userMessage(message)// <3>
        .thenReply();
  }
}
// end::prompt[]

class Caller {
  private final ComponentClient componentClient;

  Caller(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  void callIt() {
    // tag::call[]
    var sessionId = UUID.randomUUID().toString();
    String suggestion =
        componentClient
            .forAgent()// <1>
            .inSession(sessionId)// <2>
            .method(ActivityAgent::query)
            .invoke("Business colleagues meeting in London the first week of July");
    // end::call[]
  }
}

interface ActivityAgentMore {

  // tag::prompt-template[]
  @ComponentId("activity-agent")
  public class ActivityAgentWithTemplate extends Agent {
    public Effect<String> query(String message) {
      return effects()
        .systemMessageFromTemplate("activity-agent-prompt") // <1>
        .userMessage(message)//
        .thenReply();
    }
  }
  // end::prompt-template[]

  // tag::di[]
  @ComponentId("activity-agent")
  public class ActivityAgent extends Agent {
    public record Request(String userId, String message) {}

    private static final String SYSTEM_MESSAGE =
        """
        You are an activity agent. Your job is to suggest activities in the
        real world. Like for example, a team building activity, sports, an
        indoor or outdoor game, board games, a city trip, etc.
        """.stripIndent();

    private final ComponentClient componentClient;

    public ActivityAgent(ComponentClient componentClient) { // <1>
      this.componentClient = componentClient;
    }

    public Effect<String> query(Request request) {
      var profile = componentClient // <2>
          .forEventSourcedEntity(request.userId)
          .method(UserProfileEntity::getProfile)
          .invoke();

      var userMessage = request.message + "\nPreferences: " + profile.preferences; // <3>

      return effects()
          .systemMessage(SYSTEM_MESSAGE)
          .userMessage(userMessage)
          .thenReply();
    }
  }
  // end::di[]

  public record UserProfile(
      String userId,
      String preferences) {}

  public sealed interface UserProfileEvent {
    @TypeName("user-profile-updated")
    record UserProfileUpdated(String preferences
    ) implements UserProfileEvent {}
  }

  @ComponentId("user-profile")
  public class UserProfileEntity extends EventSourcedEntity<UserProfile, UserProfileEvent> {
    public record GetProfile() {}

    public Effect<UserProfile> getProfile() {
      return effects().reply(currentState());
    }

    @Override
    public UserProfile applyEvent(UserProfileEvent event) {
      return null;
    }
  }

}

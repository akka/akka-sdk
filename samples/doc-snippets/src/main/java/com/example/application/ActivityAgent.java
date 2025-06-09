package com.example.application;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Acl;
import akka.javasdk.agent.JsonParsingException;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.http.HttpResponses;

import java.time.Duration;
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

  // tag::structured-response[]
  @ComponentId("activity-agent")
  public class ActivityAgentStructuredResponse extends Agent {

    private static final String SYSTEM_MESSAGE = // <1>
      """
      You are an activity agent. Your job is to suggest activities in the
      real world. Like for example, a team building activity, sports, an
      indoor or outdoor game, board games, a city trip, etc.
      
      Your response should be a JSON object with the following structure:
      {
        "name": "Name of the activity",
        "description": "Description of the activity"
      }
      
      Do not include any explanations or text outside of the JSON structure.
      """.stripIndent();

    private static final Activity DEFAULT_ACTIVITY = new Activity("running", "Running is a great way to stay fit and healthy. You can do it anywhere, anytime, and it requires no special equipment.");

    record Activity(String name, String description) {} // <2>

    public Effect<Activity> query(String message) {
      return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(message)
        .responseAs(Activity.class) // <3>
        .onFailure(throwable -> { // <4>
          if (throwable instanceof JsonParsingException jsonParsingException) {
            return DEFAULT_ACTIVITY;
          } else {
            throw new RuntimeException(throwable);
          }
        })
        .thenReply();
    }
  }
  // end::structured-response[]

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

  // tag::stream-tokens[]
  @ComponentId("streaming-activity-agent")
  public class StreamingActivityAgent extends Agent {
    private static final String SYSTEM_MESSAGE =
        """
        You are an activity agent. Your job is to suggest activities in the
        real world. Like for example, a team building activity, sports, an
        indoor or outdoor game, board games, a city trip, etc.
        """.stripIndent();

    public StreamEffect query(String message) { // <1>
      return streamEffects() // <2>
          .systemMessage(SYSTEM_MESSAGE)
          .userMessage(message)
          .thenReply();
    }
  }
  // end::stream-tokens[]

  // tag::stream-endpoint[]
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  @HttpEndpoint("/api")
  public class ActivityHttpEndpoint {

    public record Request(String sessionId, String question) {
    }

    private final ComponentClient componentClient;

    public ActivityHttpEndpoint(ComponentClient componentClient) {
      this.componentClient = componentClient;
    }

    @Post("/ask")
    public HttpResponse ask(Request request) {
      var responseStream = componentClient
          .forAgent()
          .inSession(request.sessionId)
          .tokenStream(StreamingActivityAgent::query) // <1>
          .source(request.question); // <2>

      return HttpResponses.serverSentEvents(responseStream); // <3>
    }

    // end::stream-endpoint[]
    // tag::stream-group[]
    @Post("/ask-grouped")
    public HttpResponse askGrouped(Request request) {
      var tokenStream = componentClient
          .forAgent()
          .inSession(request.sessionId)
          .tokenStream(StreamingActivityAgent::query)
          .source(request.question);

      var groupedTokenStream =
          tokenStream
              .groupedWithin(20, Duration.ofMillis(100)) // <1>
              .map(group -> String.join("", group)); // <2>

      return HttpResponses.serverSentEvents(groupedTokenStream); // <3>
    }
    // end::stream-group[]
    // tag::stream-endpoint[]

  }
  // end::stream-endpoint[]


}

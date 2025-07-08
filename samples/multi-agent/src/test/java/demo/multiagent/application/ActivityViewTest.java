package demo.multiagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.typesafe.config.ConfigFactory;
import demo.multiagent.application.ActivityView.ActivityEntries;
import demo.multiagent.application.ActivityView.ActivityEntry;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ActivityViewTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      // Bootstrap will check if key exists when running integation tests.
      // We don't need a real one though.
      .withAdditionalConfig(
        ConfigFactory.parseString("akka.javasdk.agent.openai.api-key=fake-key")
      )
      .withWorkflowIncomingMessages("agent-team");
  }

  @Test
  public void shouldHandleMultipleSessionsForSameUser() {
    var workflowMessages = testKit.getWorkflowIncomingMessages("agent-team");

    var userId = "alice";

    // First session
    var sessionId1 = "session-1";
    var userQuery1 = "What should I do in Stockholm?";
    var finalAnswer1 = "Visit the Vasa Museum and explore Gamla Stan.";
    var workflowState1 = new AgentTeamWorkflow.State(
      userId,
      userQuery1,
      null,
      finalAnswer1,
      null,
      AgentTeamWorkflow.Status.COMPLETED
    );

    // Second session
    var sessionId2 = "session-2";
    var userQuery2 = "What's the weather like?";
    var finalAnswer2 = "It's sunny today.";
    var workflowState2 = new AgentTeamWorkflow.State(
      userId,
      userQuery2,
      null,
      finalAnswer2,
      null,
      AgentTeamWorkflow.Status.COMPLETED
    );

    // Publish both workflow state changes
    workflowMessages.publish(workflowState1, sessionId1);
    workflowMessages.publish(workflowState2, sessionId2);

    // Wait for view to be updated and verify both activities
    Awaitility
      .await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        ActivityEntries activities = componentClient
          .forView()
          .method(ActivityView::getActivities)
          .invoke(userId);

        assertThat(activities.entries()).hasSize(2);

        // Verify both entries are present (order may vary)
        assertThat(activities.entries())
          .extracting(ActivityEntry::sessionId)
          .containsExactlyInAnyOrder(sessionId1, sessionId2);

        assertThat(activities.entries())
          .extracting(ActivityEntry::userId)
          .containsOnly(userId);

        // Verify specific content of each activity
        assertThat(activities.entries())
          .extracting(ActivityEntry::userQuestion)
          .containsExactlyInAnyOrder(userQuery1, userQuery2);

        assertThat(activities.entries())
          .extracting(ActivityEntry::finalAnswer)
          .containsExactlyInAnyOrder(finalAnswer1, finalAnswer2);
      });
  }

  @Test
  public void shouldReturnEmptyForNonExistentUser() {
    ActivityEntries activities = componentClient
      .forView()
      .method(ActivityView::getActivities)
      .invoke("non-existent-user");

    assertThat(activities.entries()).isEmpty();
  }

  @Test
  public void shouldFilterActivitiesByUserId() {
    var workflowMessages = testKit.getWorkflowIncomingMessages("agent-team");

    // Activity for user bob
    var aliceState = new AgentTeamWorkflow.State(
      "bob",
      "Bob's query",
      null,
      "Bob's answer",
      null,
      AgentTeamWorkflow.Status.COMPLETED
    );

    // Activity for user charlie
    var charlieState = new AgentTeamWorkflow.State(
      "charlie",
      "Charlie's query",
      null,
      "Charlie's answer",
      null,
      AgentTeamWorkflow.Status.COMPLETED
    );

    workflowMessages.publish(aliceState, "bob-session");
    workflowMessages.publish(charlieState, "charlie-session");

    // Verify bob only sees her activities
    Awaitility
      .await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        ActivityEntries aliceActivities = componentClient
          .forView()
          .method(ActivityView::getActivities)
          .invoke("bob");

        assertThat(aliceActivities.entries()).hasSize(1);
        assertThat(aliceActivities.entries().get(0).userId()).isEqualTo("bob");
        assertThat(aliceActivities.entries().get(0).userQuestion()).isEqualTo("Bob's query");
      });

    // Verify charlie only sees his activities
    Awaitility
      .await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        ActivityEntries charlieActivities = componentClient
          .forView()
          .method(ActivityView::getActivities)
          .invoke("charlie");

        assertThat(charlieActivities.entries()).hasSize(1);
        assertThat(charlieActivities.entries().get(0).userId()).isEqualTo("charlie");
        assertThat(charlieActivities.entries().get(0).userQuestion())
          .isEqualTo("Charlie's query");
      });
  }
}

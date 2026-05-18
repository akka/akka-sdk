package demo.editorial;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.editorial.api.EditorialEndpoint;
import demo.editorial.application.Article;
import demo.editorial.application.ArticleDraft;
import demo.editorial.application.EditorInChief;
import demo.editorial.application.EditorialTasks;
import demo.editorial.application.ResearchDigest;
import demo.editorial.application.ResearchEditor;
import demo.editorial.application.ReviewEditor;
import demo.editorial.application.ReviewReport;
import demo.editorial.application.WritingLead;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EditorialIntegrationTest extends TestKitSupport {

  private static final String TOPIC =
    "How autonomous AI agents coordinate in multi-agent systems";

  private final TestModelProvider editorModel = new TestModelProvider();
  private final TestModelProvider researchEditorModel = new TestModelProvider();
  private final TestModelProvider writingLeadModel = new TestModelProvider();
  private final TestModelProvider reviewEditorModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(EditorInChief.class, editorModel)
      .withModelProvider(ResearchEditor.class, researchEditorModel)
      .withModelProvider(WritingLead.class, writingLeadModel)
      .withModelProvider(ReviewEditor.class, reviewEditorModel);
  }

  @BeforeEach
  public void resetModels() {
    editorModel.reset();
    researchEditorModel.reset();
    writingLeadModel.reset();
    reviewEditorModel.reset();
  }

  // Verifies the endpoint wiring: a submitted topic creates an ARTICLE task assigned to the
  // EditorInChief, and the task completes with a typed Article result.
  @Test
  public void shouldAcceptTopicAndCompleteArticle() {
    editorModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new Article(
            "Coordinating Autonomous AI Agents",
            "An overview of how autonomous agents divide and coordinate work.",
            List.of("Delegation", "Handoff", "Moderation")
          )
        )
      )
    );

    var response = httpClient
      .POST("/editorial")
      .withRequestBody(new EditorialEndpoint.TopicRequest(TOPIC))
      .responseBodyAs(EditorialEndpoint.TopicResponse.class)
      .invoke()
      .body();

    assertThat(response.taskId()).isNotBlank();
    assertThat(response.runId()).isNotBlank();
    assertThat(response.agentComponentId()).isEqualTo("editor-in-chief");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(response.taskId()).get(EditorialTasks.ARTICLE);
        var article = snapshot.result().orElseThrow();
        assertThat(article.title()).contains("Agents");
        assertThat(article.keyPoints()).hasSize(3);
      });
  }

  // Verifies the editorial delegation pipeline: the EditorInChief delegates the research, writing,
  // and review stages to the three section leads and synthesises their results into the final
  // Article. The leads complete their stage tasks directly here; their own inner coordination
  // (delegation, team, moderation) is covered by the research, devteam, and peerreview samples.
  @Test
  public void shouldDelegateStagesAndSynthesizeArticle() {
    editorModel
      .whenMessage(msg -> msg.contains("multi-agent"))
      .reply(
        List.of(
          delegateTo(
            EditorialTasks.RESEARCH,
            ResearchEditor.class,
            "Commission research on coordination in multi-agent systems"
          ),
          delegateTo(
            EditorialTasks.DRAFT,
            WritingLead.class,
            "Write the article draft from the research"
          ),
          delegateTo(
            EditorialTasks.REVIEW,
            ReviewEditor.class,
            "Review the draft for accuracy and readability"
          )
        )
      );

    researchEditorModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new ResearchDigest(
            "Delegation, handoff, and moderation are the core coordination patterns.",
            List.of("doc-research-1", "doc-research-2")
          )
        )
      )
    );

    writingLeadModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new ArticleDraft(
            "Coordinating Autonomous AI Agents",
            "A draft on delegation, handoff, and moderation across agents.",
            List.of("doc-section-1")
          )
        )
      )
    );

    reviewEditorModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new ReviewReport(
            "Accurate and readable; tighten the section on shared context.",
            List.of(
              "Accuracy: coordination patterns are described correctly",
              "Readability: clear progression"
            )
          )
        )
      )
    );

    editorModel
      .whenMessage(msg -> msg.contains("Continue working"))
      .reply(
        completeTask(
          new Article(
            "Coordinating Autonomous AI Agents in Production",
            "Autonomous agents coordinate through delegation, handoff, and moderation.",
            List.of(
              "Delegation fans out work and synthesises results",
              "Handoff transfers ownership between agents",
              "Moderation structures turn-taking in a conversation"
            )
          )
        )
      );

    var response = httpClient
      .POST("/editorial")
      .withRequestBody(new EditorialEndpoint.TopicRequest(TOPIC))
      .responseBodyAs(EditorialEndpoint.TopicResponse.class)
      .invoke()
      .body();

    assertThat(response.taskId()).isNotBlank();
    assertThat(response.agentComponentId()).isEqualTo("editor-in-chief");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(response.taskId()).get(EditorialTasks.ARTICLE);
        var article = snapshot.result().orElseThrow();
        assertThat(article.title()).contains("Agents");
        assertThat(article.keyPoints()).hasSize(3);
      });
  }
}

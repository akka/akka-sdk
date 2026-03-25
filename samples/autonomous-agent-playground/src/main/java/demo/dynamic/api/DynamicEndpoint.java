package demo.dynamic.api;

import akka.javasdk.agent.autonomous.AgentSetup;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.dynamic.application.DynamicAgent;
import demo.dynamic.application.DynamicTasks;
import java.util.UUID;

/**
 * Demonstrates dynamic agent setup — the same generic agent class is configured with different
 * goals and capabilities per request.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/dynamic")
public class DynamicEndpoint {

  public record TaskRequest(String content) {}

  public record TaskResponse(String taskId) {}

  private final ComponentClient componentClient;

  public DynamicEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/summarize")
  public TaskResponse summarize(TaskRequest request) {
    var agentId = UUID.randomUUID().toString();
    var agent = componentClient.forAutonomousAgent(DynamicAgent.class, agentId);

    agent.setup(
      AgentSetup.create()
        .goal("Produce a concise summary of the given content, highlighting key points.")
        .capability(TaskAcceptance.of(DynamicTasks.SUMMARIZE))
    );

    var taskId = agent.runSingleTask(DynamicTasks.SUMMARIZE.instructions(request.content()));
    return new TaskResponse(taskId);
  }

  @Post("/translate")
  public TaskResponse translate(TaskRequest request) {
    var agentId = UUID.randomUUID().toString();
    var agent = componentClient.forAutonomousAgent(DynamicAgent.class, agentId);

    agent.setup(
      AgentSetup.create()
        .goal("Translate the given content to French, preserving tone and meaning.")
        .capability(TaskAcceptance.of(DynamicTasks.TRANSLATE))
    );

    var taskId = agent.runSingleTask(DynamicTasks.TRANSLATE.instructions(request.content()));
    return new TaskResponse(taskId);
  }
}

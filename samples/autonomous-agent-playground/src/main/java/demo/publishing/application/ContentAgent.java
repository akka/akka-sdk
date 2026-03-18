package demo.publishing.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.publishing.application.PublishingTasks;

@Component(id = "content-agent")
public class ContentAgent extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(PublishingTasks.ARTICLE)
      .instructions(
        """
        You are a content publishing agent. When given a topic:
        1. Research the topic using the researchTopic tool
        2. Write a draft article using the writeDraft tool
        3. Complete the task with an Article result containing \
           the title, full article content, and status="draft"
        """
      )
      .tools(ContentTools.class)
      .maxIterations(10);
  }
}

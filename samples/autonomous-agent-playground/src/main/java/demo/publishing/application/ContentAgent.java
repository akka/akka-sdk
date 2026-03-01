package demo.publishing.application;

import static akka.javasdk.agent.autonomous.AutonomousAgent.externalInput;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.publishing.application.PublishingTasks;

@Component(id = "content-agent")
public class ContentAgent extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(PublishingTasks.ARTICLE)
      .capability(externalInput())
      .instructions(
        """
        You are a content publishing agent. When given a topic:
        1. Research the topic using the researchTopic tool
        2. Write a draft article using the writeDraft tool
        3. Request editorial approval using the requestDecision tool. \
           Include a brief summary of the article in the question. \
           Use "approval" as the decision type.
        4. Wait for the decision response. When you resume:
           - If the response indicates approval, complete the task with \
             an Article result (title, content, status="published")
           - If the response indicates rejection with feedback, revise the \
             article based on the feedback, then request approval again\
        """
      )
      .tools(ContentTools.class)
      .maxIterations(10);
  }
}

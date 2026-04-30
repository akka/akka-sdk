/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.ContentLoader;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component(id = "content-loader-test-agent", description = "Test agent for content loader")
public class ContentLoaderTestAgent extends AutonomousAgent {

  public static final AtomicInteger loaderCalls = new AtomicInteger(0);
  public static final List<String> loadedUrls =
      Collections.synchronizedList(new java.util.ArrayList<>());

  public static void reset() {
    loaderCalls.set(0);
    loadedUrls.clear();
  }

  @Override
  public AgentDefinition definition() {
    return define()
        .capability(TaskAcceptance.of(TestTasks.STRING_TASK).maxIterationsPerTask(1))
        .contentLoader(new RecordingContentLoader());
  }

  static class RecordingContentLoader implements ContentLoader {
    @Override
    public LoadedContent load(MessageContent.LoadableMessageContent content) {
      loaderCalls.incrementAndGet();
      if (content instanceof MessageContent.ImageUrlMessageContent img) {
        loadedUrls.add(img.url().toString());
      } else if (content instanceof MessageContent.PdfUrlMessageContent pdf) {
        loadedUrls.add(pdf.url().toString());
      }
      return new LoadedContent(
          new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}, Optional.of("image/png"));
    }
  }
}

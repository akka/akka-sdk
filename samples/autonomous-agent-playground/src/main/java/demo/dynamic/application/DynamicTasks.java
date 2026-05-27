package demo.dynamic.application;

import akka.javasdk.agent.task.Task;

public class DynamicTasks {

  public static final Task<String> SUMMARIZE = Task.name("Summarize")
    .description("Summarize the given content")
    .resultConformsTo(String.class);

  public static final Task<String> TRANSLATE = Task.name("Translate")
    .description("Translate content to another language")
    .resultConformsTo(String.class);
}

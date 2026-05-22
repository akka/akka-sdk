package demo.consulting.application;

import akka.javasdk.agent.task.Task;

// tag::class[]
public class ConsultingTasks {

  public record ConsultingResult(
    String assessment,
    String recommendation,
    boolean escalated
  ) {}

  public record ResearchSummary(String topic, String findings) {}

  // prettier-ignore
  public static final Task<ConsultingResult> ENGAGEMENT = Task
    .name("Engagement")
    .description("Consulting engagement — assess a client problem and deliver a recommendation")
    .resultConformsTo(ConsultingResult.class);

  // prettier-ignore
  public static final Task<ResearchSummary> RESEARCH = Task
    .name("Research")
    .description("Research a specific aspect of a client problem")
    .resultConformsTo(ResearchSummary.class);
}
// end::class[]

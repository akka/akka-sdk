package demo.consulting.application;

import akka.javasdk.agent.task.Task;
import demo.consulting.domain.ConsultingResult;
import demo.consulting.domain.ResearchSummary;

public class ConsultingTasks {

  public static final Task<ConsultingResult> ENGAGEMENT = Task
      .define("Engagement")
      .description("A consulting engagement to assess a client problem and deliver a recommendation")
      .resultConformsTo(ConsultingResult.class);

  public static final Task<ResearchSummary> RESEARCH = Task
      .define("Research")
      .description("Targeted research on a specific aspect of a consulting problem")
      .resultConformsTo(ResearchSummary.class);
}

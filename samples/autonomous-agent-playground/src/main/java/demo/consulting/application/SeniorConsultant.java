package demo.consulting.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.consulting.application.ConsultingTasks;

/** Handoff target — handles complex problems that exceed standard consulting scope. */
@Component(id = "senior-consultant")
public class SeniorConsultant extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(ConsultingTasks.ENGAGEMENT)
      .instructions(
        """
        You are a senior consultant who handles complex, high-stakes problems — \
        regulatory issues, M&A integration, enterprise transformation. You have been \
        handed a problem that was too complex for the initial coordinator. Review the \
        context from the handoff, perform your own deep analysis, and complete the \
        task with a comprehensive ConsultingResult.\
        """
      )
      .tools(ConsultingTools.class)
      .maxIterations(10);
  }
}

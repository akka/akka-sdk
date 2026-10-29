package demo.multiagent.application;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;

import akka.javasdk.testkit.TestModelProvider;
import java.util.List;

/** Scripts for the mocked models of the coordinator and its workers. */
public final class CoordinatorModels {

  public static final String ANSWER =
    "The weather in Stockholm is sunny, so you can enjoy outdoor activities like " +
    "a bike tour around Djurgården Park, visiting the Vasa Museum, exploring Gamla Stan.";

  private CoordinatorModels() {}

  /**
   * The coordinator delegates to both workers on a message about Stockholm, and completes the task
   * on the follow-up prompt of the runtime.
   */
  public static void scriptStockholm(
    TestModelProvider coordinatorModel,
    TestModelProvider weatherModel,
    TestModelProvider activitiesModel
  ) {
    coordinatorModel
      .whenMessage(msg -> msg.contains("Stockholm"))
      .reply(
        List.of(
          delegateTo(
            WeatherAgent.class,
            "{\"request\":\"What is the current weather in Stockholm?\"}"
          ),
          delegateTo(
            ActivityAgent.class,
            "{\"request\":{\"userId\":\"alice\",\"message\":\"Suggest activities to do in Stockholm considering the current weather.\"}}"
          )
        )
      );

    // the runtime sends "Continue working ..." or "Reminder ..." prompts after the workers complete
    coordinatorModel
      .whenMessage(msg -> msg.contains("Continue working") || msg.contains("Reminder"))
      .reply(completeTask(ANSWER));

    weatherModel.fixedResponse("The weather in Stockholm is sunny.");

    activitiesModel.fixedResponse(
      "You can take a bike tour around Djurgården Park, " +
      "visit the Vasa Museum, explore Gamla Stan (Old Town)..."
    );
  }
}

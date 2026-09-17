/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What a tool evaluator reports when it finds no call under the name it evaluates. */
class EvaluatorsToolNameTest {

  private static final EvalCase CASE = EvalCase.of("c", "drive opportunity");
  private static final String CHECK_NAME = "make sure the evaluated name is the registered tool";

  private static ToolCall called(String name) {
    return new ToolCall(
        name, Map.of("roverId", "opportunity"), Optional.of("ok"), Optional.empty());
  }

  private static Interaction turn(ToolCall... calls) {
    return new Interaction("drive opportunity", "", List.of(calls));
  }

  @Test
  void toolsNamesWhatWasCalledAndTellsYouToCheckTheName() {
    var result =
        Evaluators.shouldCallTools("getRoverStatus")
            .evaluate(CASE, turn(called("MissionControlAgent_getRoverStatus")));
    assertThat(result.detail())
        .isEqualTo(
            "never called [getRoverStatus]; called [MissionControlAgent_getRoverStatus];"
                + " make sure the evaluated name is the registered tool name, which is"
                + " <Class>_<method> unless @FunctionTool sets a name");
  }

  @Test
  void toolOrderNamesWhatWasCalled() {
    var result =
        Evaluators.shouldCallToolsInOrder("planRoutes", "startDrive")
            .evaluate(CASE, turn(called("planRoutes")));
    assertThat(result.detail())
        .contains("never called [startDrive]; called [planRoutes]")
        .contains(CHECK_NAME);
  }

  @Test
  void toolArgumentNamesWhatWasCalled() {
    var result =
        Evaluators.shouldCallToolWith("getRoverStatus", "roverId", "opportunity")
            .evaluate(CASE, turn(called("planRoutes")));
    assertThat(result.detail())
        .contains("getRoverStatus was never called; called [planRoutes]")
        .contains(CHECK_NAME);
  }

  @Test
  void toolResultNamesWhatWasCalled() {
    var result =
        Evaluators.toolResultShouldContain("getRoverStatus", "driving")
            .evaluate(CASE, turn(called("planRoutes")));
    assertThat(result.detail())
        .contains("getRoverStatus was never called; called [planRoutes]")
        .contains(CHECK_NAME);
  }

  @Test
  void toolResultDoesNotAskAboutTheNameWhenTheToolWasCalled() {
    var noResult =
        new ToolCall("getRoverStatus", Map.of(), Optional.empty(), Optional.of("rover is offline"));
    var result =
        Evaluators.toolResultShouldContain("getRoverStatus", "driving")
            .evaluate(CASE, turn(noResult));
    assertThat(result.detail()).isEqualTo("getRoverStatus has no recorded result");
  }

  @Test
  void aTurnWithNoToolCallSaysSo() {
    var result = Evaluators.shouldCallTools("getRoverStatus").evaluate(CASE, turn());
    assertThat(result.detail())
        .contains("never called [getRoverStatus]; called no tools")
        .contains(CHECK_NAME);
  }
}

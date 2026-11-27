/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.agent.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskRule;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TaskTest {

  public record Summary(String text) {}

  public static class SummaryRule implements TaskRule<Summary> {
    @Override
    public TaskRule.Result onComplete(Summary result) {
      return new TaskRule.Result.Accepted();
    }
  }

  public static class TextRule implements TaskRule<String> {
    @Override
    public TaskRule.Result onComplete(String result) {
      return new TaskRule.Result.Accepted();
    }
  }

  private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

  private static final MessageContent IMAGE =
      MessageContent.ImageMessageContent.fromUri("https://example.com/image.png");

  @Test
  public void shouldHaveNoExpiryByDefault() {
    assertThat(Task.name("task").expireAfter()).isEmpty();
  }

  @Test
  public void shouldKeepExpiryThroughEveryBuilderMethod() {
    var task = Task.name("task").expireAfter(TEN_MINUTES);

    assertThat(task.expireAfter()).contains(TEN_MINUTES);
    assertThat(task.description("d").expireAfter()).contains(TEN_MINUTES);
    assertThat(task.instructions("i").expireAfter()).contains(TEN_MINUTES);
    assertThat(task.attach(IMAGE).expireAfter()).contains(TEN_MINUTES);
    assertThat(task.dependsOn("other-task").expireAfter()).contains(TEN_MINUTES);
    assertThat(task.rules(TextRule.class).expireAfter()).contains(TEN_MINUTES);
    assertThat(task.resultConformsTo(Summary.class).expireAfter()).contains(TEN_MINUTES);
  }

  @Test
  public void shouldKeepOtherFieldsWhenExpiryIsSetLast() {
    var task =
        Task.name("task")
            .description("d")
            .resultConformsTo(Summary.class)
            .instructions("i")
            .attach(IMAGE)
            .dependsOn("other-task")
            .rules(SummaryRule.class)
            .expireAfter(TEN_MINUTES);

    assertThat(task.name()).isEqualTo("task");
    assertThat(task.description()).isEqualTo("d");
    assertThat(task.resultType()).isEqualTo(Summary.class);
    assertThat(task.instructions()).isEqualTo("i");
    assertThat(task.attachments()).containsExactly(IMAGE);
    assertThat(task.dependencyTaskIds()).containsExactly("other-task");
    assertThat(task.ruleClasses()).isEqualTo(List.of(SummaryRule.class));
    assertThat(task.expireAfter()).contains(TEN_MINUTES);
  }

  @Test
  public void shouldRejectExpiryThatIsNotPositive() {
    var task = Task.name("task");
    assertThatThrownBy(() -> task.expireAfter(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> task.expireAfter(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> task.expireAfter(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectExpiryThatEndsAfterTheYear9999() {
    var task = Task.name("task");
    assertThatThrownBy(() -> task.expireAfter(ChronoUnit.FOREVER.getDuration()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("before the year 10000");
    assertThatThrownBy(() -> task.expireAfter(Duration.ofDays(Integer.MAX_VALUE)))
        .isInstanceOf(IllegalArgumentException.class);

    var thousandYears = Duration.ofDays(365L * 1000);
    assertThat(task.expireAfter(thousandYears).expireAfter()).contains(thousandYears);
  }
}

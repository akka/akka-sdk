/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.Done;
import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static akka.Done.done;
import static org.assertj.core.api.Assertions.assertThat;

public class PromptTemplateTest {

  @Test
  public void shouldAllowToInitPromptOnlyOnce() {
    //given
    var testKit = EventSourcedTestKit.of(PromptTemplate::new);
    String promptValue = "prompt-1";

    //when
    EventSourcedResult<Done> result = testKit.method(PromptTemplate::init).invoke(promptValue);

    //then
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.getAllEvents()).containsExactly(new PromptTemplate.Event.Updated(promptValue));
    assertThat(result.getUpdatedState()).isEqualTo(new PromptTemplate.Prompt(promptValue));

    //when
    EventSourcedResult<Done> secondInit = testKit.method(PromptTemplate::init).invoke("next-prompt");

    //then
    assertThat(secondInit.getReply()).isEqualTo(done());
    assertThat(secondInit.didPersistEvents()).isFalse();
    assertThat(secondInit.getUpdatedState()).isEqualTo(new PromptTemplate.Prompt(promptValue));
  }

  @Test
  public void shouldGetPrompt() {
    //given
    var testKit = EventSourcedTestKit.of(PromptTemplate::new);
    String promptValue = "prompt-1";

    {
      //when
      EventSourcedResult<String> result = testKit.method(PromptTemplate::get).invoke();
      EventSourcedResult<Optional<String>> resultOpt = testKit.method(PromptTemplate::getOptional).invoke();

      //then
      assertThat(result.isError()).isTrue();
      assertThat(resultOpt.getReply()).isEqualTo(Optional.empty());
    }

    {
      //when
      testKit.method(PromptTemplate::init).invoke(promptValue);
      EventSourcedResult<String> result = testKit.method(PromptTemplate::get).invoke();
      EventSourcedResult<Optional<String>> resultOpt = testKit.method(PromptTemplate::getOptional).invoke();

      //then
      assertThat(result.getReply()).isEqualTo(promptValue);
      assertThat(resultOpt.getReply()).isEqualTo(Optional.of(promptValue));
    }

    {
      //when
      testKit.method(PromptTemplate::init).invoke(promptValue);
      testKit.method(PromptTemplate::delete).invoke();
      EventSourcedResult<String> result = testKit.method(PromptTemplate::get).invoke();
      EventSourcedResult<Optional<String>> resultOpt = testKit.method(PromptTemplate::getOptional).invoke();

      //then
      assertThat(result.isError()).isTrue();
      assertThat(resultOpt.getReply()).isEqualTo(Optional.empty());
    }
  }

  @Test
  public void shouldEditPrompt() {
    //given
    var testKit = EventSourcedTestKit.of(PromptTemplate::new);
    String promptValue1 = "prompt-1";
    String promptValue2 = "prompt-2";

    EventSourcedResult<Done> result1 = testKit.method(PromptTemplate::init).invoke(promptValue1);
    assertThat(result1.getAllEvents()).containsExactly(new PromptTemplate.Event.Updated(promptValue1));
    assertThat(result1.getUpdatedState()).isEqualTo(new PromptTemplate.Prompt(promptValue1));

    EventSourcedResult<Done> result2 = testKit.method(PromptTemplate::update).invoke(promptValue2);
    assertThat(result2.getAllEvents()).containsExactly(new PromptTemplate.Event.Updated(promptValue2));
    assertThat(result2.getUpdatedState()).isEqualTo(new PromptTemplate.Prompt(promptValue2));

    //updating with the same value
    EventSourcedResult<Done> result3 = testKit.method(PromptTemplate::update).invoke(promptValue2);
    assertThat(result3.didPersistEvents()).isFalse();
    assertThat(result3.getUpdatedState()).isEqualTo(new PromptTemplate.Prompt(promptValue2));

    //wrong value
    EventSourcedResult<Done> result4 = testKit.method(PromptTemplate::update).invoke("");
    assertThat(result4.isError()).isTrue();
    assertThat(result4.getUpdatedState()).isEqualTo(new PromptTemplate.Prompt(promptValue2));
  }
}

package demo.activities.application;

import akka.javasdk.decision.Answer;
import akka.javasdk.decision.Question;

/** The questions that the workflow asks the decision model. */
public final class ActivityQuestions {

  private ActivityQuestions() {}

  // Asked about the user's message, to choose the next step.

  public static final Question<Answer.Choice> TOPIC = Question.choice(
    "What does the user ask for?"
  )
    .option(
      "activities",
      "Suggestions for things to do, such as sports, games, trips or team events"
    )
    .option("weather", "Only the weather or a forecast, without asking what to do")
    .option("other", "Something that is not about activities or the weather")
    .build("topic");

  public static final Question<Answer.Predicate> NEEDS_WEATHER = Question.predicate(
    "Do good activity suggestions for this message depend on the current weather?"
  )
    .whenTrue("The user names a place, or asks for outdoor activities")
    .whenFalse("The user asks for indoor activities only, or names no place")
    .build("needs-weather");

  // Asked about the suggestion of the activity agent, to accept it or ask again.

  public static final Question<Answer.Score> QUALITY = Question.score(
    "How well does the suggestion answer the user's request?"
  )
    .levels(
      "It does not answer the request",
      "It answers part of the request, or ignores the weather",
      "It answers the whole request"
    )
    .build("quality");

  public static final Question<Answer.Predicate> RESPECTS_PREFERENCES = Question.predicate(
    "Does the suggestion respect every preference of the user?"
  )
    .whenTrue("No suggested activity conflicts with a preference")
    .whenFalse("At least one suggested activity conflicts with a preference")
    .build("respects-preferences");
}

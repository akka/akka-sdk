/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.decision

import java.util.Objects
import java.util.concurrent.CompletionStage
import java.{ util => ju }

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.http.scaladsl.model.HttpHeader
import akka.javasdk.agent.InternalServerException
import akka.javasdk.agent.ModelException
import akka.javasdk.agent.ModelTimeoutException
import akka.javasdk.agent.RateLimitException
import akka.javasdk.decision.Answer
import akka.javasdk.decision.Decision
import akka.javasdk.decision.DecisionModelClient
import akka.javasdk.decision.DecisionModelProvider
import akka.javasdk.decision.Question
import akka.javasdk.impl.ErrorHandling.unwrapExecutionExceptionCatcher
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiDecision
import akka.runtime.sdk.spi.SpiDecisionModelClient
import com.typesafe.config.Config
import io.opentelemetry.context.{ Context => OtelContext }

/**
 * INTERNAL API: Makes it possible to replace the decision model provider when running tests.
 */
@InternalApi
private[javasdk] final class OverrideDecisionModelProvider {
  @volatile private var provider: Option[DecisionModelProvider] = None

  def set(modelProvider: DecisionModelProvider): Unit =
    provider = Some(modelProvider)

  def get: Option[DecisionModelProvider] = provider
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object DecisionModelClientImpl {

  private val ConfigPrefix = "akka.javasdk.decision"

  // limits of the SystemOne API, checked before the call so that the failure names the question
  private val MaxChoiceOptions = 255
  private val MaxScoreLevels = 10

  def providerFromConfig(config: Config, configPath: String): DecisionModelProvider.SystemOne = {
    val actualPath =
      if (configPath == "") config.getString(s"$ConfigPrefix.model-provider")
      else configPath

    if (actualPath == "")
      throw new IllegalArgumentException(
        s"You must define the decision model provider configuration in [$ConfigPrefix.model-provider]")

    val resolvedPath =
      if (config.hasPath(actualPath)) actualPath
      else if (!actualPath.contains('.') && config.hasPath(s"$ConfigPrefix.$actualPath")) s"$ConfigPrefix.$actualPath"
      else throw new IllegalArgumentException(s"Undefined decision model provider configuration [$actualPath]")

    val providerConfig = config.getConfig(resolvedPath)
    providerConfig.getString("provider") match {
      case "system-one" => DecisionModelProvider.SystemOne.fromConfig(providerConfig)
      case other =>
        throw new IllegalArgumentException(s"Unknown decision model provider [$other] in config [$resolvedPath]")
    }
  }

  def toSpiProvider(provider: DecisionModelProvider.SystemOne): SpiDecision.ModelProvider = {
    if (provider.modelName.isBlank)
      throw new IllegalArgumentException("The decision model provider needs a model name")
    if (provider.apiKey.isBlank)
      throw new IllegalArgumentException(
        "The decision model provider needs an API key. Set [akka.javasdk.decision.system-one.api-key] " +
        "or the SYSTEM_ONE_API_KEY environment variable.")
    new SpiDecision.SystemOne(
      apiKey = provider.apiKey,
      modelName = provider.modelName,
      baseUrl = provider.baseUrl,
      modelSettings = new SpiAgent.ModelSettings(
        provider.connectionTimeout.toScala,
        provider.responseTimeout.toScala,
        provider.maxRetries,
        provider.additionalModelRequestHeaders.asScala.map(_.asInstanceOf[HttpHeader]).toSeq,
        identityHeaders = false))
  }

  def toSpiQuestion(question: Question[_]): SpiDecision.Question =
    question match {
      case q: Question.Choice =>
        if (q.options.size > MaxChoiceOptions)
          throw new IllegalArgumentException(
            s"A SystemOne choice question supports at most [$MaxChoiceOptions] options, " +
            s"question [${q.key}] has [${q.options.size}]")
        new SpiDecision.ChoiceQuestion(
          q.key,
          q.instructions,
          q.options.asScala.map(o => new SpiDecision.ChoiceOption(o.key, o.description)).toSeq)
      case q: Question.Score =>
        if (q.levels.size > MaxScoreLevels)
          throw new IllegalArgumentException(
            s"A SystemOne score question supports at most [$MaxScoreLevels] levels, " +
            s"question [${q.key}] has [${q.levels.size}]")
        new SpiDecision.ScoreQuestion(q.key, q.instructions, q.levels.asScala.toSeq)
      case q: Question.YesNo =>
        new SpiDecision.PredicateQuestion(q.key, q.instructions, q.whenYes.toScala, q.whenNo.toScala)
    }

  // the runtime fails the call when an answer is missing or does not match its question
  def toDecision(response: SpiDecision.Response): Decision = {
    val answers = response.answers.map[String, Answer] {
      case (key, a: SpiDecision.ChoiceAnswer) =>
        key -> new Answer.Choice(
          a.selected,
          a.confidence,
          a.probabilities.map { case (k, p) => k -> Double.box(p) }.asJava)
      case (key, a: SpiDecision.ScoreAnswer) =>
        key -> new Answer.Score(a.value, a.confidence, a.probabilities.map(Double.box).asJava)
      case (key, a: SpiDecision.PredicateAnswer) =>
        key -> new Answer.YesNo(a.probability)
    }
    new Decision(response.model, answers.asJava)
  }

  /**
   * Checks that a custom provider answered every question with an answer that matches the question, as a real call
   * does. Answers for questions that were not asked are dropped.
   */
  def checkDecision(decision: Decision, questions: Seq[Question[_]]): Decision = {
    val answers = new ju.LinkedHashMap[String, Answer]
    questions.foreach { question =>
      val answer =
        try decision.answer(question)
        catch {
          case e: IllegalArgumentException => throw new ModelException(e.getMessage)
        }
      checkAnswer(question, answer)
      answers.put(question.key, answer)
    }
    new Decision(decision.model, answers)
  }

  // range checks that a real call gets from the response validation of the runtime,
  // the option and type checks are in Decision.answer
  private def checkAnswer(question: Question[_], answer: Answer): Unit =
    answer match {
      case _: Answer.Choice =>
      case s: Answer.Score =>
        val score = question.asInstanceOf[Question.Score]
        if (s.value < 0 || s.value > score.levels.size - 1)
          throw new ModelException(
            s"The answer for question [${question.key}] has value [${s.value}], " +
            s"outside the levels of the question, 0 to ${score.levels.size - 1}")
      case y: Answer.YesNo =>
        if (y.probability < 0 || y.probability > 1)
          throw new ModelException(
            s"The answer for question [${question.key}] has probability [${y.probability}], outside 0 to 1")
    }

  def checkQuestions(questions: Seq[Question[_]]): Seq[Question[_]] = {
    if (questions.isEmpty) throw new IllegalArgumentException("A decision request needs at least one question")
    questions.foreach(q => Objects.requireNonNull(q, "question"))
    val duplicates = questions.groupBy(_.key).collect { case (key, qs) if qs.size > 1 => key }
    if (duplicates.nonEmpty)
      throw new IllegalArgumentException(s"Duplicate question keys [${duplicates.toSeq.sorted.mkString(", ")}]")
    questions
  }

  def checkState(state: Any): Any = {
    Objects.requireNonNull(state, "state")
    state match {
      case s: String =>
        if (s.isBlank)
          throw new IllegalArgumentException("The decision state must not be an empty string")
      case _: java.lang.Number | _: java.lang.Boolean | _: Array[Byte] =>
        throw new IllegalArgumentException(
          s"The decision state type [${state.getClass.getName}] is not supported, because it does not " +
          "serialize to JSON text, an object or an array. Send a String, or wrap the value in a record.")
      case _ =>
    }
    state
  }

  def mapSpiException(exc: Throwable): Throwable =
    exc match {
      case e: SpiAgent.AgentException =>
        e.reason match {
          case SpiAgent.RateLimitFailure => new RateLimitException(e.getMessage)
          case SpiAgent.TimeoutFailure   => new ModelTimeoutException(e.getMessage)
          case SpiAgent.InternalFailure  => new InternalServerException(e.getMessage)
          case _                         => new ModelException(e.getMessage)
        }
      case other => other
    }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class DecisionModelClientImpl(
    spiClient: SpiDecisionModelClient,
    serializer: JsonSerializer,
    config: Config,
    overrideProvider: OverrideDecisionModelProvider,
    provider: Option[DecisionModelProvider],
    telemetryContext: Option[OtelContext])(implicit ec: ExecutionContext)
    extends DecisionModelClient {
  import DecisionModelClientImpl._

  override def model(provider: DecisionModelProvider): DecisionModelClient =
    new DecisionModelClientImpl(
      spiClient,
      serializer,
      config,
      overrideProvider,
      Some(Objects.requireNonNull(provider, "provider")),
      telemetryContext)

  def withTelemetryContext(context: OtelContext): DecisionModelClientImpl =
    new DecisionModelClientImpl(spiClient, serializer, config, overrideProvider, provider, Some(context))

  override def state(state: Any): DecisionModelClient.StateRequest =
    new StateRequestImpl(checkState(state))

  private final class StateRequestImpl(state: Any) extends DecisionModelClient.StateRequest {
    override def questions(questions: Question[_]*): DecisionModelClient.Request =
      new RequestImpl(state, checkQuestions(questions.toVector))

    override def questions(questions: ju.Collection[Question[_]]): DecisionModelClient.Request =
      new RequestImpl(state, checkQuestions(Objects.requireNonNull(questions, "questions").asScala.toVector))
  }

  private final class RequestImpl(state: Any, questions: Seq[Question[_]]) extends DecisionModelClient.Request {

    override def invoke(): Decision =
      try invokeAsync().toCompletableFuture.get()
      catch unwrapExecutionExceptionCatcher

    override def invokeAsync(): CompletionStage[Decision] = decide(state, questions).asJava
  }

  private def decide(state: Any, questions: Seq[Question[_]]): Future[Decision] =
    try {
      overrideProvider.get.orElse(provider).getOrElse(DecisionModelProvider.fromConfig()) match {
        case p: DecisionModelProvider.Custom =>
          val request = new DecisionModelProvider.DecisionRequest(state, questions.asJava)
          Future(checkDecision(p.decide(request), questions))
        case p: DecisionModelProvider.SystemOne => decideWithRuntime(p, state, questions)
        case p: DecisionModelProvider.FromConfig =>
          val resolved = providerFromConfig(config, p.configPath)
          val withHeaders =
            if (p.additionalModelRequestHeaders.isEmpty) resolved
            else {
              val headers = new ju.ArrayList[akka.http.javadsl.model.HttpHeader](resolved.additionalModelRequestHeaders)
              headers.addAll(p.additionalModelRequestHeaders)
              resolved.withAdditionalModelRequestHeaders(headers)
            }
          decideWithRuntime(withHeaders, state, questions)
      }
    } catch {
      case NonFatal(e) => Future.failed(e)
    }

  private def decideWithRuntime(
      provider: DecisionModelProvider.SystemOne,
      state: Any,
      questions: Seq[Question[_]]): Future[Decision] = {
    val stateJson =
      try serializer.toJsonString(state)
      catch {
        case NonFatal(exc) =>
          throw new IllegalArgumentException(
            s"The decision state of type [${state.getClass.getName}] cannot be serialized to JSON",
            exc)
      }
    val request = new SpiDecision.Request(stateJson, questions.map(toSpiQuestion))
    spiClient.decide(toSpiProvider(provider), request, telemetryContext).transform(toDecision, mapSpiException)
  }
}

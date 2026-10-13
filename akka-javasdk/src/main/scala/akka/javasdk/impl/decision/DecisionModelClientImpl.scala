/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.decision

import java.util.Objects
import java.util.concurrent.CompletionStage

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
import akka.javasdk.impl.ErrorHandling.unwrapExecutionExceptionCatcher
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.decision.Answer
import akka.javasdk.decision.ChoiceAnswer
import akka.javasdk.decision.Decision
import akka.javasdk.decision.DecisionModelClient
import akka.javasdk.decision.DecisionModelProvider
import akka.javasdk.decision.Question
import akka.javasdk.decision.ScoreAnswer
import akka.javasdk.decision.YesNoAnswer
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiJudgment
import akka.runtime.sdk.spi.SpiJudgmentClient
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

  def toSpiProvider(provider: DecisionModelProvider.SystemOne): SpiJudgment.ModelProvider = {
    if (provider.modelName.isBlank)
      throw new IllegalArgumentException("The decision model provider needs a model name")
    new SpiJudgment.SystemOne(
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

  def toSpiQuestion(question: Question[_]): SpiJudgment.Question =
    question match {
      case q: Question.Choice =>
        new SpiJudgment.ChoiceQuestion(
          q.key,
          q.instructions,
          q.options.asScala.map(o => new SpiJudgment.ChoiceOption(o.key, o.description)).toSeq)
      case q: Question.Score =>
        new SpiJudgment.ScoreQuestion(q.key, q.instructions, q.levels.asScala.toSeq)
      case q: Question.YesNo =>
        new SpiJudgment.YesNoQuestion(q.key, q.instructions, q.whenYes.toScala, q.whenNo.toScala)
    }

  // the runtime fails the call when an answer is missing or does not match its question
  def toDecision(response: SpiJudgment.Response): Decision = {
    val answers = response.answers.map[String, Answer] {
      case (key, a: SpiJudgment.ChoiceAnswer) =>
        key -> new ChoiceAnswer(
          a.selected,
          a.confidence,
          a.probabilities.map { case (k, p) => k -> Double.box(p) }.asJava)
      case (key, a: SpiJudgment.ScoreAnswer) =>
        key -> new ScoreAnswer(a.value, a.confidence, a.probabilities.map(Double.box).asJava)
      case (key, a: SpiJudgment.YesNoAnswer) =>
        key -> new YesNoAnswer(a.probability)
    }
    new Decision(response.model, answers.asJava)
  }

  /** Checks that a custom provider answered every question with an answer of its type. */
  def checkDecision(decision: Decision, questions: Seq[Question[_]]): Decision = {
    questions.foreach { question =>
      try decision.answer(question)
      catch {
        case e: IllegalArgumentException => throw new ModelException(e.getMessage)
      }
    }
    decision
  }

  def checkQuestions(questions: Seq[Question[_]]): Seq[Question[_]] = {
    if (questions.isEmpty) throw new IllegalArgumentException("A decision request needs at least one question")
    questions.foreach(q => Objects.requireNonNull(q, "question"))
    val duplicates = questions.groupBy(_.key).collect { case (key, qs) if qs.size > 1 => key }
    if (duplicates.nonEmpty)
      throw new IllegalArgumentException(s"Duplicate question keys [${duplicates.toSeq.sorted.mkString(", ")}]")
    questions
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
    spiClient: SpiJudgmentClient,
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

  override def state(state: Any): DecisionModelClient.StateRequest = {
    Objects.requireNonNull(state, "state")
    new StateRequestImpl(state)
  }

  private final class StateRequestImpl(state: Any) extends DecisionModelClient.StateRequest {
    override def questions(questions: Question[_]*): DecisionModelClient.Request =
      new RequestImpl(state, checkQuestions(questions.toVector))
  }

  private final class RequestImpl(state: Any, questions: Seq[Question[_]]) extends DecisionModelClient.Request {

    override def invoke(): Decision =
      try invokeAsync().toCompletableFuture.get()
      catch unwrapExecutionExceptionCatcher

    override def invokeAsync(): CompletionStage[Decision] = judge(state, questions).asJava
  }

  private def judge(state: Any, questions: Seq[Question[_]]): Future[Decision] =
    try {
      overrideProvider.get.orElse(provider).getOrElse(DecisionModelProvider.fromConfig()) match {
        case p: DecisionModelProvider.Custom =>
          val request = new DecisionModelProvider.Request(state, questions.asJava)
          Future(checkDecision(p.judge(request), questions))
        case p: DecisionModelProvider.SystemOne => judgeWithRuntime(p, state, questions)
        case p: DecisionModelProvider.FromConfig =>
          judgeWithRuntime(providerFromConfig(config, p.configPath), state, questions)
      }
    } catch {
      case NonFatal(e) => Future.failed(e)
    }

  private def judgeWithRuntime(
      provider: DecisionModelProvider.SystemOne,
      state: Any,
      questions: Seq[Question[_]]): Future[Decision] = {
    val request = new SpiJudgment.Request(serializer.toJsonString(state), questions.map(toSpiQuestion))
    spiClient.judge(toSpiProvider(provider), request, telemetryContext).transform(toDecision, mapSpiException)
  }
}

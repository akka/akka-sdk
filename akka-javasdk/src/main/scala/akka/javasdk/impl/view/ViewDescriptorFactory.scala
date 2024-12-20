/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.annotations.Consume
import akka.javasdk.annotations.Query
import akka.javasdk.annotations.Table
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.view.TableUpdater
import akka.javasdk.view.UpdateContext
import akka.javasdk.view.View
import akka.javasdk.view.View.QueryStreamEffect
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.ConsumerSource
import akka.runtime.sdk.spi.MethodOptions
import akka.runtime.sdk.spi.views.SpiQueryDescriptor
import akka.runtime.sdk.spi.views.SpiTableDescriptor
import akka.runtime.sdk.spi.views.SpiTableUpdateEffect
import akka.runtime.sdk.spi.views.SpiTableUpdateEnvelope
import akka.runtime.sdk.spi.views.SpiTableUpdateHandler
import akka.runtime.sdk.spi.views.SpiType
import akka.runtime.sdk.spi.views.SpiType.SpiClass
import akka.runtime.sdk.spi.views.SpiType.SpiField
import akka.runtime.sdk.spi.views.SpiType.SpiList
import akka.runtime.sdk.spi.views.SpiViewDescriptor
import org.slf4j.LoggerFactory
import org.slf4j.MDC

import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.Optional
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.matching.Regex

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ViewDescriptorFactory {

  val TableNamePattern: Regex = """FROM\s+`?([A-Za-z][A-Za-z0-9_]*)""".r

  def apply(viewClass: Class[_], serializer: JsonSerializer, userEc: ExecutionContext): SpiViewDescriptor = {
    val componentId = ComponentDescriptorFactory.readComponentIdIdValue(viewClass)

    val tableUpdaters =
      viewClass.getDeclaredClasses.toSeq.filter(Reflect.isViewTableUpdater)

    val allQueryMethods = extractQueryMethods(viewClass)
    val allQueryStrings = allQueryMethods.map(_.queryString)

    val tables: Seq[SpiTableDescriptor] =
      tableUpdaters
        .map { tableUpdaterClass =>
          // View class type parameter declares table type
          val tableRowClass: Class[_] =
            tableUpdaterClass.getGenericSuperclass
              .asInstanceOf[ParameterizedType]
              .getActualTypeArguments
              .head match {
              case clazz: Class[_] => clazz
              case other =>
                throw new IllegalArgumentException(
                  s"Expected [$tableUpdaterClass] to extends TableUpdater[] for a concrete table row type but cannot figure out type parameter because type argument is unexpected [$other] ")
            }

          val tableName: String = {
            if (tableUpdaters.size > 1) {
              // explicitly annotated since multiple view table updaters
              tableUpdaterClass.getAnnotation(classOf[Table]).value()
            } else {
              // figure out from first query
              val query = allQueryStrings.headOption.getOrElse(
                throw new IllegalArgumentException(
                  s"View [$componentId] does not have any queries defined, must have at least one query"))
              TableNamePattern
                .findFirstMatchIn(query)
                .map(_.group(1))
                .getOrElse(throw new RuntimeException(s"Could not extract table name from query [${query}]"))
            }
          }

          val tableType = ViewSchema(tableRowClass) match {
            case spiClass: SpiClass => spiClass
            case _ =>
              throw new IllegalArgumentException(
                s"Table type must be a class but was [$tableRowClass] for table updater [$tableUpdaterClass]")
          }

          if (ComponentDescriptorFactory.hasValueEntitySubscription(tableUpdaterClass)) {
            consumeFromKvEntity(componentId, tableUpdaterClass, tableType, tableName, serializer, userEc)
          } else if (ComponentDescriptorFactory.hasEventSourcedEntitySubscription(tableUpdaterClass)) {
            consumeFromEsEntity(componentId, tableUpdaterClass, tableType, tableName, serializer, userEc)
          } else if (ComponentDescriptorFactory.hasTopicSubscription(tableUpdaterClass)) {
            consumeFromTopic(componentId, tableUpdaterClass, tableType, tableName, serializer, userEc)
          } else if (ComponentDescriptorFactory.hasStreamSubscription(tableUpdaterClass)) {
            consumeFromServiceToService(componentId, tableUpdaterClass, tableType, tableName, serializer, userEc)
          } else
            throw new IllegalStateException(s"Table updater [${tableUpdaterClass}] is missing a @Consume annotation")
        }

    new SpiViewDescriptor(
      componentId,
      viewClass.getName,
      tables,
      queries = allQueryMethods.map(_.descriptor),
      // FIXME reintroduce ACLs (does JWT make any sense here? I don't think so)
      componentOptions = new ComponentOptions(None, None))
  }

  private case class QueryMethod(descriptor: SpiQueryDescriptor, queryString: String)

  private def validQueryMethod(method: Method): Boolean =
    method.getAnnotation(classOf[Query]) != null && (method.getReturnType == classOf[
      View.QueryEffect[_]] || method.getReturnType == classOf[View.QueryStreamEffect[_]])

  private def extractQueryMethods(component: Class[_]): Seq[QueryMethod] = {
    val annotatedQueryMethods = component.getDeclaredMethods.toIndexedSeq.filter(validQueryMethod)
    annotatedQueryMethods.map(method =>
      try {
        extractQueryMethod(method)
      } catch {
        case t: Throwable => throw new RuntimeException(s"Failed to extract query for $method", t)
      })
  }

  private def extractQueryMethod(method: Method): QueryMethod = {
    val parameterizedReturnType = method.getGenericReturnType
      .asInstanceOf[java.lang.reflect.ParameterizedType]

    // extract the actual query return type from the generic query effect
    val (actualQueryOutputType, streamingQuery) =
      if (method.getReturnType == classOf[View.QueryEffect[_]]) {
        val unwrapped = parameterizedReturnType.getActualTypeArguments.head match {
          case parameterizedType: ParameterizedType if parameterizedType.getRawType == classOf[Optional[_]] =>
            parameterizedType.getActualTypeArguments.head
          case other => other
        }
        (unwrapped, false)
      } else if (method.getReturnType == classOf[View.QueryStreamEffect[_]]) {
        (parameterizedReturnType.getActualTypeArguments.head, true)
      } else {
        throw new IllegalArgumentException(s"Return type of ${method.getName} is not supported ${method.getReturnType}")
      }

    val actualQueryOutputClass = actualQueryOutputType match {
      case clazz: Class[_] => clazz
      case other =>
        throw new IllegalArgumentException(
          s"Actual query output type for ${method.getName} is not a class (must not be parameterized): $other")
    }

    val queryAnnotation = method.getAnnotation(classOf[Query])
    val queryStr = queryAnnotation.value()
    val streamUpdates = queryAnnotation.streamUpdates()
    if (streamUpdates && !streamingQuery)
      throw new IllegalArgumentException(
        s"Method [${method.getName}] is marked as streaming updates, this requires it to return a ${classOf[
          QueryStreamEffect[_]]}")

    val inputType: Option[SpiType.QueryInput] =
      method.getGenericParameterTypes.headOption.map(ViewSchema.apply(_)).map {
        case validInput: SpiType.QueryInput => validInput
        case other                          =>
          // FIXME let's see if this flies
          // For using primitive parameters directly, using their parameter name as placeholder in the query,
          // we have to make up a valid message with that as a field
          new SpiClass(
            s"SyntheticInputFor${method.getName}",
            Seq(new SpiField(method.getParameters.head.getName, other)))
      }

    val outputType = ViewSchema(actualQueryOutputClass) match {
      case output: SpiType.SpiClass =>
        if (streamingQuery) new SpiList(output)
        else output
      case _ =>
        throw new IllegalArgumentException(
          s"Query return type [${actualQueryOutputClass}] for [${method.getDeclaringClass}.${method.getName}] is not a valid query return type")
    }

    QueryMethod(
      new SpiQueryDescriptor(
        method.getName,
        queryStr,
        inputType,
        outputType,
        streamUpdates,
        // FIXME reintroduce ACLs (does JWT make any sense here? I don't think so)
        new MethodOptions(None, None)),
      queryStr)
  }

  private def consumeFromServiceToService(
      componentId: String,
      tableUpdater: Class[_],
      tableType: SpiClass,
      tableName: String,
      serializer: JsonSerializer,
      userEc: ExecutionContext): SpiTableDescriptor = {
    val annotation = tableUpdater.getAnnotation(classOf[Consume.FromServiceStream])

    val updaterMethods = tableUpdater.getMethods.toIndexedSeq

    val deleteHandlerMethod: Option[Method] = updaterMethods
      .find(ComponentDescriptorFactory.hasHandleDeletes)

    val updateHandlerMethods: Seq[Method] = updaterMethods
      .filterNot(ComponentDescriptorFactory.hasHandleDeletes)
      .filter(ComponentDescriptorFactory.hasUpdateEffectOutput)

    new SpiTableDescriptor(
      tableName,
      tableType,
      new ConsumerSource.ServiceStreamSource(annotation.service(), annotation.id(), annotation.consumerGroup()),
      Option.when(updateHandlerMethods.nonEmpty)(
        UpdateHandlerImpl(
          componentId,
          tableUpdater,
          updateHandlerMethods,
          ignoreUnknown = annotation.ignoreUnknown(),
          serializer = serializer)(userEc)),
      deleteHandlerMethod.map(deleteMethod =>
        UpdateHandlerImpl(
          componentId,
          tableUpdater,
          methods = Seq(deleteMethod),
          serializer = serializer,
          deleteHandler = true)(userEc)))
  }

  private def consumeFromEsEntity(
      componentId: String,
      tableUpdater: Class[_],
      tableType: SpiClass,
      tableName: String,
      serializer: JsonSerializer,
      userEc: ExecutionContext): SpiTableDescriptor = {

    val annotation = tableUpdater.getAnnotation(classOf[Consume.FromEventSourcedEntity])

    val updaterMethods = tableUpdater.getMethods.toIndexedSeq

    val deleteHandlerMethod: Option[Method] = updaterMethods
      .find(ComponentDescriptorFactory.hasHandleDeletes)

    val updateHandlerMethods: Seq[Method] = updaterMethods
      .filterNot(ComponentDescriptorFactory.hasHandleDeletes)
      .filter(ComponentDescriptorFactory.hasUpdateEffectOutput)

    // FIXME input type validation? (does that happen elsewhere?)
    // FIXME method output vs table type validation? (does that happen elsewhere?)

    new SpiTableDescriptor(
      tableName,
      tableType,
      new ConsumerSource.EventSourcedEntitySource(
        ComponentDescriptorFactory.readComponentIdIdValue(annotation.value())),
      Option.when(updateHandlerMethods.nonEmpty)(
        UpdateHandlerImpl(
          componentId,
          tableUpdater,
          updateHandlerMethods,
          serializer,
          ignoreUnknown = annotation.ignoreUnknown())(userEc)),
      deleteHandlerMethod.map(deleteMethod =>
        UpdateHandlerImpl(
          componentId,
          tableUpdater,
          methods = Seq(deleteMethod),
          deleteHandler = true,
          serializer = serializer)(userEc)))
  }

  private def consumeFromTopic(
      componentId: String,
      tableUpdater: Class[_],
      tableType: SpiClass,
      tableName: String,
      serializer: JsonSerializer,
      userEc: ExecutionContext): SpiTableDescriptor = {
    val annotation = tableUpdater.getAnnotation(classOf[Consume.FromTopic])

    val updaterMethods = tableUpdater.getMethods.toIndexedSeq

    val updateHandlerMethods: Seq[Method] = updaterMethods
      .filterNot(ComponentDescriptorFactory.hasHandleDeletes)
      .filter(ComponentDescriptorFactory.hasUpdateEffectOutput)

    // FIXME input type validation? (does that happen elsewhere?)
    // FIXME method output vs table type validation? (does that happen elsewhere?)

    new SpiTableDescriptor(
      tableName,
      tableType,
      new ConsumerSource.TopicSource(annotation.value(), annotation.consumerGroup()),
      Option.when(updateHandlerMethods.nonEmpty)(
        UpdateHandlerImpl(
          componentId,
          tableUpdater,
          updateHandlerMethods,
          serializer,
          ignoreUnknown = annotation.ignoreUnknown())(userEc)),
      None)
  }

  private def consumeFromKvEntity(
      componentId: String,
      tableUpdater: Class[_],
      tableType: SpiClass,
      tableName: String,
      serializer: JsonSerializer,
      userEc: ExecutionContext): SpiTableDescriptor = {
    val annotation = tableUpdater.getAnnotation(classOf[Consume.FromKeyValueEntity])

    val updaterMethods = tableUpdater.getMethods.toIndexedSeq

    // FIXME do we still need this?
    /* val subscriptionVEType = tableUpdater
      .getAnnotation(classOf[FromKeyValueEntity])
      .value()
      .getGenericSuperclass
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments
      .head
      .asInstanceOf[Class[_]] */

    // val transform = subscriptionVEType != tableClass

    val deleteHandlerMethod: Option[Method] = updaterMethods
      .find(ComponentDescriptorFactory.hasHandleDeletes)

    val updateHandlerMethods: Seq[Method] = updaterMethods
      .filterNot(ComponentDescriptorFactory.hasHandleDeletes)
      .filter(ComponentDescriptorFactory.hasUpdateEffectOutput)

    new SpiTableDescriptor(
      tableName,
      tableType,
      new ConsumerSource.KeyValueEntitySource(ComponentDescriptorFactory.readComponentIdIdValue(annotation.value())),
      Option.when(updateHandlerMethods.nonEmpty)(
        UpdateHandlerImpl(componentId, tableUpdater, updateHandlerMethods, serializer)(userEc)),
      deleteHandlerMethod.map(method =>
        UpdateHandlerImpl(
          componentId,
          tableUpdater,
          methods = Seq(method),
          deleteHandler = true,
          serializer = serializer)(userEc)))
  }

  // Note: shared impl for update and delete handling
  final case class UpdateHandlerImpl(
      componentId: String,
      tableUpdaterClass: Class[_],
      methods: Seq[Method],
      serializer: JsonSerializer,
      ignoreUnknown: Boolean = false,
      deleteHandler: Boolean = false)(implicit userEc: ExecutionContext)
      extends SpiTableUpdateHandler {

    private val userLog = LoggerFactory.getLogger(tableUpdaterClass)

    private val methodsByInput: Map[Class[_], Method] =
      if (deleteHandler) Map.empty
      else
        methods.map { m =>
          // FIXME not entirely sure this is right
          // register each possible input
          val inputType = m.getParameterTypes.head
          serializer.registerTypeHints(m.getParameterTypes.head)

          inputType -> m
        }.toMap

    // Note: New instance for each update to avoid users storing/leaking state
    private def tableUpdater(): TableUpdater[AnyRef] = {
      tableUpdaterClass.getDeclaredConstructor().newInstance().asInstanceOf[TableUpdater[AnyRef]]
    }

    override def handle(input: SpiTableUpdateEnvelope): Future[SpiTableUpdateEffect] = Future {
      val existingState: Option[AnyRef] = input.existingTableRow.map(serializer.fromBytes)
      val metadata = MetadataImpl.of(input.metadata)
      val addedToMDC = metadata.traceId match {
        case Some(traceId) =>
          MDC.put(Telemetry.TRACE_ID, traceId)
          true
        case None => false
      }
      try {

        // FIXME choose method like for other consumers

        val event =
          if (deleteHandler) null // no payload to deserialize
          else serializer.fromBytes(input.eventPayload)

        val foundMethod: Option[Method] =
          if (deleteHandler) {
            Some(methods.head) // only one delete handler
          } else {
            methodsByInput
              .collectFirst { case (clazz, method) if clazz.isAssignableFrom(event.getClass) => method }
          }

        val effect: ViewEffectImpl.PrimaryEffect[Any] = {
          foundMethod match {
            case Some(method) =>
              val updateContext = UpdateContextImpl(method.getName, metadata)
              val tableUpdaterInstance = tableUpdater()
              try {

                tableUpdaterInstance._internalSetViewState(existingState.getOrElse(tableUpdaterInstance.emptyRow()))
                tableUpdaterInstance._internalSetUpdateContext(Optional.of(updateContext))

                val result =
                  if (deleteHandler) method.invoke(tableUpdaterInstance)
                  else method.invoke(tableUpdaterInstance, event)

                result match {
                  case effect: ViewEffectImpl.PrimaryEffect[Any @unchecked] => effect
                  case other =>
                    throw new RuntimeException(
                      s"Unexpected return value from table updater [$tableUpdaterClass]: [$other]")
                }

              } catch {
                case NonFatal(error) =>
                  userLog.error(s"View updater for view [${componentId}] threw an exception", error)
                  throw ViewException(componentId, s"View unexpected failure: ${error.getMessage}", Some(error))
              } finally {
                tableUpdaterInstance._internalSetUpdateContext(Optional.empty())
              }
            case None if ignoreUnknown => ViewEffectImpl.Ignore
            case None                  =>
              // FIXME proper error message with lots of details
              throw ViewException(
                componentId,
                s"Unhandled event type [${event.getClass}] for updater [$tableUpdaterClass]",
                None)

          }
        }

        effect match {
          case ViewEffectImpl.Update(newState) =>
            if (newState == null) {
              // FIXME MDC trace id should stretch here as well
              userLog.error(
                s"View updater tried to set row state to null, not allowed [${componentId}] threw an exception")
              throw ViewException(componentId, "updateState with null state is not allowed.", None)
            }
            val bytesPayload = serializer.toBytes(newState)
            new SpiTableUpdateEffect.UpdateRow(bytesPayload)
          case ViewEffectImpl.Delete => SpiTableUpdateEffect.DeleteRow
          case ViewEffectImpl.Ignore => SpiTableUpdateEffect.IgnoreUpdate
        }
      } finally {
        if (addedToMDC) MDC.remove(Telemetry.TRACE_ID)
      }

    }(userEc)
  }

  private final case class UpdateContextImpl(eventName: String, metadata: Metadata)
      extends AbstractContext
      with UpdateContext {

    override def eventSubject(): Optional[String] =
      if (metadata.isCloudEvent)
        metadata.asCloudEvent().subject()
      else
        Optional.empty()
  }
}

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import scala.reflect.ClassTag
import kalix.javasdk.action.Action
import kalix.javasdk.annotations.Publish
import kalix.javasdk.annotations.Query
import kalix.javasdk.annotations.Subscribe
import kalix.javasdk.annotations.Table
import kalix.javasdk.annotations.ViewId
import kalix.javasdk.eventsourcedentity.EventSourcedEntity
import kalix.javasdk.impl.ComponentDescriptorFactory.eventSourcedEntitySubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.findEventSourcedEntityClass
import kalix.javasdk.impl.ComponentDescriptorFactory.findEventSourcedEntityType
import kalix.javasdk.impl.ComponentDescriptorFactory.findPublicationTopicName
import kalix.javasdk.impl.ComponentDescriptorFactory.findSubscriptionConsumerGroup
import kalix.javasdk.impl.ComponentDescriptorFactory.findSubscriptionSourceName
import kalix.javasdk.impl.ComponentDescriptorFactory.findSubscriptionTopicName
import kalix.javasdk.impl.ComponentDescriptorFactory.findValueEntityType
import kalix.javasdk.impl.ComponentDescriptorFactory.hasAcl
import kalix.javasdk.impl.ComponentDescriptorFactory.hasActionOutput
import kalix.javasdk.impl.ComponentDescriptorFactory.hasEventSourcedEntitySubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.hasHandleDeletes
import kalix.javasdk.impl.ComponentDescriptorFactory.hasStreamSubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.hasSubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.hasTopicPublication
import kalix.javasdk.impl.ComponentDescriptorFactory.hasTopicSubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.hasUpdateEffectOutput
import kalix.javasdk.impl.ComponentDescriptorFactory.hasValueEntitySubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.streamSubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.topicSubscription
import kalix.javasdk.impl.reflection.Reflect
import kalix.javasdk.impl.reflection.Reflect.Syntax._
import kalix.javasdk.valueentity.ValueEntity
import kalix.javasdk.view.View

object Validations {

  import Reflect.methodOrdering

  object Validation {

    def apply(messages: Array[String]): Validation = Validation(messages.toIndexedSeq)

    def apply(messages: Seq[String]): Validation =
      if (messages.isEmpty) Valid
      else Invalid(messages)

    def apply(message: String): Validation = Invalid(Seq(message))
  }

  sealed trait Validation {
    def isValid: Boolean

    final def isInvalid: Boolean = !isInvalid
    def ++(validation: Validation): Validation

    def failIfInvalid: Validation
  }

  case object Valid extends Validation {
    override def isValid: Boolean = true
    override def ++(validation: Validation): Validation = validation

    override def failIfInvalid: Validation = this
  }

  object Invalid {
    def apply(message: String): Invalid =
      Invalid(Seq(message))
  }

  case class Invalid(messages: Seq[String]) extends Validation {
    override def isValid: Boolean = false

    override def ++(validation: Validation): Validation =
      validation match {
        case Valid      => this
        case i: Invalid => Invalid(this.messages ++ i.messages)
      }

    override def failIfInvalid: Validation =
      throw InvalidComponentException(messages.mkString(", "))
  }

  private def when(cond: Boolean)(block: => Validation): Validation =
    if (cond) block else Valid

  private def when[T: ClassTag](component: Class[_])(block: => Validation): Validation =
    if (assignable[T](component)) block else Valid

  private def assignable[T: ClassTag](component: Class[_]): Boolean =
    implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]].isAssignableFrom(component)

  private def commonSubscriptionValidation(
      component: Class[_],
      updateMethodPredicate: Method => Boolean): Validation = {
    typeLevelSubscriptionValidation(component) ++
    eventSourcedEntitySubscriptionValidations(component) ++
    missingEventHandlerValidations(component, updateMethodPredicate) ++
    ambiguousHandlerValidations(component, updateMethodPredicate) ++
    valueEntitySubscriptionValidations(component, updateMethodPredicate) ++
    topicSubscriptionValidations(component) ++
    topicPublicationValidations(component, updateMethodPredicate) ++
    publishStreamIdMustBeFilled(component) ++
    noSubscriptionMethodWithAcl(component) ++
    subscriptionMethodMustHaveOneParameter(component)
  }

  def validate(component: Class[_]): Validation =
    componentMustBePublic(component) ++
    validateAction(component) ++
    validateView(component) ++
    validateEventSourcedEntity(component) ++
    validateValueEntity(component)

  private def validateEventSourcedEntity(component: Class[_]) =
    when[EventSourcedEntity[_, _]](component) {
      eventSourcedEntityEventMustBeSealed(component) ++
      eventSourcedCommandHandlersMustBeUnique(component)
    }

  private def eventSourcedEntityEventMustBeSealed(component: Class[_]): Validation = {
    val eventType =
      component.getGenericSuperclass
        .asInstanceOf[ParameterizedType]
        .getActualTypeArguments()(1)
        .asInstanceOf[Class[_]]

    when(!eventType.isSealed) {
      Invalid(
        s"The event type of an EventSourcedEntity is required to be a sealed interface. Event '${eventType.getName}' in '${component.getName}' is not sealed.")
    }
  }

  private def eventSourcedCommandHandlersMustBeUnique(component: Class[_]): Validation = {
    val commandHandlers = component.getMethods
      .filter(_.getReturnType == classOf[EventSourcedEntity.Effect[_]])
    commandHandlersMustBeUnique(component, commandHandlers)
  }

  def validateValueEntity(component: Class[_]): Validation = when[ValueEntity[_]](component) {
    valueEntityCommandHandlersMustBeUnique(component)
  }

  def valueEntityCommandHandlersMustBeUnique(component: Class[_]): Validation = {
    val commandHandlers = component.getMethods
      .filter(_.getReturnType == classOf[ValueEntity.Effect[_]])
    commandHandlersMustBeUnique(component, commandHandlers)
  }

  def commandHandlersMustBeUnique(component: Class[_], commandHandlers: Array[Method]): Validation = {
    val nonUnique = commandHandlers
      .groupBy(_.getName)
      .filter(_._2.length > 1)

    when(nonUnique.nonEmpty) {
      val nonUniqueWarnings =
        nonUnique
          .map { case (name, methods) => s"${methods.length} command handler methods named '$name'" }
          .mkString(", ")
      Invalid(
        errorMessage(
          component,
          s"${component.getSimpleName} has $nonUniqueWarnings. Command handlers must have unique names."))
    }
  }

  private def componentMustBePublic(component: Class[_]): Validation = {
    if (component.isPublic) {
      Valid
    } else {
      Invalid(
        errorMessage(
          component,
          s"${component.getSimpleName} is not marked with `public` modifier. Components must be public."))
    }
  }

  private def validateAction(component: Class[_]): Validation = {
    when[Action](component) {
      commonSubscriptionValidation(component, hasActionOutput) ++
      actionValidation(component)
    }
  }

  private def actionValidation(component: Class[_]): Validation = {
    // Nothing here right now
    Valid
  }

  private def validateView(component: Class[_]): Validation = {
    when[View[_]](component) {
      validateSingleView(component) ++
      viewMustHaveViewId(component) // ViewId mandatory for single view
    } ++
    when(Reflect.isMultiTableView(component)) {
      val nestedViewClasses = component.getDeclaredClasses.toSeq.filter(Reflect.isNestedViewTable)
      val nestedValidations =
        nestedViewClasses.map(validateSingleView) ++ nestedViewClasses.map(viewMustNotHaveViewId)

      nestedValidations.reduce(_ ++ _) ++
      viewMustNotHaveTableName(component) ++
      viewMustHaveAtLeastOneQueryMethod(component) ++
      viewMustHaveViewId(component) // ViewId mandatory for multi-table on the outer level class
    }

  }

  private def validateSingleView(component: Class[_]): Validation = {
    when(!Reflect.isNestedViewTable(component)) {
      viewMustHaveAtLeastOneQueryMethod(component)
    } ++
    commonSubscriptionValidation(component, hasUpdateEffectOutput) ++
    viewMustHaveTableName(component) ++
    viewMustHaveMethodLevelSubscriptionWhenTransformingUpdates(component)
  }

  private def errorMessage(element: AnnotatedElement, message: String): String = {
    val elementStr =
      element match {
        case clz: Class[_] => clz.getName
        case meth: Method  => s"${meth.getDeclaringClass.getName}#${meth.getName}"
        case any           => any.toString
      }
    s"On '$elementStr': $message"
  }

  def typeLevelSubscriptionValidation(component: Class[_]): Validation = {
    val typeLevelSubs = List(
      hasValueEntitySubscription(component),
      hasEventSourcedEntitySubscription(component),
      hasStreamSubscription(component),
      hasTopicSubscription(component))

    when(typeLevelSubs.count(identity) > 1) {
      Validation(errorMessage(component, "Only one subscription type is allowed on a type level."))
    }
  }

  private def eventSourcedEntitySubscriptionValidations(component: Class[_]): Validation = {
    val methods = component.getMethods.toIndexedSeq
    when(
      hasEventSourcedEntitySubscription(component) &&
      methods.exists(hasEventSourcedEntitySubscription)) {
      // collect offending methods
      val messages = methods.filter(hasEventSourcedEntitySubscription).map { method =>
        errorMessage(
          method,
          "You cannot use @Subscribe.EventSourcedEntity annotation in both methods and class. You can do either one or the other.")
      }
      Validation(messages)
    }
  }

  private def getEventType(esEntity: Class[_]): Class[_] = {
    val genericTypeArguments = esEntity.getGenericSuperclass
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments
    genericTypeArguments(1).asInstanceOf[Class[_]]
  }

  private def ambiguousHandlerValidations(component: Class[_], updateMethodPredicate: Method => Boolean): Validation = {

    val methods = component.getMethods.toIndexedSeq

    if (hasSubscription(component)) {
      val effectMethods = methods
        .filter(updateMethodPredicate)
      ambiguousHandlersErrors(effectMethods, component)
    } else {
      val effectOutputMethodsGrouped = methods
        .filter(hasSubscription)
        .filter(updateMethodPredicate)
        .groupBy(findSubscriptionSourceName)

      effectOutputMethodsGrouped
        .map { case (_, methods) =>
          ambiguousHandlersErrors(methods, component)
        }
        .fold(Valid)(_ ++ _)
    }

  }

  private def ambiguousHandlersErrors(handlers: Seq[Method], component: Class[_]): Validation = {
    val ambiguousHandlers = handlers
      .groupBy(_.getParameterTypes.lastOption)
      .filter(_._2.size > 1)
      .map {
        case (Some(inputType), methods) =>
          Validation(errorMessage(
            component,
            s"Ambiguous handlers for ${inputType.getCanonicalName}, methods: [${methods.sorted.map(_.getName).mkString(", ")}] consume the same type."))
        case (None, methods) => //only delete handlers
          Validation(
            errorMessage(component, s"Ambiguous delete handlers: [${methods.sorted.map(_.getName).mkString(", ")}]."))
      }

    val sealedHandler = handlers.find(_.getParameterTypes.lastOption.exists(_.isSealed))
    val sealedHandlerMixedUsage = if (sealedHandler.nonEmpty && handlers.size > 1) {
      val unexpectedHandlerNames = handlers.filterNot(m => m == sealedHandler.get).map(_.getName)
      Validation(
        errorMessage(
          component,
          s"Event handler accepting a sealed interface [${sealedHandler.get.getName}] cannot be mixed with handlers for specific events. Please remove following handlers: [${unexpectedHandlerNames
            .mkString(", ")}]."))
    } else {
      Valid
    }

    ambiguousHandlers.fold(sealedHandlerMixedUsage)(_ ++ _)
  }

  private def missingEventHandlerValidations(
      component: Class[_],
      updateMethodPredicate: Method => Boolean): Validation = {
    val methods = component.getMethods.toIndexedSeq

    eventSourcedEntitySubscription(component) match {
      case Some(classLevel) =>
        val eventType = getEventType(classLevel.value())
        if (!classLevel.ignoreUnknown() && eventType.isSealed) {
          val effectMethodsInputParams: Seq[Class[_]] = methods
            .filter(updateMethodPredicate)
            .map(_.getParameterTypes.last) //last because it could be a view update methods with 2 params
          missingEventHandler(effectMethodsInputParams, eventType, component)
        } else {
          Valid
        }
      case None =>
        //method level
        val effectOutputMethodsGrouped = methods
          .filter(hasEventSourcedEntitySubscription)
          .filter(updateMethodPredicate)
          .groupBy(findEventSourcedEntityClass)

        effectOutputMethodsGrouped
          .map { case (entityClass, methods) =>
            val eventType = getEventType(entityClass)
            if (eventType.isSealed) {
              missingEventHandler(methods.map(_.getParameterTypes.last), eventType, component)
            } else {
              Valid
            }
          }
          .fold(Valid)(_ ++ _)
    }
  }

  private def missingEventHandler(
      inputEventParams: Seq[Class[_]],
      eventType: Class[_],
      component: Class[_]): Validation = {
    if (inputEventParams.exists(param => param.isSealed && param == eventType)) {
      //single sealed interface handler
      Valid
    } else {
      if (eventType.isSealed) {
        //checking possible only for sealed interfaces
        Validation(
          eventType.getPermittedSubclasses
            .filterNot(inputEventParams.contains)
            .map(clazz => errorMessage(component, s"missing an event handler for '${clazz.getName}'."))
            .toList)
      } else {
        Valid
      }
    }
  }

  private def topicSubscriptionValidations(component: Class[_]): Validation = {
    val methods = component.getMethods.toIndexedSeq
    val noMixedLevelSubs = when(hasTopicSubscription(component) && methods.exists(hasTopicSubscription)) {
      // collect offending methods
      val messages = methods.filter(hasTopicSubscription).map { method =>
        errorMessage(
          method,
          "You cannot use @Subscribe.Topic annotation in both methods and class. You can do either one or the other.")
      }
      Validation(messages)
    }

    val theSameConsumerGroupPerTopic = when(methods.exists(hasTopicSubscription)) {
      methods
        .filter(hasTopicSubscription)
        .sorted
        .groupBy(findSubscriptionTopicName)
        .map { case (topicName, methods) =>
          val consumerGroups = methods.map(findSubscriptionConsumerGroup).distinct.sorted
          when(consumerGroups.size > 1) {
            Validation(errorMessage(
              component,
              s"All subscription methods to topic [$topicName] must have the same consumer group, but found different consumer groups [${consumerGroups
                .mkString(", ")}]."))
          }
        }
        .fold(Valid)(_ ++ _)
    }

    noMixedLevelSubs ++ theSameConsumerGroupPerTopic
  }

  private def missingSourceForTopicPublication(component: Class[_]): Validation = {
    val methods = component.getMethods.toSeq
    if (hasSubscription(component)) {
      Valid
    } else {
      val messages = methods
        .filter(hasTopicPublication)
        .filterNot(method => hasSubscription(method))
        .map { method =>
          errorMessage(
            method,
            "You must select a source for @Publish.Topic. Annotate this methods with one of @Subscribe or REST annotations.")
        }
      Validation(messages)
    }
  }

  private def topicPublicationForSourceValidation(
      sourceName: String,
      component: Class[_],
      methodsGroupedBySource: Map[String, Seq[Method]]): Validation = {
    methodsGroupedBySource
      .map { case (entityType, methods) =>
        val topicNames = methods
          .filter(hasTopicPublication)
          .map(findPublicationTopicName)

        if (topicNames.nonEmpty && topicNames.length != methods.size) {
          Validation(errorMessage(
            component,
            s"Add @Publish.Topic annotation to all subscription methods from $sourceName \"$entityType\". Or remove it from all methods."))
        } else if (topicNames.toSet.size > 1) {
          Validation(
            errorMessage(
              component,
              s"All @Publish.Topic annotation for the same subscription source $sourceName \"$entityType\" should point to the same topic name. " +
              s"Create a separate Action if you want to split messages to different topics from the same source."))
        } else {
          Valid
        }
      }
      .fold(Valid)(_ ++ _)
  }

  private def topicPublicationValidations(component: Class[_], updateMethodPredicate: Method => Boolean): Validation = {
    val methods = component.getMethods.toSeq

    //VE type level subscription is not checked since we expecting only a single method in this case
    val veSubscriptions: Map[String, Seq[Method]] = methods
      .filter(hasValueEntitySubscription)
      .groupBy(findValueEntityType)

    val esSubscriptions: Map[String, Seq[Method]] = eventSourcedEntitySubscription(component) match {
      case Some(esEntity) =>
        Map(ComponentDescriptorFactory.readTypeIdValue(esEntity.value()) -> methods.filter(updateMethodPredicate))
      case None =>
        methods
          .filter(hasEventSourcedEntitySubscription)
          .groupBy(findEventSourcedEntityType)
    }

    val topicSubscriptions: Map[String, Seq[Method]] = topicSubscription(component) match {
      case Some(topic) => Map(topic.value() -> methods.filter(updateMethodPredicate))
      case None =>
        methods
          .filter(hasTopicSubscription)
          .groupBy(findSubscriptionTopicName)
    }

    val streamSubscriptions: Map[String, Seq[Method]] = streamSubscription(component) match {
      case Some(stream) => Map(stream.id() -> methods.filter(updateMethodPredicate))
      case None         => Map.empty //only type level
    }

    missingSourceForTopicPublication(component) ++
    topicPublicationForSourceValidation("ValueEntity", component, veSubscriptions) ++
    topicPublicationForSourceValidation("EventSourcedEntity", component, esSubscriptions) ++
    topicPublicationForSourceValidation("Topic", component, topicSubscriptions) ++
    topicPublicationForSourceValidation("Stream", component, streamSubscriptions)
  }

  private def publishStreamIdMustBeFilled(component: Class[_]): Validation = {
    Option(component.getAnnotation(classOf[Publish.Stream]))
      .map { ann =>
        when(ann.id().trim.isEmpty) {
          Validation(Seq("@Publish.Stream id can not be an empty string"))
        }
      }
      .getOrElse(Valid)
  }

  private def noSubscriptionMethodWithAcl(component: Class[_]): Validation = {

    val hasSubscriptionAndAcl = (method: Method) => hasAcl(method) && hasSubscription(method)

    val messages =
      component.getMethods.toIndexedSeq.filter(hasSubscriptionAndAcl).map { method =>
        errorMessage(
          method,
          "Methods annotated with Kalix @Subscription annotations are for internal use only and cannot be annotated with ACL annotations.")
      }

    Validation(messages)
  }

  private def viewMustNotHaveViewId(component: Class[_]): Validation = {
    val ann = component.getAnnotation(classOf[ViewId])
    when(ann != null) {
      Invalid(errorMessage(component, "A nested View should not be annotated with @ViewId."))
    }
  }
  private def viewMustHaveViewId(component: Class[_]): Validation = {
    val ann = component.getAnnotation(classOf[ViewId])
    if (ann == null) {
      Invalid(errorMessage(component, "A View should be annotated with @ViewId."))
    } else {
      val viewId: String = ann.value()
      if (viewId == null || viewId.trim.isEmpty) {
        Invalid(errorMessage(component, "@ViewId name is empty, must be a non-empty string."))
      } else Valid
    }
  }

  private def viewMustNotHaveTableName(component: Class[_]): Validation = {
    val ann = component.getAnnotation(classOf[Table])
    when(ann != null) {
      Invalid(errorMessage(component, "A multi-table View should not be annotated with @Table."))
    }
  }
  private def viewMustHaveTableName(component: Class[_]): Validation = {
    val ann = component.getAnnotation(classOf[Table])
    if (ann == null) {
      Invalid(errorMessage(component, "A View should be annotated with @Table."))
    } else {
      val tableName: String = ann.value()
      if (tableName == null || tableName.trim.isEmpty) {
        Invalid(errorMessage(component, "@Table name is empty, must be a non-empty string."))
      } else Valid
    }
  }

  private def viewMustHaveMethodLevelSubscriptionWhenTransformingUpdates(component: Class[_]): Validation = {
    if (hasValueEntitySubscription(component)) {
      val tableType: Class[_] = tableTypeOf(component)
      val valueEntityClass: Class[_] =
        component.getAnnotation(classOf[Subscribe.ValueEntity]).value().asInstanceOf[Class[_]]
      val entityStateClass = valueEntityStateClassOf(valueEntityClass)

      when(entityStateClass != tableType) {
        val message =
          s"You are using a type level annotation in this View and that requires the View type [${tableType.getName}] " +
          s"to match the ValueEntity type [${entityStateClass.getName}]. " +
          s"If your intention is to transform the type, you should instead add a method like " +
          s"`UpdateEffect<${tableType.getName}> onChange(${entityStateClass.getName} state)`" +
          " and move the @Subscribe.ValueEntity to it."

        Validation(Seq(errorMessage(component, message)))
      }
    } else {
      Valid
    }
  }

  private def viewMustHaveAtLeastOneQueryMethod(component: Class[_]): Validation = {
    val hasAtLeastOneQuery =
      component.getMethods.exists(method => method.hasAnnotation[Query])
    if (!hasAtLeastOneQuery)
      Invalid(
        errorMessage(
          component,
          "No valid query method found. Views should have at least one method annotated with @Query."))
    else Valid
  }

  private def subscriptionMethodMustHaveOneParameter(component: Class[_]): Validation = {
    val offendingMethods = component.getMethods
      .filter(hasValueEntitySubscription)
      .filterNot(hasHandleDeletes)
      .filterNot(_.getParameterTypes.length == 1)

    val messages =
      offendingMethods.map { method =>
        errorMessage(
          method,
          "Subscription method must have exactly one parameter, unless it's marked as handleDeletes.")
      }

    Validation(messages)
  }

  private def valueEntitySubscriptionValidations(
      component: Class[_],
      updateMethodPredicate: Method => Boolean): Validation = {

    val subscriptionMethods = component.getMethods.toIndexedSeq.filter(hasValueEntitySubscription).sorted
    val updatedMethods = if (hasValueEntitySubscription(component)) {
      component.getMethods.toIndexedSeq.filter(updateMethodPredicate).sorted
    } else {
      subscriptionMethods.filterNot(hasHandleDeletes).filter(updateMethodPredicate)
    }

    val (handleDeleteMethods, handleDeleteMethodsWithParam) =
      subscriptionMethods.filter(hasHandleDeletes).partition(_.getParameterTypes.isEmpty)

    val noMixedLevelValueEntitySubscription =
      when(hasValueEntitySubscription(component) && subscriptionMethods.nonEmpty) {
        // collect offending methods
        val messages = subscriptionMethods.map { method =>
          errorMessage(
            method,
            "You cannot use @Subscribe.ValueEntity annotation in both methods and class. You can do either one or the other.")
        }
        Validation(messages)
      }

    val handleDeletesMustHaveZeroArity = {
      val messages =
        handleDeleteMethodsWithParam.map { method =>
          val numParams = method.getParameters.length
          errorMessage(
            method,
            s"Method annotated with '@Subscribe.ValueEntity' and handleDeletes=true must not have parameters. Found $numParams method parameters.")
        }

      Validation(messages)
    }

    val onlyOneValueEntityUpdateIsAllowed = {
      if (updatedMethods.size >= 2) {
        val messages = errorMessage(
          component,
          s"Duplicated update methods [${updatedMethods.map(_.getName).mkString(", ")}]for ValueEntity subscription.")
        Validation(messages)
      } else Valid
    }

    val onlyOneHandlesDeleteIsAllowed = {
      val offendingMethods = handleDeleteMethods.filter(_.getParameterTypes.isEmpty)

      if (offendingMethods.size >= 2) {
        val messages =
          offendingMethods.map { method =>
            errorMessage(
              method,
              "Multiple methods annotated with @Subscription.ValueEntity(handleDeletes=true) is not allowed.")
          }
        Validation(messages)
      } else Valid
    }

    val standaloneMethodLevelHandleDeletesIsNotAllowed = {
      if (handleDeleteMethods.nonEmpty && updatedMethods.isEmpty) {
        val messages =
          handleDeleteMethods.map { method =>
            errorMessage(method, "Method annotated with handleDeletes=true has no matching update method.")
          }
        Validation(messages)
      } else Valid
    }

    noMixedLevelValueEntitySubscription ++
    handleDeletesMustHaveZeroArity ++
    onlyOneValueEntityUpdateIsAllowed ++
    onlyOneHandlesDeleteIsAllowed ++
    standaloneMethodLevelHandleDeletesIsNotAllowed
  }

  private def tableTypeOf(component: Class[_]) = {
    component.getGenericSuperclass
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments
      .head
      .asInstanceOf[Class[_]]
  }

  private def valueEntityStateClassOf(valueEntityClass: Class[_]): Class[_] = {
    valueEntityClass.getGenericSuperclass
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments
      .head
      .asInstanceOf[Class[_]]
  }

}

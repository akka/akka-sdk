/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.reflection.Reflect
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

import scala.reflect.ClassTag
import akka.javasdk.annotations.Consume.FromKeyValueEntity
import akka.javasdk.annotations.Produce.ServiceStream
import ComponentDescriptorFactory.eventSourcedEntitySubscription
import ComponentDescriptorFactory.hasAcl
import ComponentDescriptorFactory.hasConsumerOutput
import ComponentDescriptorFactory.hasEventSourcedEntitySubscription
import ComponentDescriptorFactory.hasHandleDeletes
import ComponentDescriptorFactory.hasStreamSubscription
import ComponentDescriptorFactory.hasSubscription
import ComponentDescriptorFactory.hasTopicPublication
import ComponentDescriptorFactory.hasTopicSubscription
import ComponentDescriptorFactory.hasUpdateEffectOutput
import ComponentDescriptorFactory.hasValueEntitySubscription
import Reflect.Syntax._
import akka.javasdk.annotations.ComponentId
import akka.javasdk.annotations.Query
import akka.javasdk.annotations.Table
import akka.javasdk.consumer.Consumer
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.view.View

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
    missingEventHandlerValidations(component, updateMethodPredicate) ++
    ambiguousHandlerValidations(component, updateMethodPredicate) ++
    valueEntitySubscriptionValidations(component, updateMethodPredicate) ++
    topicPublicationValidations(component) ++
    publishStreamIdMustBeFilled(component) ++
    noSubscriptionMethodWithAcl(component, updateMethodPredicate) ++
    subscriptionMethodMustHaveOneParameter(component, updateMethodPredicate)
  }

  def validate(component: Class[_]): Validation =
    componentMustBePublic(component) ++
    validateTimedAction(component) ++
    validateConsumer(component) ++
    validateView(component) ++
    validateEventSourcedEntity(component) ++
    validateValueEntity(component)

  private def validateEventSourcedEntity(component: Class[_]) =
    when[EventSourcedEntity[_, _]](component) {
      eventSourcedEntityEventMustBeSealed(component) ++
      eventSourcedCommandHandlersMustBeUnique(component) ++
      mustHaveNonEmptyComponentId(component)
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

  def validateValueEntity(component: Class[_]): Validation = when[KeyValueEntity[_]](component) {
    valueEntityCommandHandlersMustBeUnique(component) ++
    mustHaveNonEmptyComponentId(component)
  }

  def valueEntityCommandHandlersMustBeUnique(component: Class[_]): Validation = {
    val commandHandlers = component.getMethods
      .filter(_.getReturnType == classOf[KeyValueEntity.Effect[_]])
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

  private def validateTimedAction(component: Class[_]): Validation = {
    when[TimedAction](component) {
      actionValidation(component) ++
      mustHaveNonEmptyComponentId(component)
    }
  }

  private def validateConsumer(component: Class[_]): Validation = {
    when[Consumer](component) {
      commonSubscriptionValidation(component, hasConsumerOutput) ++
      actionValidation(component) ++
      mustHaveNonEmptyComponentId(component)
    }
  }

  private def actionValidation(component: Class[_]): Validation = {
    // Nothing here right now
    Valid
  }

  private def validateView(component: Class[_]): Validation =
    when[View](component) {
      val tableUpdaters: Seq[Class[_]] = component.getDeclaredClasses.filter(Reflect.isViewTableUpdater).toSeq

      mustHaveNonEmptyComponentId(component) ++
      viewMustNotHaveTableAnnotation(component) ++
      viewMustHaveAtLeastOneViewTableUpdater(component) ++
      viewMustHaveAtLeastOneQueryMethod(component) ++
      viewQueriesMustReturnEffect(component) ++
      viewMultipleTableUpdatersMustHaveTableAnnotations(tableUpdaters) ++
      tableUpdaters
        .map(updaterClass =>
          validateVewTableUpdater(updaterClass) ++
          viewTableAnnotationMustNotBeEmptyString(updaterClass) ++
          viewMustHaveCorrectUpdateHandlerWhenTransformingViewUpdates(updaterClass))
        .foldLeft(Valid: Validation)(_ ++ _)
      // FIXME query annotated return type should be effect
    }

  private def viewMustHaveAtLeastOneViewTableUpdater(component: Class[_]) =
    when(component.getDeclaredClasses.count(Reflect.isViewTableUpdater) < 1) {
      Validation(errorMessage(component, "A view must contain at least one public static TableUpdater subclass."))
    }

  private def validateVewTableUpdater(tableUpdater: Class[_]): Validation = {
    when(!hasSubscription(tableUpdater)) {
      Validation(errorMessage(tableUpdater, "A TableUpdater subclass must be annotated with `@Consume` annotation."))
    } ++
    commonSubscriptionValidation(tableUpdater, hasUpdateEffectOutput)
  }

  private def viewTableAnnotationMustNotBeEmptyString(tableUpdater: Class[_]): Validation = {
    val annotation = tableUpdater.getAnnotation(classOf[Table])
    when(annotation != null && annotation.value().trim.isEmpty) {
      Validation(errorMessage(tableUpdater, "@Table name is empty, must be a non-empty string."))
    }
  }

  private def viewQueriesMustReturnEffect(component: Class[_]): Validation = {
    val queriesWithWrongReturnType = component.getMethods.toIndexedSeq.filter(m =>
      m.getAnnotation(classOf[Query]) != null && m.getReturnType != classOf[View.QueryEffect[_]])
    queriesWithWrongReturnType.foldLeft(Valid: Validation) { (validation, methodWithWrongReturnType) =>
      validation ++ Validation(
        errorMessage(methodWithWrongReturnType, "Query methods must return View.QueryEffect<RowType>."))
    }
  }

  private def viewMultipleTableUpdatersMustHaveTableAnnotations(tableUpdaters: Seq[Class[_]]): Validation =
    if (tableUpdaters.size > 1) {
      tableUpdaters.find(_.getAnnotation(classOf[Table]) == null) match {
        case Some(clazz) =>
          Validation(errorMessage(clazz, "When there are multiple table updater, each must be annotated with @Table."))
        case None => Valid
      }
    } else Valid

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
      Valid
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
        Valid
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

  private def missingSourceForTopicPublication(component: Class[_]): Validation = {
    if (hasTopicPublication(component) && !hasSubscription(component)) {
      Validation(
        errorMessage(
          component,
          "You must select a source for @Produce.ToTopic. Annotate this class with one a @Consume annotation."))
    } else {
      Valid
    }
  }

  private def topicPublicationValidations(component: Class[_]): Validation = {
    missingSourceForTopicPublication(component)
  }

  private def publishStreamIdMustBeFilled(component: Class[_]): Validation = {
    Option(component.getAnnotation(classOf[ServiceStream]))
      .map { ann =>
        when(ann.id().trim.isEmpty) {
          Validation(Seq("@Produce.ServiceStream id can not be an empty string"))
        }
      }
      .getOrElse(Valid)
  }

  private def noSubscriptionMethodWithAcl(component: Class[_], updateMethodPredicate: Method => Boolean): Validation = {

    val hasSubscriptionAndAcl = (method: Method) =>
      hasAcl(method) && updateMethodPredicate(method) && hasSubscription(component)

    val messages =
      component.getMethods.toIndexedSeq.filter(hasSubscriptionAndAcl).map { method =>
        errorMessage(
          method,
          "Methods from classes annotated with Kalix @Consume annotations are for internal use only and cannot be annotated with ACL annotations.")
      }

    Validation(messages)
  }

  private def mustHaveNonEmptyComponentId(component: Class[_]): Validation = {
    val ann = component.getAnnotation(classOf[ComponentId])
    if (ann != null) {
      val componentId: String = ann.value()
      if (componentId == null || componentId.trim.isEmpty) {
        Invalid(errorMessage(component, "@ComponentId name is empty, must be a non-empty string."))
      } else Valid
    } else {
      //missing annotation means that the component is disabled
      Valid
    }
  }

  private def viewMustNotHaveTableAnnotation(component: Class[_]): Validation = {
    val ann = component.getAnnotation(classOf[Table])
    when(ann != null) {
      Invalid(errorMessage(component, "A View itself should not be annotated with @Table."))
    }
  }

  private def viewMustHaveCorrectUpdateHandlerWhenTransformingViewUpdates(tableUpdater: Class[_]): Validation = {
    if (hasValueEntitySubscription(tableUpdater)) {
      val tableType: Class[_] = tableTypeOf(tableUpdater)
      val valueEntityClass: Class[_] =
        tableUpdater.getAnnotation(classOf[FromKeyValueEntity]).value().asInstanceOf[Class[_]]
      val entityStateClass = valueEntityStateClassOf(valueEntityClass)

      if (entityStateClass != tableType) {
        val viewUpdateMatchesTableType = tableUpdater.getMethods
          .filter(hasUpdateEffectOutput)
          .exists(m => {
            val updateHandlerType = m.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head
            updateHandlerType == tableType
          })

        when(!viewUpdateMatchesTableType) {
          val message =
            s"You are using a type level annotation in this View and that requires the View type [${tableType.getName}] " +
            s"to match the ValueEntity type [${entityStateClass.getName}]. " +
            s"If your intention is to transform the type, you should add a method like " +
            s"`UpdateEffect<${tableType.getName}> onChange(${entityStateClass.getName} state)`."

          Validation(Seq(errorMessage(tableUpdater, message)))
        }
      } else {
        Valid
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

  private def subscriptionMethodMustHaveOneParameter(
      component: Class[_],
      updateMethodPredicate: Method => Boolean): Validation = {
    if (hasSubscription(component)) {
      val offendingMethods = component.getMethods
        .filter(updateMethodPredicate)
        .filterNot(hasHandleDeletes)
        .filterNot(_.getParameterTypes.length == 1)

      val messages =
        offendingMethods.map { method =>
          errorMessage(
            method,
            "Subscription method must have exactly one parameter, unless it's marked with @DeleteHandler.")
        }

      Validation(messages)
    } else {
      Valid
    }

  }

  private def valueEntitySubscriptionValidations(
      component: Class[_],
      updateMethodPredicate: Method => Boolean): Validation = {

    when(hasValueEntitySubscription(component)) {
      val subscriptionMethods = component.getMethods.toIndexedSeq.filter(updateMethodPredicate).sorted
      val updatedMethods = subscriptionMethods.filterNot(hasHandleDeletes)

      val (handleDeleteMethods, handleDeleteMethodsWithParam) =
        subscriptionMethods.filter(hasHandleDeletes).partition(_.getParameterTypes.isEmpty)

      val handleDeletesMustHaveZeroArity = {
        val messages =
          handleDeleteMethodsWithParam.map { method =>
            val numParams = method.getParameters.length
            errorMessage(
              method,
              s"Method annotated with '@DeleteHandler' must not have parameters. Found $numParams method parameters.")
          }

        Validation(messages)
      }

      val onlyOneValueEntityUpdateIsAllowed = {
        when(updatedMethods.size >= 2) {
          val messages = errorMessage(
            component,
            s"Duplicated update methods [${updatedMethods.map(_.getName).mkString(", ")}]for KeyValueEntity subscription.")
          Validation(messages)
        }
      }

      val onlyOneHandlesDeleteIsAllowed = {
        val offendingMethods = handleDeleteMethods.filter(_.getParameterTypes.isEmpty)

        when(offendingMethods.size >= 2) {
          val messages =
            offendingMethods.map { method =>
              errorMessage(method, "Multiple methods annotated with @DeleteHandler are not allowed.")
            }
          Validation(messages)
        }
      }

      handleDeletesMustHaveZeroArity ++
      onlyOneValueEntityUpdateIsAllowed ++
      onlyOneHandlesDeleteIsAllowed
    }
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

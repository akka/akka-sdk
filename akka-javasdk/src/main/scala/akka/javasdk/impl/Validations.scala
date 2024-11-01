/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import scala.reflect.ClassTag
import akka.annotation.InternalApi
import akka.javasdk.annotations.ComponentId
import akka.javasdk.annotations.Consume.FromKeyValueEntity
import akka.javasdk.annotations.Produce.ServiceStream
import akka.javasdk.annotations.Query
import akka.javasdk.annotations.Table
import akka.javasdk.consumer.Consumer
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.ComponentDescriptorFactory.eventSourcedEntitySubscription
import akka.javasdk.impl.ComponentDescriptorFactory.hasAcl
import akka.javasdk.impl.ComponentDescriptorFactory.hasConsumerOutput
import akka.javasdk.impl.ComponentDescriptorFactory.hasESEffectOutput
import akka.javasdk.impl.ComponentDescriptorFactory.hasEventSourcedEntitySubscription
import akka.javasdk.impl.ComponentDescriptorFactory.hasHandleDeletes
import akka.javasdk.impl.ComponentDescriptorFactory.hasKVEEffectOutput
import akka.javasdk.impl.ComponentDescriptorFactory.hasQueryEffectOutput
import akka.javasdk.impl.ComponentDescriptorFactory.hasStreamSubscription
import akka.javasdk.impl.ComponentDescriptorFactory.hasSubscription
import akka.javasdk.impl.ComponentDescriptorFactory.hasTimedActionEffectOutput
import akka.javasdk.impl.ComponentDescriptorFactory.hasTopicPublication
import akka.javasdk.impl.ComponentDescriptorFactory.hasTopicSubscription
import akka.javasdk.impl.ComponentDescriptorFactory.hasUpdateEffectOutput
import akka.javasdk.impl.ComponentDescriptorFactory.hasValueEntitySubscription
import akka.javasdk.impl.ComponentDescriptorFactory.hasWorkflowEffectOutput
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.reflection.Reflect.Syntax._
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.view.View
import akka.javasdk.workflow.Workflow

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object Validations {

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

    def failIfInvalid(): Unit
  }

  case object Valid extends Validation {
    override def isValid: Boolean = true
    override def ++(validation: Validation): Validation = validation

    override def failIfInvalid(): Unit = ()
  }

  object Invalid {
    def apply(message: String): Invalid =
      Invalid(Seq(message))
  }

  final case class Invalid(messages: Seq[String]) extends Validation {
    override def isValid: Boolean = false

    override def ++(validation: Validation): Validation =
      validation match {
        case Valid      => this
        case i: Invalid => Invalid(this.messages ++ i.messages)
      }

    override def failIfInvalid(): Unit = throwFailureSummary()

    def throwFailureSummary(): Nothing =
      throw ValidationException(messages.mkString(", "))
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
    validateValueEntity(component) ++
    validateWorkflow(component)

  private def validateEventSourcedEntity(component: Class[_]) =
    when[EventSourcedEntity[_, _]](component) {
      eventSourcedEntityEventMustBeSealed(component) ++
      eventSourcedCommandHandlersMustBeUnique(component) ++
      mustHaveValidComponentId(component) ++
      commandHandlerArityShouldBeZeroOrOne(component, hasESEffectOutput)
    }

  private def validateWorkflow(component: Class[_]) =
    when[Workflow[_]](component) {
      commandHandlerArityShouldBeZeroOrOne(component, hasWorkflowEffectOutput)
    }

  private def eventSourcedEntityEventMustBeSealed(component: Class[_]): Validation = {
    val eventClass = Reflect.eventSourcedEntityEventType(component)
    when(!eventClass.isSealed) {
      Invalid(
        s"The event type of an EventSourcedEntity is required to be a sealed interface. Event '${eventClass.getName}' in '${component.getName}' is not sealed.")
    }
  }

  private def eventSourcedCommandHandlersMustBeUnique(component: Class[_]): Validation = {
    val commandHandlers = component.getMethods
      .filter(_.getReturnType == classOf[EventSourcedEntity.Effect[_]])
    commandHandlersMustBeUnique(component, commandHandlers)
  }

  def validateValueEntity(component: Class[_]): Validation = when[KeyValueEntity[_]](component) {
    valueEntityCommandHandlersMustBeUnique(component) ++
    mustHaveValidComponentId(component) ++
    commandHandlerArityShouldBeZeroOrOne(component, hasKVEEffectOutput)
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
      mustHaveValidComponentId(component) ++
      commandHandlerArityShouldBeZeroOrOne(component, hasTimedActionEffectOutput)
    }
  }

  private def validateConsumer(component: Class[_]): Validation = {
    when[Consumer](component) {
      hasConsumeAnnotation(component, "Consumer") ++
      commonSubscriptionValidation(component, hasConsumerOutput) ++
      actionValidation(component) ++
      mustHaveValidComponentId(component)
    }
  }

  private def hasConsumeAnnotation(component: Class[_], componentName: String): Validation = {
    when(!hasSubscription(component)) {
      Invalid(errorMessage(component, s"A $componentName must be annotated with `@Consume` annotation."))
    }
  }

  private def actionValidation(component: Class[_]): Validation = {
    // Nothing here right now
    Valid
  }

  private def validateView(component: Class[_]): Validation =
    when[View](component) {
      val tableUpdaters: Seq[Class[_]] = component.getDeclaredClasses.filter(Reflect.isViewTableUpdater).toSeq

      mustHaveValidComponentId(component) ++
      viewMustNotHaveTableAnnotation(component) ++
      viewMustHaveAtLeastOneViewTableUpdater(component) ++
      viewMustHaveAtLeastOneQueryMethod(component) ++
      validateQueryResultTypes(component) ++
      viewQueriesWithStreamUpdatesMustBeStreaming(component) ++
      commandHandlerArityShouldBeZeroOrOne(component, hasQueryEffectOutput) ++
      viewMultipleTableUpdatersMustHaveTableAnnotations(tableUpdaters) ++
      tableUpdaters
        .map(updaterClass =>
          validateVewTableUpdater(updaterClass) ++
          hasConsumeAnnotation(updaterClass, "TableUpdater") ++
          viewTableAnnotationMustNotBeEmptyString(updaterClass) ++
          viewMustHaveCorrectUpdateHandlerWhenTransformingViewUpdates(updaterClass))
        .foldLeft(Valid: Validation)(_ ++ _)
      // FIXME query annotated return type should be effect
    }

  private def commandHandlerArityShouldBeZeroOrOne(
      component: Class[_],
      methodPredicate: Method => Boolean): Validation = {
    component.getMethods.toIndexedSeq
      .filter(methodPredicate)
      .filter(_.getParameters.length > 1)
      .foldLeft(Valid: Validation) { (validation, methodWithWrongArity) =>
        validation ++ Validation(errorMessage(
          component,
          s"Method [${methodWithWrongArity.getName}] must have zero or one argument. If you need to pass more arguments, wrap them in a class."))
      }

  }

  private def viewMustHaveAtLeastOneViewTableUpdater(component: Class[_]) =
    when(component.getDeclaredClasses.count(Reflect.isViewTableUpdater) < 1) {
      Validation(errorMessage(component, "A view must contain at least one public static TableUpdater subclass."))
    }

  private def validateVewTableUpdater(tableUpdater: Class[_]): Validation = {
    when(!hasSubscription(tableUpdater)) {
      Validation(errorMessage(tableUpdater, "A TableUpdater subclass must be annotated with `@Consume` annotation."))
    } ++
    validateViewUpdaterRowType(tableUpdater) ++
    commonSubscriptionValidation(tableUpdater, hasUpdateEffectOutput)
  }

  private val primitiveWrapperClasses: Set[Class[AnyRef]] = Set(
    classOf[java.lang.Integer].asInstanceOf[Class[AnyRef]],
    classOf[java.lang.Long].asInstanceOf[Class[AnyRef]],
    classOf[java.lang.Float].asInstanceOf[Class[AnyRef]],
    classOf[java.lang.Double].asInstanceOf[Class[AnyRef]],
    classOf[java.lang.Byte].asInstanceOf[Class[AnyRef]],
    classOf[java.lang.Short].asInstanceOf[Class[AnyRef]],
    classOf[java.lang.Boolean].asInstanceOf[Class[AnyRef]],
    classOf[java.lang.Character].asInstanceOf[Class[AnyRef]],
    // not really a primitive wrapper but works for now
    classOf[java.lang.String].asInstanceOf[Class[AnyRef]])

  private def validateViewUpdaterRowType(tableUpdater: Class[_]): Validation = {
    val tpe = tableUpdater.getGenericSuperclass.asInstanceOf[ParameterizedType]
    tpe.getActualTypeArguments.head match {
      case clazz: Class[_] =>
        if (primitiveWrapperClasses(clazz.asInstanceOf[Class[AnyRef]]))
          Validation(errorMessage(tableUpdater, s"View row type ${clazz.getName} is not supported"))
        else Valid

    }
  }

  private def viewTableAnnotationMustNotBeEmptyString(tableUpdater: Class[_]): Validation = {
    val annotation = tableUpdater.getAnnotation(classOf[Table])
    when(annotation != null && annotation.value().trim.isEmpty) {
      Validation(errorMessage(tableUpdater, "@Table name is empty, must be a non-empty string."))
    }
  }

  private def validateQueryResultTypes(component: Class[_]): Validation = {
    val queryMethods =
      component.getMethods.toIndexedSeq.flatMap(m => Option(m.getAnnotation(classOf[Query])).map(_ => m))
    val queriesWithWrongReturnType = queryMethods.filter(m =>
      !(m.getReturnType == classOf[View.QueryEffect[_]] || m.getReturnType == classOf[View.QueryStreamEffect[_]]))

    val wrongReturnTypeValidations = queriesWithWrongReturnType.foldLeft(Valid: Validation) {
      (validation, methodWithWrongReturnType) =>
        validation ++ Validation(
          errorMessage(
            methodWithWrongReturnType,
            s"Query methods must return View.QueryEffect<RowType> or View.QueryStreamEffect<RowType> (was ${methodWithWrongReturnType.getReturnType}."))
    }

    val wrongResultTypeValidations =
      queryMethods.filterNot(queriesWithWrongReturnType.contains).foldLeft(Valid: Validation) { (validation, method) =>
        val resultType = method.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head
        resultType match {
          case clazz: Class[_] if primitiveWrapperClasses(clazz.asInstanceOf[Class[AnyRef]]) =>
            validation ++ Validation(errorMessage(method, s"View query result type ${clazz.getName} is not supported"))
          case _ => validation
        }
      }

    wrongReturnTypeValidations ++ wrongResultTypeValidations
  }

  private def viewQueriesWithStreamUpdatesMustBeStreaming(component: Class[_]): Validation = {
    val streamingUpdatesQueriesWithWrongEffect = component.getMethods.toIndexedSeq.filter { m =>
      val annotation = m.getAnnotation(classOf[Query])
      annotation != null && annotation.streamUpdates() && m.getReturnType != classOf[View.QueryStreamEffect[_]]
    }
    streamingUpdatesQueriesWithWrongEffect.foldLeft(Valid: Validation)((validation, incorrectMethod) =>
      validation ++ Validation(
        errorMessage(
          incorrectMethod,
          s"Query methods marked with streamUpdates must return View.QueryStreamEffect<RowType>")))
  }

  private def viewMultipleTableUpdatersMustHaveTableAnnotations(tableUpdaters: Seq[Class[_]]): Validation =
    if (tableUpdaters.size > 1) {
      tableUpdaters.find(_.getAnnotation(classOf[Table]) eq null) match {
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
        val eventType = Reflect.eventSourcedEntityEventType(classLevel.value())
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

  private def mustHaveValidComponentId(component: Class[_]): Validation = {
    val ann = component.getAnnotation(classOf[ComponentId])
    if (ann != null) {
      val componentId: String = ann.value()
      if ((componentId eq null) || componentId.trim.isEmpty)
        Invalid(errorMessage(component, "@ComponentId name is empty, must be a non-empty string."))
      else if (componentId.contains("|"))
        Invalid(errorMessage(component, "@ComponentId must not contain the pipe character '|'."))
      else Valid
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
      val tableType: Class[_] = Reflect.tableTypeForTableUpdater(tableUpdater)
      val valueEntityClass: Class[_] =
        tableUpdater.getAnnotation(classOf[FromKeyValueEntity]).value().asInstanceOf[Class[_]]
      val entityStateClass = Reflect.keyValueEntityStateType(valueEntityClass)

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
            s"`Effect<${tableType.getName}> onChange(${entityStateClass.getName} state)`."

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

}

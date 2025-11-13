/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import java.lang.annotation.Annotation
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util
import java.util.Optional

import scala.annotation.nowarn
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.reflect.ClassTag

import akka.Done
import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.EvaluationResult
import akka.javasdk.annotations.AgentDescription
import akka.javasdk.annotations.AgentRole
import akka.javasdk.annotations.Component
import akka.javasdk.annotations.ComponentId
import akka.javasdk.annotations.GrpcEndpoint
import akka.javasdk.annotations.http.HttpEndpoint
import akka.javasdk.annotations.mcp.McpEndpoint
import akka.javasdk.client.ComponentClient
import akka.javasdk.consumer.Consumer
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.client.ComponentClientImpl
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.view.TableUpdater
import akka.javasdk.view.View
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.RunnableStep
import com.fasterxml.jackson.annotation.JsonSubTypes

/**
 * Class extension to facilitate some reflection common usages.
 *
 * INTERNAL API
 */
@InternalApi
private[impl] object Reflect {
  object Syntax {

    implicit class ClassOps(cls: Class[_]) {
      def isPublic: Boolean = Modifier.isPublic(cls.getModifiers)

      def annotationOption[A <: Annotation](implicit ev: ClassTag[A]): Option[A] =
        if (cls.isPublic)
          Option(cls.getAnnotation(ev.runtimeClass.asInstanceOf[Class[A]]))
        else
          None

      /**
       * Collects all methods annotated with passed annotation from the given class, including inherited methods.
       */
      def methodsAnnotatedWith[A <: Annotation](implicit ev: ClassTag[A]): IndexedSeq[Method] = {
        val annotationClass = ev.runtimeClass.asInstanceOf[Class[A]]

        def allMethods(c: Class[_]): Seq[Method] = {
          if (c == null || c == classOf[Object]) Seq.empty
          else c.getDeclaredMethods.toSeq ++ allMethods(c.getSuperclass) ++ c.getInterfaces.flatMap(allMethods)
        }

        allMethods(cls).toIndexedSeq
          .filter(m => m.getAnnotation(annotationClass) != null)
          .distinct
      }
    }

    implicit class MethodOps(javaMethod: Method) {
      def isPublic: Boolean = Modifier.isPublic(javaMethod.getModifiers)
    }

    implicit class AnnotatedElementOps(annotated: AnnotatedElement) {
      def hasAnnotation[A <: Annotation](implicit ev: ClassTag[A]): Boolean =
        annotated.getAnnotation(ev.runtimeClass.asInstanceOf[Class[Annotation]]) != null

      def annotationOption[A <: Annotation](implicit ev: ClassTag[A]): Option[A] = {
        Option(annotated.getAnnotation(ev.runtimeClass.asInstanceOf[Class[A]]))
      }
    }

  }

  def isRestEndpoint(cls: Class[_]): Boolean =
    cls.getAnnotation(classOf[HttpEndpoint]) != null

  def isGrpcEndpoint(cls: Class[_]): Boolean =
    cls.getAnnotation(classOf[GrpcEndpoint]) != null

  def isMcpEndpoint(cls: Class[_]): Boolean =
    cls.getAnnotation(classOf[McpEndpoint]) != null

  def isEventSourcedEntity(cls: Class[_]): Boolean =
    classOf[EventSourcedEntity[_, _]].isAssignableFrom(cls)

  def isKeyValueEntity(cls: Class[_]): Boolean =
    classOf[KeyValueEntity[_]].isAssignableFrom(cls)

  def isEntity(cls: Class[_]): Boolean =
    isEventSourcedEntity(cls) || isKeyValueEntity(cls)

  def isWorkflow(cls: Class[_]): Boolean =
    classOf[Workflow[_]].isAssignableFrom(cls)

  def isView(cls: Class[_]): Boolean = extendsView(cls)

  def isConsumer(cls: Class[_]): Boolean = extendsConsumer(cls)

  def isAction(clazz: Class[_]): Boolean = classOf[TimedAction].isAssignableFrom(clazz)

  def isTimedAction(cls: Class[_]): Boolean =
    classOf[TimedAction].isAssignableFrom(cls)

  def isAgent(cls: Class[_]): Boolean =
    classOf[Agent].isAssignableFrom(cls)

  def isToolCandidate(cls: Class[_]): Boolean =
    isEventSourcedEntity(cls) ||
    isKeyValueEntity(cls) ||
    isWorkflow(cls) ||
    isView(cls)

  def isEvaluatorAgent(cls: Class[_]): Boolean = {
    isAgent(cls) && {
      val effectMethod =
        cls.getDeclaredMethods
          .find { method =>
            isCommandHandlerCandidate[Agent.Effect[_]](method)
          }
      effectMethod match {
        case Some(method) =>
          val returnClass = getReturnClass(cls, method)
          classOf[EvaluationResult].isAssignableFrom(returnClass)
        case None =>
          // StreamEffect
          false
      }
    }

  }

  // command handlers candidate must have 0 or 1 parameter and return the components effect type
  // we might later revisit this, instead of single param, we can require (State, Cmd) => Effect like in Akka
  def isCommandHandlerCandidate[E](method: Method)(implicit effectType: ClassTag[E]): Boolean = {
    effectType.runtimeClass.isAssignableFrom(method.getReturnType) &&
    method.getParameterTypes.length <= 1 &&
    // Workflow will have lambdas returning Effect, we want to filter them out
    !method.getName.startsWith("lambda$")
  }

  def getReturnClass[T](declaringClass: Class[_], method: Method): Class[T] =
    getReturnType(declaringClass, method) match {
      case clazz: Class[?]      => clazz.asInstanceOf[Class[T]]
      case p: ParameterizedType => p.getRawType.asInstanceOf[Class[T]]
    }

  def getReturnType(declaringClass: Class[_], method: Method): Type =
    if (isAction(declaringClass) || isEntity(declaringClass) || isWorkflow(declaringClass) || isAgent(declaringClass)) {
      // here we are expecting a wrapper in the form of an Effect
      method.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head
    } else {
      // in other cases we expect a View query method, but declaring class may not extend View[_] class for join views
      method.getReturnType
    }

  def isReturnTypeOptional(method: Method): Boolean = {
    method.getGenericReturnType
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments
      .headOption
      .exists(t =>
        t.isInstanceOf[ParameterizedType] && t.asInstanceOf[ParameterizedType].getRawType == classOf[Optional[_]])
  }

  def keyValueEntityStateType(component: Class[_]): Class[_] = {
    findSingleTypeParam(component, s"Cannot find key value state class for $component")
  }

  /**
   * Find the type parameter class.
   *
   * This method should only be called on a class receiving a single type parameter. For example, given a type defined
   * as F[G], it will return Class[G].
   */
  @tailrec
  private def findSingleTypeParam(current: Class[_], errorMsg: String): Class[_] =
    if (current == classOf[AnyRef])
      // recursed to root without finding type param
      throw new IllegalArgumentException(errorMsg)
    else {
      current.getGenericSuperclass match {
        case parameterizedType: ParameterizedType =>
          if (parameterizedType.getActualTypeArguments.length == 1)
            parameterizedType.getActualTypeArguments.head.asInstanceOf[Class[_]]
          else throw new IllegalArgumentException(errorMsg)
        case noTypeParamsParent: Class[_] =>
          // recurse and look at parent
          findSingleTypeParam(noTypeParamsParent, errorMsg)
      }
    }

  private def extendsView(component: Class[_]): Boolean =
    classOf[View].isAssignableFrom(component)

  private def extendsConsumer(component: Class[_]): Boolean =
    classOf[Consumer].isAssignableFrom(component)

  def isViewTableUpdater(component: Class[_]): Boolean =
    classOf[TableUpdater[_]].isAssignableFrom(component) &&
    Modifier.isStatic(component.getModifiers) &&
    Modifier.isPublic(component.getModifiers)

  def workflowStateType(component: Class[_]): Class[_] = {
    findSingleTypeParam(component, s"Cannot find workflow state class for $component")
  }

  def workflowKnownInputTypes(clz: Class[_]): List[Class[_]] = {

    // register all inputs for methods returning StepEffect
    val stepEffectClass = classOf[Workflow.StepEffect]
    val methods = clz.getDeclaredMethods.filter { m =>
      m.getParameterTypes.length == 1 && stepEffectClass.isAssignableFrom(m.getReturnType)
    }

    methods.foldLeft(List.empty[Class[_]]) { (acc, method) =>
      acc ++ lookupSubClasses(method.getParameterTypes.head)
    }
  }

  @nowarn("msg=deprecated")
  def workflowKnownInputTypes[S, W <: Workflow[S]](workflow: Workflow[S]): List[Class[_]] =
    workflow
      .definition()
      .getSteps
      .asScala
      .flatMap {
        case asyncCallStep: Workflow.AsyncCallStep[_, _, _] =>
          if (asyncCallStep.transitionInputClass == null) lookupSubClasses(asyncCallStep.callInputClass)
          else lookupSubClasses(asyncCallStep.callInputClass) ++ lookupSubClasses(asyncCallStep.transitionInputClass)

        case callStep: Workflow.CallStep[_, _, _] =>
          if (callStep.transitionInputClass == null) lookupSubClasses(callStep.callInputClass)
          else lookupSubClasses(callStep.callInputClass) ++ lookupSubClasses(callStep.transitionInputClass)

        case runnable: RunnableStep => List.empty
      }
      .toList

  /**
   * This method will try to find all know subtypes if the passed class is an interface. Interfaces that are neither
   * sealed nor have JsonSubTypes annotation cannot be handled As we can't deserialize them.
   *
   * Note this method is only used when registering workflow step input types
   */
  private def lookupSubClasses(cls: Class[_]): List[Class[_]] = {

    // primitive types show up as abstract types to Modifier.isAbstract
    // basically, Modifier.isAbstract is not reliable enough in that respect
    // therefore we need this extra isAbstract method
    def isAbstract: Boolean =
      !cls.isInterface &&
      !cls.isPrimitive &&
      Modifier.isAbstract(cls.getModifiers)

    if (cls.isAssignableFrom(classOf[Done])) {
      // especial handling for akka.Done
      // it is a sealed and abstract class, but we know we can deserialize
      // Note that Done is a scala class and Java reflection don't see it as sealed
      List(cls)

    } else if (cls.isSealed) {
      // if sealed, we know what to do
      cls.getPermittedSubclasses.toList :+ cls

    } else if (cls.isInterface || isAbstract) {
      // not a concreate class?
      // then need to find the subtypes using Jackson annotation
      if (cls.hasAnnotation[JsonSubTypes]) {
        val anno = cls.getAnnotation(classOf[JsonSubTypes])
        val subTypes =
          anno.value().foldLeft(List.empty[Class[_]]) { (acc, typ) =>
            acc :+ typ.value()
          }
        subTypes :+ cls
      } else {
        if (cls.isInterface) {
          throw new IllegalArgumentException(
            s"Can't determine all existing subtypes of ${cls.getName}. Interfaces " +
            s"must be either sealed or be annotated with ${classOf[JsonSubTypes].getName}")
        } else {
          throw new IllegalArgumentException(
            s"Can't determine all existing subtypes of ${cls.getName}. Abstract classes " +
            s"must be annotated with ${classOf[JsonSubTypes].getName}")
        }
      }
    } else {
      // we might have a concreate class with subtypes, but in this case
      // there is nothing we can do since the sky is the limit
      List(cls)
    }
  }

  def tableUpdaterRowType(tableUpdater: Class[_]): Class[_] =
    findSingleTypeParam(tableUpdater, s"Cannot find table updater class for ${tableUpdater.getClass}")

  def allKnownEventSourcedEntityEventType(component: Class[_]): Seq[Class[_]] = {
    val eventType = eventSourcedEntityEventType(component)
    eventType.getPermittedSubclasses.toSeq
  }

  def eventSourcedEntityEventType(component: Class[_]): Class[_] =
    concreteEsApplyEventMethod(component).getParameterTypes.head

  def eventSourcedEntityStateType(component: Class[_]): Class[_] =
    concreteEsApplyEventMethod(component).getReturnType

  private def concreteEsApplyEventMethod(component: Class[_]): Method = {
    component.getMethods
      .find(m =>
        m.getName == "applyEvent" &&
        // in case of their own overloads with more params
        m.getParameters.length == 1 &&
        // there the erased method from the base class
        m.getParameterTypes.head != classOf[AnyRef])
      .get // there always is one or else it would not compile
  }

  private implicit val stringArrayOrdering: Ordering[Array[String]] =
    Ordering.fromLessThan(util.Arrays.compare[String](_, _) < 0)

  implicit val methodOrdering: Ordering[Method] =
    Ordering.by((m: Method) => (m.getName, m.getReturnType.getName, m.getParameterTypes.map(_.getName)))

  def lookupComponentClientFields(instance: Any): List[ComponentClientImpl] = {
    // collect all ComponentClients in passed clz
    // also scan superclasses as declaredFields only return fields declared in current class
    // Note: although unlikely, we can't be certain that a user will inject the component client only once
    // nor can we account for single inheritance. ComponentClients can be defined on passed instance or on superclass
    // and users can define different fields for ComponentClient
    @tailrec
    def collectAll(currentClz: Class[_], acc: List[ComponentClientImpl]): List[ComponentClientImpl] = {
      if (currentClz == classOf[Any]) acc // return when reach Object/Any
      else {
        val fields = currentClz.getDeclaredFields
        val clients = // all client instances found in current class definition
          fields
            .collect { case field if field.getType == classOf[ComponentClient] => field }
            .map { field =>
              field.setAccessible(true)
              field.get(instance).asInstanceOf[ComponentClientImpl]
            }
        collectAll(currentClz.getSuperclass, acc ++ clients)
      }
    }

    collectAll(instance.getClass, List.empty)
  }

  def tableTypeForTableUpdater(tableUpdater: Class[_]): Class[_] =
    tableUpdater.getGenericSuperclass
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments
      .head
      .asInstanceOf[Class[_]]

  @nowarn("cat=deprecation")
  def readComponentId(clz: Class[_]): String = {
    val componentAnn = clz.getAnnotation(classOf[akka.javasdk.annotations.Component])
    if (componentAnn != null) componentAnn.id()
    else {
      val componentIdAnn = clz.getAnnotation(classOf[ComponentId])
      if (componentIdAnn != null) componentIdAnn.value() else ""
    }
  }

  def readComponentName(annotated: AnnotatedElement): Option[String] = {
    val componentAnnotation = annotated.getAnnotation(classOf[Component])
    if (componentAnnotation ne null) {
      val name = componentAnnotation.name()
      if (name.nonEmpty) Some(name) else None
    } else {
      None
    }
  }

  def readComponentDescription(annotated: AnnotatedElement): Option[String] = {
    val componentAnnotation = annotated.getAnnotation(classOf[Component])
    if (componentAnnotation ne null) {
      val description = componentAnnotation.description()
      if (description.nonEmpty) Some(description) else None
    } else {
      None
    }
  }

  def readAgentName[A <: Agent](agentClass: Class[A]): Option[String] = {
    val nameOpt = readComponentName(agentClass)
    nameOpt.orElse {
      @nowarn("cat=deprecation")
      val agentDescAnno = agentClass.annotationOption[AgentDescription]
      agentDescAnno.map(_.name())
    }
  }

  def readAgentDescription[A <: Agent](agentClass: Class[A]): Option[String] = {
    val descOpt = readComponentDescription(agentClass)
    descOpt.orElse {
      @nowarn("cat=deprecation")
      val agentDescAnno = agentClass.annotationOption[AgentDescription]
      agentDescAnno.map(_.description())
    }
  }

  def readAgentRole[A <: Agent](agentClass: Class[A]): Option[String] = {
    val agentRoleAnn = agentClass.annotationOption[AgentRole]
    @nowarn("cat=deprecation")
    val agentDescAnno = agentClass.annotationOption[AgentDescription]

    agentRoleAnn
      .map(_.value())
      .orElse(agentDescAnno.map(_.role()))
  }

}

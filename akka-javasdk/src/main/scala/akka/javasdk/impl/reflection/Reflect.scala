/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import akka.annotation.InternalApi
import akka.javasdk.annotations.GrpcEndpoint
import akka.javasdk.annotations.http.HttpEndpoint
import akka.javasdk.annotations.mcp.McpEndpoint
import akka.javasdk.client.ComponentClient
import akka.javasdk.consumer.Consumer
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.client.ComponentClientImpl
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.view.TableUpdater
import akka.javasdk.view.View
import akka.javasdk.workflow.Workflow
import java.lang.annotation.Annotation
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util
import java.util.Optional

import scala.annotation.tailrec
import scala.reflect.ClassTag

import akka.javasdk.agent.Agent

/**
 * Class extension to facilitate some reflection common usages.
 *
 * INTERNAL API
 */
@InternalApi
private[impl] object Reflect {
  object Syntax {

    implicit class ClassOps(clazz: Class[_]) {
      def isPublic: Boolean = Modifier.isPublic(clazz.getModifiers)

      def annotationOption[A <: Annotation](implicit ev: ClassTag[A]): Option[A] =
        if (clazz.isPublic)
          Option(clazz.getAnnotation(ev.runtimeClass.asInstanceOf[Class[A]]))
        else
          None
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

  def isEntity(cls: Class[_]): Boolean =
    classOf[EventSourcedEntity[_, _]].isAssignableFrom(cls) ||
    classOf[KeyValueEntity[_]].isAssignableFrom(cls)

  def isWorkflow(cls: Class[_]): Boolean =
    classOf[Workflow[_]].isAssignableFrom(cls)

  def isView(cls: Class[_]): Boolean = extendsView(cls)

  def isConsumer(cls: Class[_]): Boolean = extendsConsumer(cls)

  def isAction(clazz: Class[_]): Boolean = classOf[TimedAction].isAssignableFrom(clazz)

  def isAgent(cls: Class[_]): Boolean =
    classOf[Agent].isAssignableFrom(cls)

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
    findStateType(component, s"Cannot find key value state class for $component")
  }

  def workflowStateType(component: Class[_]): Class[_] = {
    findStateType(component, s"Cannot find workflow state class for $component")
  }

  @tailrec
  private def findStateType(current: Class[_], errorMsg: String): Class[_] = {
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
          findStateType(noTypeParamsParent, errorMsg)
      }
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

  def workflowStateType[S, W <: Workflow[S]](workflow: W): Class[S] = {
    loop(workflow.getClass, s"Cannot find workflow state class for ${workflow.getClass}").asInstanceOf[Class[S]]
  }

  def kveStateType[S, E <: KeyValueEntity[S]](kve: E): Class[S] = {
    loop(kve.getClass, s"Cannot find Key Value Entity state class for ${kve.getClass}").asInstanceOf[Class[S]]
  }

  @tailrec
  private def loop(current: Class[_], errorMsg: String): Class[_] =
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
          loop(noTypeParamsParent, errorMsg)
      }
    }

  def tableUpdaterRowType(tableUpdater: Class[_]): Class[_] = {
    @tailrec
    def loop(current: Class[_]): Class[_] =
      if (current == classOf[AnyRef])
        // recursed to root without finding type param
        throw new IllegalArgumentException(s"Cannot find table updater class for ${tableUpdater.getClass}")
      else {
        current.getGenericSuperclass match {
          case parameterizedType: ParameterizedType =>
            if (parameterizedType.getActualTypeArguments.size == 1)
              parameterizedType.getActualTypeArguments.head.asInstanceOf[Class[_]]
            else throw new IllegalArgumentException(s"Cannot find table updater class for ${tableUpdater.getClass}")
          case noTypeParamsParent: Class[_] =>
            // recurse and look at parent
            loop(noTypeParamsParent)
        }
      }

    loop(tableUpdater)
  }

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

}

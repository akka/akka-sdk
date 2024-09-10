/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import akka.annotation.InternalApi
import akka.javasdk.annotations.http.HttpEndpoint
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
import java.util
import java.util.Optional

import scala.annotation.tailrec
import scala.reflect.ClassTag

import akka.javasdk.Result

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

      def getAnnotationOption[A <: Annotation](implicit ev: ClassTag[A]): Option[A] =
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

    }

  }

  def isRestEndpoint(cls: Class[_]): Boolean =
    cls.getAnnotation(classOf[HttpEndpoint]) != null

  def isEntity(cls: Class[_]): Boolean =
    classOf[EventSourcedEntity[_, _]].isAssignableFrom(cls) ||
    classOf[KeyValueEntity[_]].isAssignableFrom(cls)

  def isWorkflow(cls: Class[_]): Boolean =
    classOf[Workflow[_]].isAssignableFrom(cls)

  def isView(cls: Class[_]): Boolean = extendsView(cls)

  def isConsumer(cls: Class[_]): Boolean = extendsConsumer(cls)

  def isAction(clazz: Class[_]): Boolean = classOf[TimedAction].isAssignableFrom(clazz)

  def getReturnType[R](declaringClass: Class[_], method: Method): Class[R] = {
    if (isAction(declaringClass) || isEntity(declaringClass) || isWorkflow(declaringClass)) {
      // here we are expecting a wrapper in the form of an Effect
      val returnType = method.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head
      returnType match {
        case parameterizedType: ParameterizedType if parameterizedType.getRawType == classOf[Result[_, _]] =>
          classOf[Result[_, _]].asInstanceOf[Class[R]]
        case other => other.asInstanceOf[Class[R]]
      }
    } else {
      // in other cases we expect a View query method, but declaring class may not extend View[_] class for join views
      method.getReturnType.asInstanceOf[Class[R]]
    }
  }

  def getResultReturnTypes(method: Method): (Class[_], Class[_]) = {
    val resultReturnType = method.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head
    val arguments = resultReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments
    (arguments.head.asInstanceOf[Class[_]], arguments(1).asInstanceOf[Class[_]])
  }

  def isReturnTypeOptional[R](method: Method): Boolean = {
    method.getGenericReturnType
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments
      .headOption
      .exists(t =>
        t.isInstanceOf[ParameterizedType] && t.asInstanceOf[ParameterizedType].getRawType == classOf[Optional[_]])
  }

  private def extendsView(component: Class[_]): Boolean =
    classOf[View].isAssignableFrom(component)

  private def extendsConsumer(component: Class[_]): Boolean =
    classOf[Consumer].isAssignableFrom(component)

  def isViewTableUpdater(component: Class[_]): Boolean =
    classOf[TableUpdater[_]].isAssignableFrom(component) &&
    Modifier.isStatic(component.getModifiers) &&
    Modifier.isPublic(component.getModifiers)

  def allKnownEventTypes[S, E, ES <: EventSourcedEntity[S, E]](entity: ES): Seq[Class[_]] = {
    val eventType = entity.getClass.getGenericSuperclass
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments()(1)
      .asInstanceOf[Class[E]]

    eventType.getPermittedSubclasses.toSeq
  }

  def workflowStateType[S, W <: Workflow[S]](workflow: W): Class[S] =
    workflow.getClass.getGenericSuperclass
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments
      .head
      .asInstanceOf[Class[S]]

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

}

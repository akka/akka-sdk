/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.reflection

import java.lang.annotation.Annotation
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.util

import scala.annotation.tailrec
import scala.reflect.ClassTag

import akka.platform.javasdk.action.Action
import akka.platform.javasdk.annotations.http.Endpoint
import akka.platform.javasdk.client.ComponentClient
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity
import akka.platform.javasdk.impl.client.ComponentClientImpl
import akka.platform.javasdk.keyvalueentity.KeyValueEntity
import akka.platform.javasdk.view.View
import akka.platform.javasdk.workflow.Workflow

/**
 * Class extension to facilitate some reflection common usages.
 */
object Reflect {
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
    cls.getAnnotation(classOf[Endpoint]) != null

  def isEntity(cls: Class[_]): Boolean =
    classOf[EventSourcedEntity[_, _]].isAssignableFrom(cls) ||
    classOf[KeyValueEntity[_]].isAssignableFrom(cls)

  def isWorkflow(cls: Class[_]): Boolean =
    classOf[Workflow[_]].isAssignableFrom(cls)

  def isView(cls: Class[_]): Boolean = isMultiTableView(cls) || extendsView(cls)

  def isAction(clazz: Class[_]): Boolean = classOf[Action].isAssignableFrom(clazz)

  /**
   * A multi-table view doesn't extend View itself, but contains at least one View class.
   */
  def isMultiTableView(component: Class[_]): Boolean = {
    !extendsView(component) && component.getDeclaredClasses.exists(extendsView)
  }

  /**
   * A view table component is a View which is a nested class (static member class) of a multi-table view.
   */
  def isNestedViewTable(component: Class[_]): Boolean = {
    extendsView(component) &&
    (component.getDeclaringClass ne null) &&
    Modifier.isStatic(component.getModifiers)
  }

  def getReturnType[R](declaringClass: Class[_], method: Method): Class[R] = {
    if (isAction(declaringClass) || isEntity(declaringClass) || isWorkflow(declaringClass)) {
      // here we are expecting a wrapper in the form of an Effect
      method.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head.asInstanceOf[Class[R]]
    } else {
      // in other cases we expect a View query method, but declaring class may not extend View[_] class for join views
      method.getReturnType.asInstanceOf[Class[R]]
    }
  }

  private def extendsView(component: Class[_]): Boolean =
    classOf[View[_]].isAssignableFrom(component)

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

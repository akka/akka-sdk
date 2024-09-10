/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import akka.annotation.InternalApi
import akka.javasdk.impl.AclDescriptorFactory

import java.lang.reflect.Method
import scala.annotation.tailrec
import com.google.protobuf.Descriptors
import com.google.protobuf.any.{ Any => ScalaPbAny }

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ServiceMethod {
  def isStreamOut(method: Method): Boolean = false

  // this is more for early validation. We don't support stream-in right now
  // we block it before deploying anything
  def isStreamIn(method: Method): Boolean = false
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] sealed trait ServiceMethod {
  def methodName: String
  def javaMethodOpt: Option[Method]

  def streamIn: Boolean
  def streamOut: Boolean
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] sealed trait AnyJsonRequestServiceMethod extends ServiceMethod {
  def inputType: Class[_]
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] sealed trait UrlTemplate {
  def templateUrl(componentTypeId: String, methodName: String): String
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object EntityUrlTemplate extends UrlTemplate {
  override def templateUrl(componentTypeId: String, methodName: String): String = {
    s"/akka/v1.0/entity/${componentTypeId}/{id}/${methodName}"
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object WorkflowUrlTemplate extends UrlTemplate {
  override def templateUrl(componentTypeId: String, methodName: String): String =
    s"/akka/v1.0/workflow/${componentTypeId}/{id}/${methodName}"
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ViewUrlTemplate extends UrlTemplate {
  override def templateUrl(componentTypeId: String, methodName: String): String =
    s"/akka/v1.0/view/${componentTypeId}/${methodName}"
}

/**
 * Build from command handler methods on Entities and Workflows
 *
 * INTERNAL API
 */
@InternalApi
private[impl] final case class CommandHandlerMethod(
    component: Class[_],
    method: Method,
    urlTemplate: UrlTemplate,
    streamOut: Boolean = false)
    extends AnyJsonRequestServiceMethod {

  override def methodName: String = method.getName
  override def javaMethodOpt: Option[Method] = Some(method)
  val hasInputType: Boolean = method.getParameterTypes.headOption.isDefined
  val inputType: Class[_] = method.getParameterTypes.headOption.getOrElse(classOf[Unit])
  val streamIn: Boolean = false
}

/**
 * Build from command handler methods on actions
 *
 * INTERNAL API
 */
@InternalApi
private[impl] final case class ActionHandlerMethod(component: Class[_], method: Method)
    extends AnyJsonRequestServiceMethod {
  override def methodName: String = method.getName
  override def javaMethodOpt: Option[Method] = Some(method)
  val hasInputType: Boolean = method.getParameterTypes.headOption.isDefined
  val inputType: Class[_] = method.getParameterTypes.headOption.getOrElse(classOf[Unit])
  val streamIn: Boolean = false
  val streamOut: Boolean = false
}

/**
 * Build from methods annotated with @Consume at type level.
 *
 * It's used as a 'virtual' method because there is no Java method backing it. It will exist only in the gRPC descriptor
 * and will be used for view updates with transform = false
 *
 * INTERNAL API
 */
@InternalApi
private[impl] final case class VirtualServiceMethod(component: Class[_], methodName: String, inputType: Class[_])
    extends AnyJsonRequestServiceMethod {

  override def javaMethodOpt: Option[Method] = None

  val streamIn: Boolean = false
  val streamOut: Boolean = false
}

private[impl] final case class CombinedSubscriptionServiceMethod(
    componentName: String,
    combinedMethodName: String,
    methodsMap: Map[String, Method])
    extends AnyJsonRequestServiceMethod {

  val methodName: String = combinedMethodName
  override def inputType: Class[_] = classOf[ScalaPbAny]

  override def javaMethodOpt: Option[Method] = None

  val streamIn: Boolean = false
  val streamOut: Boolean = false
}

/**
 * Build from methods annotated with @Consume. Those methods are not annotated with Spring REST annotations and are only
 * used internally (between proxy and user function).
 *
 * INTERNAL API
 */
@InternalApi
private[impl] final case class SubscriptionServiceMethod(javaMethod: Method) extends AnyJsonRequestServiceMethod {

  val methodName: String = javaMethod.getName
  val inputType: Class[_] = javaMethod.getParameterTypes.head

  override def javaMethodOpt: Option[Method] = Some(javaMethod)

  val streamIn: Boolean = ServiceMethod.isStreamIn(javaMethod)
  val streamOut: Boolean = ServiceMethod.isStreamOut(javaMethod)
}

/**
 * Additional trait to simplify pattern matching for actual and virtual delete service method
 *
 * INTERNAL API
 */
@InternalApi
private[impl] trait DeleteServiceMethod extends ServiceMethod

/**
 * A special case for subscription method with arity zero, in comparison to SubscriptionServiceMethod with required
 * arity one.
 *
 * INTERNAL API
 */
@InternalApi
private[impl] final case class HandleDeletesServiceMethod(javaMethod: Method) extends DeleteServiceMethod {
  override def methodName: String = javaMethod.getName

  override def javaMethodOpt: Option[Method] = Some(javaMethod)

  override def streamIn: Boolean = false

  override def streamOut: Boolean = false
}

/**
 * Similar to VirtualServiceMethod but for deletes.
 *
 * INTERNAL API
 */
@InternalApi
private[impl] final case class VirtualDeleteServiceMethod(component: Class[_], methodName: String)
    extends DeleteServiceMethod {

  override def javaMethodOpt: Option[Method] = None

  override def streamIn: Boolean = false

  override def streamOut: Boolean = false
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object KalixMethod {
  def apply(
      serviceMethod: ServiceMethod,
      methodOptions: Option[kalix.MethodOptions] = None,
      entityIds: Seq[String] = Seq.empty): KalixMethod = {

    val aclOptions =
      serviceMethod.javaMethodOpt.flatMap { meth =>
        AclDescriptorFactory.methodLevelAclAnnotation(meth)
      }

    new KalixMethod(serviceMethod, methodOptions, entityIds)
      .withKalixOptions(aclOptions)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class KalixMethod private (
    serviceMethod: ServiceMethod,
    methodOptions: Option[kalix.MethodOptions] = None,
    entityIds: Seq[String] = Seq.empty) {

  /**
   * KalixMethod is used to collect all the information that we need to produce a gRPC method for the proxy. At the end
   * of the road, we need to check if any incompatibility was created. Therefore the validation should occur when we
   * finish to scan the component and are ready to build the gRPC method.
   *
   * For example, a method eventing.in method with an ACL annotation.
   */
  def validate(): Unit = {
    // check if eventing.in and acl are mixed
    methodOptions.foreach { opts =>
      if (opts.getEventing.hasIn && opts.hasAcl)
        throw ServiceIntrospectionException(
          // safe call: ServiceMethods without a java counterpart won't have ACL anyway
          serviceMethod.javaMethodOpt.get,
          "Subscription methods are for internal use only and cannot be combined with ACL annotations.")
    }
  }

  /**
   * This method merges the new method options with the existing ones. In case of collision the 'opts' are kept
   *
   * @param opts
   * @return
   */
  def withKalixOptions(opts: kalix.MethodOptions): KalixMethod =
    copy(methodOptions = Some(mergeKalixOptions(methodOptions, opts)))

  /**
   * This method merges the new method options with the existing ones. In case of collision the 'opts' are kept
   * @param opts
   * @return
   */
  def withKalixOptions(opts: Option[kalix.MethodOptions]): KalixMethod =
    opts match {
      case Some(methodOptions) => withKalixOptions(methodOptions)
      case None                => this
    }

  private[akka] def mergeKalixOptions(
      source: Option[kalix.MethodOptions],
      addOn: kalix.MethodOptions): kalix.MethodOptions = {
    val builder = source match {
      case Some(src) => src.toBuilder
      case None      => kalix.MethodOptions.newBuilder()
    }
    builder.mergeFrom(addOn)
    builder.build()
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] trait ExtractorCreator {
  def apply(descriptor: Descriptors.Descriptor): ParameterExtractor[DynamicMessageContext, AnyRef]
}

/**
 * Ensures all generated names in a given package are unique, noting that grpcMethod names and message names must not
 * conflict.
 *
 * Note that it is important to make sure that invoking this is done in an deterministic order or else JVMs on different
 * nodes will generate different names for the same method. Sorting can be done using ReflectionUtils.methodOrdering
 *
 * INTERNAL API
 */
@InternalApi
private[impl] final class NameGenerator {
  private var names: Set[String] = Set.empty

  def getName(base: String): String = {
    if (names(base)) {
      incrementName(base, 1)
    } else {
      names += base
      base
    }
  }

  @tailrec
  private def incrementName(base: String, inc: Int): String = {
    val name = base + inc
    if (names(name)) {
      incrementName(base, inc + 1)
    } else {
      names += name
      name
    }
  }
}

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client

import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util
import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.http.scaladsl.model.HttpMethods
import com.google.protobuf.any.Any
import kalix.javasdk.DeferredCall
import kalix.javasdk.Metadata
import kalix.javasdk.action.Action
import kalix.javasdk.annotations.TypeId
import kalix.javasdk.eventsourcedentity.EventSourcedEntity
import kalix.javasdk.impl.client.MethodRefResolver
import kalix.javasdk.impl.reflection.EntityUrlTemplate
import kalix.javasdk.impl.reflection.IdExtractor
import kalix.javasdk.impl.reflection.RestServiceIntrospector
import kalix.javasdk.impl.reflection.RestServiceIntrospector.BodyParameter
import kalix.javasdk.impl.reflection.RestServiceIntrospector.PathParameter
import kalix.javasdk.impl.reflection.RestServiceIntrospector.QueryParamParameter
import kalix.javasdk.impl.reflection.RestServiceIntrospector.RestService
import kalix.javasdk.impl.reflection.SyntheticRequestServiceMethod
import kalix.javasdk.valueentity.ValueEntity
import kalix.javasdk.workflow.Workflow
import kalix.spring.impl.KalixClient
import kalix.spring.impl.RestKalixClientImpl
import org.springframework.web.bind.annotation.RequestMethod
import reactor.core.publisher.Flux

object ComponentMethodRef {

  def noParams[R](
      kalixClient: KalixClient,
      lambda: scala.Any,
      ids: util.List[String],
      callMetadata: Optional[Metadata]): DeferredCall[Any, R] = {
    deferred(Seq.empty, kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids.asScala.toList, callMetadata)
  }

  def noParams[R](
      kalixClient: KalixClient,
      method: Method,
      ids: util.List[String],
      callMetadata: Optional[Metadata]): DeferredCall[Any, R] = {
    deferred(Seq.empty, kalixClient, method, ids.asScala.toList, callMetadata)
  }

  private[client] def deferred[R](
      params: Seq[scala.Any],
      kalixClient: KalixClient,
      method: Method,
      ids: List[String],
      callMetadata: Optional[Metadata]): DeferredCall[Any, R] = {

    val kalixClientImpl = kalixClient.asInstanceOf[RestKalixClientImpl]
    val declaringClass = method.getDeclaringClass
    val returnType: Class[R] = getReturnType(declaringClass, method)

    val deferredCall =
      if (classOf[EventSourcedEntity[_, _]].isAssignableFrom(declaringClass) ||
        classOf[ValueEntity[_]].isAssignableFrom(declaringClass)) {

        val typeId = declaringClass.getAnnotation(classOf[TypeId]).value()
        val template = EntityUrlTemplate.templateUrl(typeId, method.getName)
        val pathParameter = Map("id" -> ids.head) // always only a single id
        if (params.nonEmpty) {
          val body = params.headOption.map {
            // little hack to accept POST body as raw string
            // json raw string must be wrapped with quotes, like in "my-string"
            // so users would need to pass "\"my-string\"", this hack let user pass a String without needing to quote it
            case str: String if !str.startsWith("\"") && !str.endsWith("\"") => s"\"$str\""
            case any                                                         => any
          }
          kalixClientImpl.runWithBody(HttpMethods.POST, template, pathParameter, Map.empty, body, returnType)
        } else {
          kalixClientImpl.runWithoutBody(HttpMethods.GET, template, pathParameter, Map.empty, returnType)
        }

      } else {

        val restService: RestService = RestServiceIntrospector.inspectService(declaringClass)
        val restMethod: SyntheticRequestServiceMethod =
          restService.methods.find(_.javaMethod.getName == method.getName) match {
            case Some(method) => method
            case None =>
              throw new IllegalStateException(s"Method [${method.getName}] is not annotated as a REST endpoint.")
          }

        val requestMethod: RequestMethod = restMethod.requestMethod

        val queryParams: Map[String, util.List[scala.Any]] = restMethod.params
          .collect { case p: QueryParamParameter => p }
          .map(p => (p.name, getQueryParam(params, p.param.getParameterIndex)))
          .toMap

        val pathVariables: Map[String, ?] = restMethod.params
          .collect { case p: PathParameter => p }
          .map(p => (p.name, getPathParam(params, p.param.getParameterIndex, p.name)))
          .toMap ++ idVariables(ids, method)

        val bodyIndex =
          restMethod.params.collect { case p: BodyParameter => p }.map(_.param.getParameterIndex).headOption
        val body = bodyIndex.map(params(_))
        val pathTemplate = restMethod.parsedPath.path

        requestMethod match {
          case RequestMethod.GET =>
            kalixClientImpl.runWithoutBody(HttpMethods.GET, pathTemplate, pathVariables, queryParams, returnType)
          case RequestMethod.HEAD => notSupported(requestMethod, pathTemplate)
          case RequestMethod.POST =>
            kalixClientImpl.runWithBody(HttpMethods.POST, pathTemplate, pathVariables, queryParams, body, returnType)
          case RequestMethod.PUT =>
            kalixClientImpl.runWithBody(HttpMethods.PUT, pathTemplate, pathVariables, queryParams, body, returnType)
          case RequestMethod.PATCH =>
            kalixClientImpl.runWithBody(HttpMethods.PATCH, pathTemplate, pathVariables, queryParams, body, returnType)
          case RequestMethod.DELETE =>
            kalixClientImpl.runWithoutBody(HttpMethods.DELETE, pathTemplate, pathVariables, queryParams, returnType)
          case RequestMethod.OPTIONS => notSupported(requestMethod, pathTemplate)
          case RequestMethod.TRACE   => notSupported(requestMethod, pathTemplate)
        }

      }

    if (callMetadata.isEmpty) deferredCall
    else deferredCall.withMetadata(callMetadata.get)
  }

  private def getReturnType[R](declaringClass: Class[_], method: Method): Class[R] = {
    if (classOf[Action].isAssignableFrom(declaringClass)
      || classOf[ValueEntity[_]].isAssignableFrom(declaringClass)
      || classOf[EventSourcedEntity[_, _]].isAssignableFrom(declaringClass)
      || classOf[Workflow[_]].isAssignableFrom(declaringClass)) {
      // here we are expecting a wrapper in the form of an Effect
      method.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head.asInstanceOf[Class[R]]
    } else {
      // in other cases we expect a View query method, but declaring class may not extend View[_] class for join views
      val viewReturnType = method.getReturnType
      if (classOf[Flux[_]].isAssignableFrom(viewReturnType)) {
        throw new IllegalStateException("Deferred call with a Flux<?> response type are currently not supported.")
      } else {
        viewReturnType.asInstanceOf[Class[R]]
      }
    }
  }

  private def getQueryParam(params: Seq[scala.Any], parameterIndex: Int): util.List[scala.Any] = {
    val value = params(parameterIndex)
    if (value == null) {
      util.List.of()
    } else if (value.isInstanceOf[util.List[_]]) {
      value.asInstanceOf[util.List[scala.Any]]
    } else {
      util.List.of(value)
    }
  }

  private def getPathParam(params: Seq[scala.Any], parameterIndex: Int, paramName: String): scala.Any = {
    val value = params(parameterIndex)
    if (value == null) {
      throw new IllegalStateException(s"Path param [$paramName] cannot be null.")
    }
    value
  }

  private def notSupported(requestMethod: RequestMethod, pathTemplate: String) = {
    throw new IllegalStateException(s"HTTP $requestMethod not supported when calling $pathTemplate")
  }

  private def idVariables(ids: List[String], method: Method): Map[String, String] = {

    val declaringClass = method.getDeclaringClass
    if (declaringClass.getAnnotation(classOf[TypeId]) == null) {
      //not an entity or workflows
      Map.empty
    } else if (IdExtractor.shouldGenerateId(method)) {
      Map.empty
    } else {
      val idNames = IdExtractor.extractIds(declaringClass, method)
      if (ids.isEmpty) {
        throw new IllegalStateException(s"Id is missing when calling [${method.getName}] method")
      } else if (ids.size != idNames.size) {
        throw new IllegalStateException(
          s"Expecting ${idNames.size} instead of ${ids.size} when calling [${method.getName}] method. Provide values for [${idNames
            .mkString(", ")}] ids.")
      } else if (idNames.size == 1) { //single key
        Map(idNames.head -> ids.head)
      } else { //compound key
        idNames.zip(ids).toMap
      }
    }
  }
}

// TODO: this and all ComponentMethodRef variants deserve javadoc explaining what it really is
final class ComponentMethodRef[R](
    kalixClient: KalixClient,
    method: Method,
    id: String,
    metadataOpt: Optional[Metadata]) {

  /** @return ComponentMethodRef with updated metadata */
  def withMetadata(metadata: Metadata): ComponentMethodRef[R] = {
    val merged = metadataOpt.toScala.map(m => m.merge(metadata)).getOrElse(metadata)
    new ComponentMethodRef[R](this.kalixClient, this.method, this.id, Optional.of(merged))
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, id: String, metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), id, metadataOpt)
  }

  // TODO: this and all deferred variants deserve javadoc explaining what it really is and when it should be used
  def deferred(): DeferredCall[Any, R] =
    ComponentMethodRef.noParams(kalixClient, method, util.List.of(id), metadataOpt)

  // TODO: this and all invokeAsync variants deserve javadoc explaining what it really is and when it should be used
  def invokeAsync(): CompletionStage[R] =
    deferred().invokeAsync()
}

final class ComponentMethodRef1[A1, R](
    kalixClient: KalixClient,
    method: Method,
    ids: util.List[String],
    metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata])
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /** @return ComponentMethodRef1 with updated metadata */
  def withMetadata(metadata: Metadata): ComponentMethodRef1[A1, R] = {
    val merged = metadataOpt.toScala.map(m => m.merge(metadata)).getOrElse(metadata)
    new ComponentMethodRef1[A1, R](this.kalixClient, this.method, this.ids, Optional.of(merged))
  }

  def deferred(a1: A1): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1), kalixClient, method, ids.asScala.toList, metadataOpt)
  }

  def invokeAsync(a1: A1): CompletionStage[R] =
    deferred(a1).invokeAsync()
}

// format: off
final class ComponentMethodRef2[A1, A2, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef3[A1, A2, A3, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef4[A1, A2, A3, A4, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {


  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4), kalixClient, lambda, ids.asScala.toList,metadataOpt)
  }
}
final class ComponentMethodRef5[A1, A2, A3, A4, A5, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef6[A1, A2, A3, A4, A5, A6, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {


  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef7[A1, A2, A3, A4, A5, A6, A7, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef8[A1, A2, A3, A4, A5, A6, A7, A8, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef9[A1, A2, A3, A4, A5, A6, A7, A8, A9, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12, a13: A13): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12, a13: A13, a14: A14): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }



  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12, a13: A13, a14: A14, a15: A15): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12, a13: A13, a14: A14, a15: A15, a16: A16): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12, a13: A13, a14: A14, a15: A15, a16: A16, a17: A17): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12, a13: A13, a14: A14, a15: A15, a16: A16, a17: A17, a18: A18): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef19[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12, a13: A13, a14: A14, a15: A15, a16: A16, a17: A17, a18: A18, a19: A19): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef20[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12, a13: A13, a14: A14, a15: A15, a16: A16, a17: A17, a18: A18, a19: A19, a20: A20): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
final class ComponentMethodRef21[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, R](kalixClient: KalixClient, lambda: Method, ids: util.List[String], metadataOpt: Optional[Metadata]) {

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, Optional.empty[Metadata]())
  }

  def this(kalixClient: KalixClient, lambda: scala.Any, ids: util.List[String], metadataOpt: Optional[Metadata]) = {
    this(kalixClient, MethodRefResolver.resolveMethodRef(lambda), ids, metadataOpt)
  }

  /**
   * Pass in the parameters that are required to execute this call.
   *
   * The types and order of parameters are the same as defined in the method reference
   * used to build this DeferredCall.
   */
  def deferred(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8, a9: A9, a10: A10, a11: A11, a12: A12, a13: A13, a14: A14, a15: A15, a16: A16, a17: A17, a18: A18, a19: A19, a20: A20, a21: A21): DeferredCall[Any, R] = {
    ComponentMethodRef.deferred(Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21), kalixClient, lambda, ids.asScala.toList, metadataOpt)
  }
}
// format: on

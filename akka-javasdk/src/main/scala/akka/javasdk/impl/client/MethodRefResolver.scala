/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.lang.invoke.SerializedLambda
import java.lang.reflect.Method

private[impl] object MethodRefResolver {

  /**
   * Holds both the resolved method and the component class extracted from the instantiated method type (first parameter
   * of the functional interface, which is the unbound receiver for `ComponentClass::method` style references).
   */
  case class MethodRefInfo(method: Method, componentClass: Class[_])

  /**
   * Resolve the method ref for a lambda.
   */
  def resolveMethodRef(lambda: Any): Method =
    resolveMethodRefInfo(lambda).method

  /**
   * Resolve the method ref for a lambda, also returning the component class (the receiver type extracted from the
   * instantiated method type of the functional interface). This correctly identifies the concrete agent class even when
   * the method is inherited from a superclass or declared as a default interface method.
   */
  def resolveMethodRefInfo(lambda: Any): MethodRefInfo = {
    val lambdaType = lambda.getClass

    if (!classOf[java.io.Serializable].isInstance(lambda)) {
      throw new IllegalArgumentException(
        "Can only resolve method references from serializable SAMs, class was: " + lambdaType)
    }

    val writeReplace =
      try {
        lambda.getClass.getDeclaredMethod("writeReplace")
      } catch {
        case e: NoSuchMethodError =>
          throw new IllegalArgumentException(
            "Passed in object does not provide a writeReplace method, hence it can't be a Java 8 method reference.",
            e)
      }

    writeReplace.setAccessible(true)

    val serializedLambda = writeReplace.invoke(lambda) match {
      case s: SerializedLambda => s
      case _ =>
        throw new IllegalArgumentException(
          "Passed in object does not writeReplace itself with SerializedLambda, hence it can't be a Java 8 method reference.")
    }

    if (serializedLambda.getImplClass.equals("<init>")) {
      throw new IllegalArgumentException("Passed in method ref is a constructor.")
    }

    // Load the class the method is implemented on (may be a superclass or interface)
    val implClass = loadClass(lambdaType.getClassLoader, serializedLambda.getImplClass)
    val argumentClasses = getArgumentClasses(lambdaType.getClassLoader, serializedLambda.getImplMethodSignature)

    // Try getDeclaredMethod first; fall back to getMethod for inherited / default interface methods
    val method =
      try {
        implClass.getDeclaredMethod(serializedLambda.getImplMethodName, argumentClasses: _*)
      } catch {
        case _: NoSuchMethodException =>
          implClass.getMethod(serializedLambda.getImplMethodName, argumentClasses: _*)
      }

    // For unbound instance method references `ComponentClass::method`, the instantiated method
    // type's first parameter is the receiver type (i.e. the concrete component class), even when
    // the method itself is inherited from a superclass or default interface method.
    val instantiatedArgs =
      getArgumentClasses(lambdaType.getClassLoader, serializedLambda.getInstantiatedMethodType)
    val componentClass = if (instantiatedArgs.nonEmpty) instantiatedArgs.head else implClass

    MethodRefInfo(method, componentClass)
  }

  private def loadClass(classLoader: ClassLoader, internalName: String) = {
    Class.forName(internalName.replace('/', '.'), false, classLoader)
  }

  private def getArgumentClasses(classLoader: ClassLoader, methodDescriptor: String): List[Class[_]] = {
    def parseArgumentClasses(offset: Int, arrayDepth: Int): List[Class[_]] = {
      methodDescriptor.charAt(offset) match {
        case ')' => Nil
        case 'L' =>
          val end = methodDescriptor.indexOf(';', offset)
          val className = if (arrayDepth > 0) {
            methodDescriptor.substring(offset - arrayDepth, end)
          } else {
            methodDescriptor.substring(offset + 1, end)
          }
          loadClass(classLoader, className) :: parseArgumentClasses(end + 1, 0)
        case '[' =>
          parseArgumentClasses(offset + 1, arrayDepth + 1)
        case _ if arrayDepth > 0 =>
          val className = methodDescriptor.substring(offset - arrayDepth, offset + 1)
          loadClass(classLoader, className) :: parseArgumentClasses(offset + 1, 0)
        case other =>
          val clazz = other match {
            case 'Z'     => classOf[Boolean]
            case 'C'     => classOf[Char]
            case 'B'     => classOf[Byte]
            case 'S'     => classOf[Short]
            case 'I'     => classOf[Int]
            case 'F'     => classOf[Float]
            case 'J'     => classOf[Long]
            case 'D'     => classOf[Double]
            case unknown => throw sys.error("Unknown primitive type: " + unknown)
          }
          clazz :: parseArgumentClasses(offset + 1, 0)
      }
    }

    parseArgumentClasses(1, 0)
  }
}

/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.io.InputStream
import java.lang.invoke.SerializedLambda
import java.lang.reflect.Method

import scala.jdk.CollectionConverters._

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode

private[impl] object MethodRefResolver {

  /**
   * Resolve the method ref for a lambda.
   *
   * Java method references resolve directly via SerializedLambda. Scala lambdas always wrap the call in a synthetic
   * $anonfun$ method; in that case we read the bytecode of that method to locate the single delegated call and return
   * the real target method.
   */
  def resolveMethodRef(lambda: Any): Method = {
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

    // Try to load the class that the method ref is defined on
    val ownerClass = loadClass(lambdaType.getClassLoader, serializedLambda.getImplClass)

    val argumentClasses = getArgumentClasses(lambdaType.getClassLoader, serializedLambda.getImplMethodSignature)
    if (serializedLambda.getImplClass.equals("<init>")) {
      throw new IllegalArgumentException("Passed in method ref is a constructor.")
    } else if (serializedLambda.getImplMethodName.startsWith("$anonfun$")) {
      // Scala lambda: the impl method is a synthetic $anonfun$ wrapper in the enclosing class.
      // We inspect its bytecode to find the single delegated method call.
      resolveScalaLambdaDelegate(
        lambdaType.getClassLoader,
        serializedLambda.getImplClass,
        serializedLambda.getImplMethodName)
    } else {
      ownerClass.getDeclaredMethod(serializedLambda.getImplMethodName, argumentClasses: _*)
    }
  }

  // Reads the bytecode of the synthetic $anonfun$ method and finds the single INVOKEVIRTUAL /
  // INVOKEINTERFACE instruction that represents the real target method.
  private def resolveScalaLambdaDelegate(
      classLoader: ClassLoader,
      implClass: String,
      implMethodName: String): Method = {
    val stream: InputStream = classLoader.getResourceAsStream(implClass + ".class")
    if (stream == null)
      throw new IllegalArgumentException(s"Cannot load bytecode for $implClass to resolve Scala lambda")

    val classBytes =
      try stream.readAllBytes()
      finally stream.close()

    val classNode = new ClassNode()
    new ClassReader(classBytes).accept(classNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG)

    val lambdaMethod = classNode.methods.asScala
      .find(_.name == implMethodName)
      .getOrElse(throw new IllegalArgumentException(
        s"Cannot find synthetic lambda method '$implMethodName' in class $implClass"))

    val delegateCalls = lambdaMethod.instructions.toArray.collect {
      case insn: MethodInsnNode
          if (insn.getOpcode == Opcodes.INVOKEVIRTUAL || insn.getOpcode == Opcodes.INVOKEINTERFACE)
            && insn.owner != "java/lang/Object" =>
        insn
    }

    if (delegateCalls.length != 1)
      throw new IllegalArgumentException(
        s"Scala lambda body must contain exactly one virtual method call to be resolved as a method " +
        s"reference, found ${delegateCalls.length}. Use a simple method reference: entity => entity.someMethod()")

    val target = delegateCalls.head
    val targetClass = loadClass(classLoader, target.owner)
    val targetArgClasses = getArgumentClasses(classLoader, target.desc)
    try {
      targetClass.getDeclaredMethod(target.name, targetArgClasses: _*)
    } catch {
      case e: NoSuchMethodException =>
        throw new IllegalArgumentException(s"Could not find method '${target.name}' in ${targetClass.getName}", e)
    }
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

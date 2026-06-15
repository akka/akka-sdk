/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import akka.japi.{ function => jfn }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object MethodRefResolverSpec {
  class SomeEntity {
    def getState: String = "state"
    def processCommand(cmd: String): String = cmd
  }
}

class MethodRefResolverSpec extends AnyWordSpec with Matchers {
  import MethodRefResolverSpec._

  "MethodRefResolver" should {

    "resolve a Scala lambda with no args to the underlying method" in {
      // Scala always wraps the call in a synthetic $anonfun$ method; we read the bytecode to find
      // the real target.
      val ref: jfn.Function[SomeEntity, String] = _.getState
      val method = MethodRefResolver.resolveMethodRef(ref)
      method.getName shouldBe "getState"
      method.getDeclaringClass shouldBe classOf[SomeEntity]
      method.getParameterCount shouldBe 0
    }

    "resolve a Scala lambda with one arg to the underlying method" in {
      val ref: jfn.Function2[SomeEntity, String, String] = _.processCommand(_)
      val method = MethodRefResolver.resolveMethodRef(ref)
      method.getName shouldBe "processCommand"
      method.getDeclaringClass shouldBe classOf[SomeEntity]
      method.getParameterTypes shouldBe Array(classOf[String])
    }

    "reject a Scala lambda with multiple method calls" in {
      val ref: jfn.Function[SomeEntity, String] = e => e.getState + e.processCommand("x")
      an[IllegalArgumentException] should be thrownBy MethodRefResolver.resolveMethodRef(ref)
    }
  }
}

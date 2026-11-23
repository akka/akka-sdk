/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.DependencyProvider
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OverridingDependencyProviderSpec extends AnyWordSpec with Matchers {

  private val delegate = new DependencyProvider {
    override def getDependency[T](clazz: Class[T]): T =
      if (clazz == classOf[String]) "delegate".asInstanceOf[T]
      else if (clazz == classOf[Integer]) Integer.valueOf(1).asInstanceOf[T]
      else throw new IllegalArgumentException("Unknown dependency type " + clazz.getName)
  }

  private val overrides = Map[Class[_], Any](classOf[String] -> "override")

  "OverridingDependencyProvider" should {

    "return the override for a class that has one" in {
      new OverridingDependencyProvider(overrides, Some(delegate)).getDependency(classOf[String]) shouldBe "override"
    }

    "ask the delegate for a class without an override" in {
      new OverridingDependencyProvider(overrides, Some(delegate)).getDependency(classOf[Integer]) shouldBe 1
    }

    "match the exact class only" in {
      intercept[IllegalArgumentException] {
        new OverridingDependencyProvider(overrides, Some(delegate)).getDependency(classOf[CharSequence])
      }
    }

    "return the override without a delegate" in {
      new OverridingDependencyProvider(overrides, None).getDependency(classOf[String]) shouldBe "override"
    }

    "fail for a class without an override when there is no delegate" in {
      val message = intercept[IllegalStateException] {
        new OverridingDependencyProvider(overrides, None).getDependency(classOf[Integer])
      }.getMessage
      message should include("java.lang.Integer")
      message should include("no DependencyProvider was configured")
      message should include("setup-and-dependency-injection.html")
    }

    "tell which classes it can provide" in {
      new OverridingDependencyProvider(overrides, None).canProvide(classOf[String]) shouldBe true
      new OverridingDependencyProvider(overrides, None).canProvide(classOf[Integer]) shouldBe false
      new OverridingDependencyProvider(overrides, Some(delegate)).canProvide(classOf[Integer]) shouldBe true
    }
  }
}

/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import scala.jdk.CollectionConverters._

import akka.javasdk.agent.ContentLoader
import akka.javasdk.agent.MessageContent
import akka.javasdk.agent.autonomous.capability.TaskAcceptance
import akka.javasdk.agent.task.Task
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentDefinitionSpec extends AnyWordSpec with Matchers {

  private val testTask = Task
    .name("TestTask")
    .description("A test task")
    .resultConformsTo(classOf[String])

  private val loader = new ContentLoader {
    override def load(content: MessageContent.LoadableMessageContent): ContentLoader.LoadedContent =
      ContentLoader.LoadedContent.from(Array.emptyByteArray)
  }

  private def impl(d: akka.javasdk.agent.autonomous.AgentDefinition): AgentDefinitionImpl =
    d.asInstanceOf[AgentDefinitionImpl]

  "AgentDefinition" should {

    "start empty" in {
      val d = impl(AgentDefinitionImpl.empty())

      d.instructions shouldBe ""
      d.capabilities.asScala shouldBe empty
      d.contentLoader shouldBe None
    }

    "set instructions" in {
      val d = impl(AgentDefinitionImpl.empty().instructions("Do something"))

      d.instructions shouldBe "Do something"
    }

    "accumulate capabilities" in {
      val d = impl(
        AgentDefinitionImpl
          .empty()
          .capability(TaskAcceptance.of(testTask))
          .capability(TaskAcceptance.of(testTask)))

      d.capabilities.asScala should have size 2
    }

    "set contentLoader" in {
      val d = impl(AgentDefinitionImpl.empty().contentLoader(loader))

      d.contentLoader shouldBe Some(loader)
    }

    "override contentLoader" in {
      val first = new ContentLoader {
        override def load(content: MessageContent.LoadableMessageContent): ContentLoader.LoadedContent =
          ContentLoader.LoadedContent.from(Array.emptyByteArray)
      }
      val d = impl(AgentDefinitionImpl.empty().contentLoader(first).contentLoader(loader))

      d.contentLoader shouldBe Some(loader)
    }

    "be immutable — each method returns a new instance" in {
      val d1 = AgentDefinitionImpl.empty()
      val d2 = d1.instructions("Some instructions")
      val d3 = d2.contentLoader(loader)
      val d4 = d3.capability(TaskAcceptance.of(testTask))

      impl(d1).instructions shouldBe ""
      impl(d1).contentLoader shouldBe None
      impl(d1).capabilities.asScala shouldBe empty

      impl(d2).instructions shouldBe "Some instructions"
      impl(d2).contentLoader shouldBe None

      impl(d3).instructions shouldBe "Some instructions"
      impl(d3).contentLoader shouldBe Some(loader)
      impl(d3).capabilities.asScala shouldBe empty

      impl(d4).instructions shouldBe "Some instructions"
      impl(d4).contentLoader shouldBe Some(loader)
      impl(d4).capabilities.asScala should have size 1
    }
  }
}

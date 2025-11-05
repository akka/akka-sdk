/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.jdk.CollectionConverters._

import akka.javasdk.agent.MemoryFilter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MemoryFilterSupplierSpec extends AnyWordSpec with Matchers {

  "MemoryFilterSupplier" should {

    "merge same-type filters when chained" in {
      // given - chain multiple filters of the same type
      val filterSupplier = MemoryFilter
        .includeFromAgentId("agent-1")
        .includeFromAgentId("agent-2")

      // when - get the list of filters
      val filters = filterSupplier.get().asScala.toList

      // then - should have a single filter with both IDs in order
      filters should have size 1
      filters.head shouldBe a[MemoryFilter.Include]

      val filter = filters.head.asInstanceOf[MemoryFilter.Include]
      filter.ids().asScala should contain theSameElementsAs Set("agent-1", "agent-2")
    }

    "merge same-type filters with lists and preserve order" in {
      // given - chain filters with lists
      val filterSupplier = MemoryFilter
        .includeFromAgentIds(Set("agent-1", "agent-2").asJava)
        .includeFromAgentId("agent-3")
        .includeFromAgentIds(Set("agent-4", "agent-5").asJava)

      // when
      val filters = filterSupplier.get().asScala.toList

      // then - should merge all into one filter preserving the order
      filters should have size 1
      filters.head shouldBe a[MemoryFilter.Include]

      val filter = filters.head.asInstanceOf[MemoryFilter.Include]
      filter.ids().asScala should contain theSameElementsAs Set("agent-1", "agent-2", "agent-3", "agent-4", "agent-5")
    }

    "merge excludeFromAgentId filters and preserve order" in {
      // given
      val filterSupplier = MemoryFilter
        .excludeFromAgentId("agent-1")
        .excludeFromAgentId("agent-2")
        .excludeFromAgentId("agent-3")

      // when
      val filters = filterSupplier.get().asScala.toList

      // then
      filters should have size 1
      filters.head shouldBe a[MemoryFilter.Exclude]

      val filter = filters.head.asInstanceOf[MemoryFilter.Exclude]
      filter.ids().asScala should contain theSameElementsAs Set("agent-1", "agent-2", "agent-3")
    }

    "merge includeFromAgentRole filters and preserve order" in {
      // given
      val filterSupplier = MemoryFilter
        .includeFromAgentRole("summarizer")
        .includeFromAgentRole("translator")
        .includeFromAgentRole("analyzer")

      // when
      val filters = filterSupplier.get().asScala.toList

      // then
      filters should have size 1
      filters.head shouldBe a[MemoryFilter.Include]

      val filter = filters.head.asInstanceOf[MemoryFilter.Include]
      filter.roles().asScala should contain theSameElementsAs Set("summarizer", "translator", "analyzer")
    }

    "merge excludeFromAgentRole filters and preserve order" in {
      // given
      val filterSupplier = MemoryFilter
        .excludeFromAgentRole("debug")
        .excludeFromAgentRole("internal")
        .excludeFromAgentRole("test")

      // when
      val filters = filterSupplier.get().asScala.toList

      // then
      filters should have size 1
      filters.head shouldBe a[MemoryFilter.Exclude]

      val filter = filters.head.asInstanceOf[MemoryFilter.Exclude]
      filter.roles().asScala should contain theSameElementsAs Set("debug", "internal", "test")
    }

    "merge same-type filters into existing filter preserving order" in {
      // given - mix same and different filter types
      // When adding a filter of the same type, it should merge into the existing one
      val filterSupplier = MemoryFilter
        .includeFromAgentId("agent-1")
        .excludeFromAgentRole("debug")
        .includeFromAgentId("agent-2") // should merge with first includeFromAgentId
        .excludeFromAgentRole("internal") // should merge with first excludeFromAgentRole
        .includeFromAgentRole("summarizer")

      // when
      val filters = filterSupplier.get().asScala.toList

      // then - should have 3 filters with merged same-type filters
      filters should have size 2
      filters.head shouldBe a[MemoryFilter.Include]
      filters(1) shouldBe a[MemoryFilter.Exclude]

      val filter0 = filters.head.asInstanceOf[MemoryFilter.Include]
      filter0.ids().asScala should contain theSameElementsAs Set("agent-1", "agent-2")
      filter0.roles().asScala should contain theSameElementsAs Set("summarizer")

      val filter1 = filters(1).asInstanceOf[MemoryFilter.Exclude]
      filter1.roles().asScala should contain theSameElementsAs Set("debug", "internal")
    }

    "work with single filter" in {
      // given
      val filterSupplier = MemoryFilter.includeFromAgentId("agent-1")

      // when
      val filters = filterSupplier.get().asScala.toList

      // then
      filters should have size 1
      filters.head shouldBe a[MemoryFilter.Include]

      val filter = filters.head.asInstanceOf[MemoryFilter.Include]
      filter.ids().asScala.toList shouldBe List("agent-1")
    }

    "keep different filter types separate" in {
      // given - use all different filter types (each type only once)
      val filterSupplier = MemoryFilter
        .includeFromAgentId("agent-1")
        .excludeFromAgentId("agent-2")
        .includeFromAgentRole("summarizer")
        .excludeFromAgentRole("debug")

      // when
      val filters = filterSupplier.get().asScala.toList

      // then - should have 4 separate filters since they are all different types
      filters should have size 2
      filters.head shouldBe a[MemoryFilter.Include]
      filters(1) shouldBe a[MemoryFilter.Exclude]

      val filter0 = filters.head.asInstanceOf[MemoryFilter.Include]
      filter0.ids().asScala should contain theSameElementsAs Set("agent-1")
      filter0.roles().asScala should contain theSameElementsAs Set("summarizer")

      val filter1 = filters(1).asInstanceOf[MemoryFilter.Exclude]
      filter1.ids().asScala should contain theSameElementsAs Set("agent-2")
      filter1.roles().asScala should contain theSameElementsAs Set("debug")
    }

  }
}

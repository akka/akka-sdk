/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.enforcer;

import static akka.javasdk.enforcer.VersionComparator.*;
import static akka.javasdk.enforcer.VersionComparator.Direction.*;
import static akka.javasdk.enforcer.VersionComparator.Strictness.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VersionComparatorTest {

  @Nested
  class NewerOnlyStrictness {
    @Test
    void sameVersion_noConflict() {
      assertFalse(check("33.5.0-jre", "33.5.0-jre", NEWER_ONLY).isConflict);
    }

    @Test
    void appOlderMajor_noConflict() {
      // Runtime has newer, backward compatible
      assertFalse(check("31.1.0-jre", "33.5.0-jre", NEWER_ONLY).isConflict);
    }

    @Test
    void appOlderPatch_noConflict() {
      assertFalse(check("2.18.1", "2.18.3", NEWER_ONLY).isConflict);
    }

    @Test
    void appNewerMajor_conflict() {
      ConflictResult r = check("35.0.0-jre", "33.5.0-jre", NEWER_ONLY);
      assertTrue(r.isConflict);
      assertEquals(APP_NEWER, r.direction);
    }

    @Test
    void appNewerPatch_conflict() {
      ConflictResult r = check("2.18.5", "2.18.3", NEWER_ONLY);
      assertTrue(r.isConflict);
      assertEquals(APP_NEWER, r.direction);
    }

    @Test
    void appNewerMinor_conflict() {
      ConflictResult r = check("2.19.0", "2.18.3", NEWER_ONLY);
      assertTrue(r.isConflict);
      assertEquals(APP_NEWER, r.direction);
    }
  }

  @Nested
  class MajorStrictness {
    @Test
    void sameMajorDifferentMinor_noConflict() {
      assertFalse(check("2.19.0", "2.18.3", MAJOR).isConflict);
    }

    @Test
    void differentMajor_conflict() {
      assertTrue(check("3.0.0", "2.18.3", MAJOR).isConflict);
    }

    @Test
    void differentMajorAppOlder_conflict() {
      assertTrue(check("1.5.0", "2.18.3", MAJOR).isConflict);
    }

    @Test
    void sameMajorDifferentQualifier_conflict() {
      // 1.0-M1 vs 1.0 — same major but qualifier differs
      assertTrue(check("1.0-M1", "1.0", MAJOR).isConflict);
    }

    @Test
    void sameMajorSnapshotVsRelease_conflict() {
      assertTrue(check("2.0-SNAPSHOT", "2.0", MAJOR).isConflict);
    }

    @Test
    void sameMajorDifferentMilestones_conflict() {
      // M1 vs M10 — same major but different milestones
      assertTrue(check("1.0-M1", "1.0-M10", MAJOR).isConflict);
    }

    @Test
    void sameMajorSameQualifier_noConflict() {
      assertFalse(check("2.19.0-jre", "2.18.3-jre", MAJOR).isConflict);
    }
  }

  @Nested
  class MinorStrictness {
    @Test
    void sameMinorDifferentPatch_noConflict() {
      assertFalse(check("2.18.1", "2.18.3", MINOR).isConflict);
    }

    @Test
    void differentMinor_conflict() {
      assertTrue(check("2.19.0", "2.18.3", MINOR).isConflict);
    }

    @Test
    void sameMinorDifferentQualifier_conflict() {
      // 2.18.3-M1 vs 2.18.3 — same minor but qualifier differs
      assertTrue(check("2.18.3-M1", "2.18.3", MINOR).isConflict);
    }

    @Test
    void sameMinorSnapshotVsRelease_conflict() {
      assertTrue(check("2.18.3-SNAPSHOT", "2.18.3", MINOR).isConflict);
    }

    @Test
    void sameMinorSameQualifier_noConflict() {
      assertFalse(check("2.18.1-jre", "2.18.3-jre", MINOR).isConflict);
    }
  }

  @Nested
  class PatchStrictness {
    @Test
    void sameVersion_noConflict() {
      assertFalse(check("2.18.3", "2.18.3", PATCH).isConflict);
    }

    @Test
    void differentPatch_conflict() {
      assertTrue(check("2.18.1", "2.18.3", PATCH).isConflict);
    }

    @Test
    void samePatchDifferentQualifier_noConflict() {
      // Same numeric base, only qualifier differs — allowed in patch mode
      assertFalse(check("2.18.3-jre", "2.18.3-android", PATCH).isConflict);
    }

    @Test
    void samePatchSnapshotVsRelease_noConflict() {
      // Same numeric base, SNAPSHOT vs release — allowed in patch mode
      assertFalse(check("2.18.3-SNAPSHOT", "2.18.3", PATCH).isConflict);
    }

    @Test
    void differentMinor_conflict() {
      assertTrue(check("2.19.0", "2.18.3", PATCH).isConflict);
    }
  }

  @Nested
  class ExactStrictness {
    @Test
    void sameVersion_noConflict() {
      assertFalse(check("2.18.3", "2.18.3", EXACT).isConflict);
    }

    @Test
    void differentPatch_conflict() {
      assertTrue(check("2.18.1", "2.18.3", EXACT).isConflict);
    }

    @Test
    void sameBaseDifferentQualifier_conflict() {
      // Exact mode flags qualifier differences too
      assertTrue(check("2.18.3-jre", "2.18.3-android", EXACT).isConflict);
    }

    @Test
    void snapshotVsRelease_conflict() {
      assertTrue(check("2.18.3-SNAPSHOT", "2.18.3", EXACT).isConflict);
    }
  }

  @Nested
  class MavenVersionSemantics {
    @Test
    void normalizedEquality() {
      // Maven considers "1.0" and "1.0.0" equal
      assertFalse(check("1.0", "1.0.0", EXACT).isConflict);
    }

    @Test
    void snapshotOlderThanRelease() {
      // Maven: 2.18.3-SNAPSHOT < 2.18.3
      ConflictResult r = check("2.18.3", "2.18.3-SNAPSHOT", NEWER_ONLY);
      assertTrue(r.isConflict);
      assertEquals(APP_NEWER, r.direction);
    }

    @Test
    void qualifierOrdering() {
      // Maven: alpha < beta < rc < release
      ConflictResult r = check("1.0.0-beta1", "1.0.0-alpha1", NEWER_ONLY);
      assertTrue(r.isConflict);
      assertEquals(APP_NEWER, r.direction);
    }
  }
}

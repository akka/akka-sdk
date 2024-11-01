import sbt._
import sbt.Keys._

/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */

object DocSettings {

  def forModule(docTitle: String) = Seq(
    // Generate javadocs by just including non generated Java sources
    Compile / doc / sources := {
      val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
      (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
    },
    // javadoc (I think java 9 onwards) refuses to compile javadocs if it can't compile the entire source path.
    // but since we have java files depending on Scala files, we need to include ourselves on the classpath.
    Compile / doc / dependencyClasspath := (Compile / fullClasspath).value,
    Compile / doc / javacOptions ++= Seq(
      "-Xdoclint:none",
      "-overview",
      ((Compile / javaSource).value / "overview.html").getAbsolutePath,
      "-notimestamp",
      "-doctitle",
      docTitle,
      "-noqualifier",
      "java.lang"))

}

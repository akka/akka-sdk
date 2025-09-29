/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ClassicActorSystemProvider
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory

object ApplicationConfig extends ExtensionId[ApplicationConfig] {

  override def createExtension(system: ExtendedActorSystem): ApplicationConfig =
    new ApplicationConfig

  override def get(system: ClassicActorSystemProvider): ApplicationConfig = super.get(system)

  def loadApplicationConf: Config = {
    val testConf = "application-test.conf"
    if (getClass.getResource(s"/$testConf") eq null) {
      val confResource = Option(
        System.getProperty("application-config.resource", System.getenv("APPLICATION_CONFIG_RESOURCE")))
      val confFile = Option(System.getProperty("application-config.file", System.getenv("APPLICATION_CONFIG_FILE")))
      (confResource, confFile) match {
        case (None, None)    => ConfigFactory.load(ConfigFactory.parseResources("application.conf"))
        case (Some(r), None) => ConfigFactory.load(ConfigFactory.parseResources(r))
        case (None, Some(f)) => ConfigFactory.load(ConfigFactory.parseFile(new File(f)))
        case (Some(r), Some(f)) =>
          throw new ConfigException.Generic(
            s"You set more than one of application-config.file='$f', application-config.resource='$r'; don't know which one to use!")
      }
    } else
      ConfigFactory.load(ConfigFactory.parseResources(testConf))
  }
}

class ApplicationConfig extends Extension {
  private val config = new AtomicReference[Config]

  def getConfig: Config = config.get match {
    case null =>
      val c = ApplicationConfig.loadApplicationConf
      if (config.compareAndSet(null, c))
        c
      else
        config.get

    case c => c
  }

  def overrideConfig(c: Config): Unit =
    config.set(c)
}

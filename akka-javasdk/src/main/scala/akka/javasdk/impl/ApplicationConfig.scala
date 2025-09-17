/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.ClassicActorSystemProvider
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import java.util.concurrent.atomic.AtomicReference

object ApplicationConfig extends ExtensionId[ApplicationConfig] {

  override def createExtension(system: ExtendedActorSystem): ApplicationConfig =
    new ApplicationConfig

  override def get(system: ClassicActorSystemProvider): ApplicationConfig = super.get(system)

  def loadApplicationConf: Config = {
    val testConf = "application-test.conf"
    if (getClass.getResource(s"/$testConf") eq null) {

      val portKey = "akka.javasdk.dev-mode.http-port"
      val appConfigAlone = ConfigFactory.parseResources("application.conf")
      val appConfig = ConfigFactory.load(appConfigAlone)

      if (appConfigAlone.hasPath(portKey)) {
        // if application.conf is defining the port, we stick with it
        appConfig
      } else {
        // if not, try to load config.resources
        sys.props.get("config.resource") match {
          case Some(extraFile) =>
            val extraConfig = ConfigFactory.parseResources(extraFile)
            // the config resource goes before app config
            ConfigFactory.load(extraConfig.withFallback(appConfigAlone))
          case None =>
            appConfig
        }
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

/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.javasdk.agent.ModelProvider
import com.typesafe.config.Config

class MyModelProvider(config: Config) extends ModelProvider.Custom {

  override def modelName(): String = {
    config.getString("model-name")
  }

  override def createChatModel(): AnyRef = ???

  override def createStreamingChatModel(): AnyRef = ???
}

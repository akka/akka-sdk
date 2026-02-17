/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.javasdk.agent.ModelProvider
import com.typesafe.config.Config

class NoConfigMyModelProvider extends ModelProvider.Custom {

  override def modelName(): String = {
    "no-config-model-name"
  }

  override def createChatModel(): AnyRef = ???

  override def createStreamingChatModel(): AnyRef = ???
}

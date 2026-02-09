package com.example.application;

import akka.javasdk.agent.ModelProvider;
import com.typesafe.config.Config;

// tag::custom[]
public class MyModelProvider implements ModelProvider.Custom {

  private final String modelName;

  public MyModelProvider(Config config) { // <1>
    modelName = config.getString("model-name");
    //TODO model provider initialization based on injected config
  }

  @Override
  public String modelName() {
    return modelName;
  }

  @Override
  public Object createChatModel() { // <2>
    //TODO return ChatModel implementation
    return null;
  }

  @Override
  public Object createStreamingChatModel() { //<3>
    //TODO return StreamingChatModel implementation
    return null;
  }
}
// end::custom[]

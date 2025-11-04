package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryFilter;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;

public interface MyAgentMore {
  @Component(id = "my-agent")
  public class MyAgentWithModel extends Agent {

    // tag::model[]
    public Effect<String> query(String question) {
      return effects()
        .model(
          ModelProvider.openAi() // <1>
            .withApiKey(System.getenv("OPENAI_API_KEY"))
            .withModelName("gpt-4o")
            .withTemperature(0.6)
            .withMaxTokens(10000)
        )
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
    }
    // end::model[]
  }

  @Component(id = "my-agent-memory")
  public class MyAgentNoMemory extends Agent {

    // tag::no-memory[]
    public Effect<String> ask(String question) {
      return effects()
        .memory(MemoryProvider.none())
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
    }
    // end::no-memory[]
  }

  @Component(id = "my-agent-readlast")
  public class MyAgentReadLastMemory extends Agent {

    // tag::read-last[]
    public Effect<String> ask(String question) {
      return effects()
        .memory(MemoryProvider.limitedWindow().readLast(5))
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
    }
    // end::read-last[]
  }

  @Component(id = "my-agent-filter")
  public class MyAgentWithFilter extends Agent {

    // tag::filter-include[]
    public Effect<String> ask(String question) {
      return effects()
        .memory(
          MemoryProvider.limitedWindow()
            .filtered(MemoryFilter.includeFromAgentId("summarizer-agent")) // <1>
        )
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
    }
    // end::filter-include[]
  }

  @Component(id = "my-agent-filter-exclude")
  public class MyAgentWithFilterExclude extends Agent {

    // tag::filter-exclude[]
    public Effect<String> ask(String question) {
      return effects()
        .memory(
          MemoryProvider.limitedWindow()
            .filtered(MemoryFilter.excludeFromAgentRole("internal")) // <1>
        )
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
    }
    // end::filter-exclude[]
  }

  @Component(id = "my-agent-filter-readlast")
  public class MyAgentWithFilterAndReadLast extends Agent {

    // tag::filter-readlast[]
    public Effect<String> ask(String question) {
      return effects()
        .memory(
          MemoryProvider.limitedWindow()
            .readLast(10, MemoryFilter.excludeFromAgentId("debug-agent")) // <1>
        )
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
    }
    // end::filter-readlast[]
  }

  @Component(id = "my-agent-filter-multiple")
  public class MyAgentWithMultipleFilters extends Agent {

    // tag::filter-multiple[]
    public Effect<String> ask(String question) {
      return effects()
        .memory(
          MemoryProvider.limitedWindow()
            .filtered(
              MemoryFilter.includeFromAgentId("agent-1")
                .includeFromAgentId("agent-2")
                .excludeFromAgentRole("internal") // <1>
            )
        )
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .thenReply();
    }
    // end::filter-multiple[]
  }
}

package demo.multiagent;


import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpClientProvider;
import demo.multiagent.application.SessionMemory;
import demo.multiagent.application.agents.ActivityAgent;
import demo.multiagent.application.agents.AgentsRegistry;
import demo.multiagent.application.agents.Planner;
import demo.multiagent.application.agents.Selector;
import demo.multiagent.application.agents.Summarizer;
import demo.multiagent.application.agents.WeatherAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Setup
public class Bootstrap implements ServiceSetup {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ComponentClient componentClient;
  private final HttpClientProvider httpClientProvider;
  private final String OPENAI_API_KEY = "OPENAI_API_KEY";

  public Bootstrap(ComponentClient componentClient, HttpClientProvider httpClientProvider) {
    this.httpClientProvider = httpClientProvider;

    if (System.getenv(OPENAI_API_KEY) == null || System.getenv(OPENAI_API_KEY).isEmpty()) {
      logger.error(
        "No API keys found. Make sure you have OPENAI_API_KEY defined as environment variable.");
      throw new RuntimeException("No API keys found.");
    }

      this.componentClient = componentClient;
  }
  final private static OpenAiChatModelName chatModelName = OpenAiChatModelName.GPT_4_O_MINI;

  @Override
  @SuppressWarnings("unchecked")
  public DependencyProvider createDependencyProvider() {

    ChatLanguageModel chatModel = OpenAiChatModel.builder()
      .apiKey(System.getenv(OPENAI_API_KEY))
      .modelName(chatModelName)
      .build();

    var sessionMemory = new SessionMemory(componentClient);
    var agentsRegister =
      new AgentsRegistry()
        .register(new WeatherAgent(sessionMemory, chatModel, httpClientProvider))
        .register(new ActivityAgent(sessionMemory, chatModel));

    return new DependencyProvider() {
      @Override
      public <T> T getDependency(Class<T> cls) {
        if (cls.equals(AgentsRegistry.class)) {
          return (T) agentsRegister;
        }

        if (cls.equals(Selector.class)) {
          return (T) new Selector(agentsRegister, chatModel);
        }

        if (cls.equals(Planner.class)) {
          return (T) new Planner(agentsRegister, chatModel);
        }

        if (cls.equals(Summarizer.class)) {
          return (T) new Summarizer(chatModel);
        }

        return null;
      }
    };
  }

}


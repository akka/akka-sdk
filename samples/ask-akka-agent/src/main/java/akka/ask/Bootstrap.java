package akka.ask;

import akka.ask.agent.application.AskAkkaAgent;
import akka.ask.common.KeyUtils;
import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import akka.stream.Materializer;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Setup
public class Bootstrap implements ServiceSetup {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final MongoClient mongoClient;
  private final ComponentClient componentClient;

  public Bootstrap(
    ComponentClient componentClient,
    Materializer materializer) {

    if (!KeyUtils.hasValidKeys()) {
      throw new IllegalStateException(
        "No API keys found. When running locally, make sure you have a " + ".env.local file located under " +
          "src/main/resources/ (see src/main/resources/.env.example). When running in production, " +
          "make sure you have OPENAI_API_KEY and MONGODB_ATLAS_URI defined as environment variable.");
    }

    this.componentClient = componentClient;
    this.mongoClient = MongoClients.create(KeyUtils.readMongoDbUri());
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {
      @Override
      public <T> T getDependency(Class<T> cls) {
        if (cls.equals(AskAkkaAgent.class)) {
          return (T) new AskAkkaAgent(componentClient, mongoClient);
        }

        if (cls.equals(MongoClient.class)) {
          return (T) mongoClient;
        }
        return null;
      }
    };
  }
}

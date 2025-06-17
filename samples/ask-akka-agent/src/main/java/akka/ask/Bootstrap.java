package akka.ask;

import akka.ask.agent.application.Knowledge;
import akka.ask.common.KeyUtils;
import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

// tag::mongodb[]
// tag::knowledge[]
@Setup
public class Bootstrap implements ServiceSetup {
  // end::knowledge[]
  public Bootstrap() {
    if (!KeyUtils.hasValidKeys()) {
      throw new IllegalStateException(
          "No API keys found. Make sure you have OPENAI_API_KEY and MONGODB_ATLAS_URI defined as environment variable.");
    }
  }
  // tag::knowledge[]

  @Override
  public DependencyProvider createDependencyProvider() {
    MongoClient mongoClient = MongoClients.create(KeyUtils.readMongoDbUri());

    // end::mongodb[]
    Knowledge knowledge = new Knowledge(mongoClient);
    // tag::mongodb[]

    return new DependencyProvider() {
      @Override
      public <T> T getDependency(Class<T> cls) {
        if (cls.equals(MongoClient.class)) {
          return (T) mongoClient;
        }

        // end::mongodb[]
        if (cls.equals(Knowledge.class)) {
          return (T) knowledge;
        }
        // tag::mongodb[]

        return null;
      }
    };
  }
}
// end::knowledge[]
// end::mongodb[]

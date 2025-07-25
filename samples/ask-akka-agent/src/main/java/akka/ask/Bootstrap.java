package akka.ask;

import akka.ask.agent.application.Knowledge;
import akka.ask.common.KeyUtils;
import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.typesafe.config.Config;

// tag::mongodb[]
// tag::knowledge[]
@Setup
public class Bootstrap implements ServiceSetup {

  private Config config;

  // end::knowledge[]

  public Bootstrap(Config config) {
    this.config = config;
    // end::mongodb[]
    KeyUtils.checkKeys(config);
    // tag::mongodb[]
  }

  // tag::knowledge[]

  @Override
  public DependencyProvider createDependencyProvider() {
    MongoClient mongoClient = MongoClients.create(config.getString("mongodb.uri"));
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

package demo.editorial.application;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;

@Setup
public class Bootstrap implements ServiceSetup {

  private final ComponentClient componentClient;

  public Bootstrap(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    var documentTools = new DocumentTools(componentClient);
    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == DocumentTools.class) {
          return (T) documentTools;
        }
        return null;
      }
    };
  }
}

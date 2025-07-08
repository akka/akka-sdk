package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.example.application.ActivityAgentMore;
import com.example.application.MyAgentMore;
import com.example.application.MyComponent;
import com.typesafe.config.Config;
import java.util.Set;

// tag::pojo-dependency-injection[]
// tag::disable-components[]
@Setup
public class MyAppSetup implements ServiceSetup {

  private final Config appConfig;

  public MyAppSetup(Config appConfig) {
    this.appConfig = appConfig;
  }

  // end::disable-components[]

  @Override
  public DependencyProvider createDependencyProvider() { // <1>
    final var myAppSettings = new MyAppSettings(
      appConfig.getBoolean("my-app.some-feature-flag")
    ); // <2>

    return new DependencyProvider() { // <3>
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == MyAppSettings.class) {
          return (T) myAppSettings;
        } else {
          throw new RuntimeException("No such dependency found: " + clazz);
        }
      }
    };
  }

  // end::pojo-dependency-injection[]
  // tag::disable-components[]
  @Override
  public Set<Class<?>> disabledComponents() { // <1>
    if (appConfig.getString("my-app.environment").equals("prod")) {
      return Set.of(MyComponent.class); // <2>
      // end::disable-components[]
    } else if (true) {
      // to be able to run, otherwise duplicate components
      return Set.of(
        ActivityAgentMore.ActivityAgent.class,
        ActivityAgentMore.StreamingActivityAgent.class,
        ActivityAgentMore.ActivityAgentStructuredResponse.class,
        ActivityAgentMore.ActivityAgentWithTemplate.class,
        ActivityAgentMore.ActivityHttpEndpoint.class,
        ActivityAgentMore.UserProfileEntity.class,
        MyAgentMore.MyAgentNoMemory.class,
        MyAgentMore.MyAgentReadLastMemory.class,
        MyAgentMore.MyAgentWithModel.class
      );
      // tag::disable-components[]
    } else {
      return Set.of(); // <2>
    }
  }
  // tag::pojo-dependency-injection[]
}
// end::disable-components[]
// end::pojo-dependency-injection[]

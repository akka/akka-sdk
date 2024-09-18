package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.typesafe.config.Config;

// tag::pojo-dependency-injection[]
@Setup
public class MyAppSetup implements ServiceSetup {

  private final Config appConfig;

  public MyAppSetup(Config appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  public DependencyProvider createDependencyProvider() { // <1>
    final var myAppSettings =
        new MyAppSettings(appConfig.getBoolean("my-app.some-feature-flag")); // <2>

    return new DependencyProvider() { // <3>
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == MyAppSettings.class) {
          return (T) myAppSettings;
        } else {
          throw new RuntimeException("No such dependency found: "+ clazz);
        }
      }
    };
  }

  // end::pojo-dependency-injection[]
}

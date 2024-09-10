package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import com.example.domain.Counter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;

@Setup
public class CounterSetup implements ServiceSetup {

  private ComponentClient componentClient;

  public CounterSetup(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public void onStartup() {
    componentClient.forEventSourcedEntity("123").method(Counter::increase).invokeAsync(10);
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return springDependencyProvider();
  }

  private DependencyProvider springDependencyProvider() {
    try {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      ResourcePropertySource resourcePropertySource = new ResourcePropertySource(new ClassPathResource("application.properties"));
      context.getEnvironment().getPropertySources().addFirst(resourcePropertySource);
      context.registerBean(ComponentClient.class, () -> componentClient);
      context.scan("com.example");
      context.refresh();
      return new DependencyProvider() {
        @Override
        public <T> T getDependency(Class<T> clazz) {
          return context.getBean(clazz);
        }
      };
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


}
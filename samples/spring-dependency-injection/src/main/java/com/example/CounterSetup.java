package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import com.example.domain.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;

// tag::lifecycle[]
@Setup // <1>
// tag::spring[]
public class CounterSetup implements ServiceSetup {

  // end::spring[]
  private  final Logger logger = LoggerFactory.getLogger(getClass());
  private final ComponentClient componentClient;

  public CounterSetup(ComponentClient componentClient) { // <2>
    this.componentClient = componentClient;
  }

  @Override
  public void onStartup() { // <3>
    logger.info("Service starting up");
    componentClient.forEventSourcedEntity("123")
        .method(Counter::get)
        .invokeAsync().thenAccept(result ->
          logger.info("Initial value for entity 123 is [{}]", result)
        );
  }
  // end::lifecycle[]

  // tag::spring[]
  @Override
  public DependencyProvider createDependencyProvider() {
    try {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(); // <1>
      ResourcePropertySource resourcePropertySource = new ResourcePropertySource(new ClassPathResource("application.properties"));
      context.getEnvironment().getPropertySources().addFirst(resourcePropertySource);
      context.registerBean(ComponentClient.class, () -> componentClient);
      context.scan("com.example");
      context.refresh();
      return context::getBean; // <2>
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


}
// end::spring[]
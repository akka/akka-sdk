package com.example;

import akka.platform.javasdk.DependencyProvider;
import akka.platform.javasdk.ServiceSetup;
import akka.platform.javasdk.ServiceLifecycle;
import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.PlatformServiceSetup;
import akka.platform.javasdk.client.ComponentClient;
import com.example.domain.Counter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;

@PlatformServiceSetup
// NOTE: This default ACL settings is very permissive as it allows any traffic from the internet.
// Our samples default to this permissive configuration to allow users to easily try it out.
// However, this configuration is not intended to be reproduced in production environments.
// Documentation at https://docs.kalix.io/java/access-control.html
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class CounterSetup implements ServiceSetup {

  private ComponentClient componentClient;

  public CounterSetup(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public ServiceLifecycle serviceLifecycle() {
    return new ServiceLifecycle() {
      @Override
      public void onStartup() {
        componentClient.forEventSourcedEntity("123").method(Counter::increase).invokeAsync(10);
      }
    };
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
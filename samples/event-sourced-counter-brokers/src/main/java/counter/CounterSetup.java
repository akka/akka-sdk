package counter;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import counter.application.CounterStore;

@Setup
public class CounterSetup implements ServiceSetup {

  @Override
  public DependencyProvider createDependencyProvider() {
    return DependencyProvider.single(new CounterStore());
  }
}

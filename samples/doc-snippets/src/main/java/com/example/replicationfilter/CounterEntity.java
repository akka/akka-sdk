package com.example.replicationfilter;

// tag::replication-filter[]
import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.EnableReplicationFilter;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.ReplicationFilter;

@Component(id = "counter")
@EnableReplicationFilter // <1>
public class CounterEntity extends KeyValueEntity<Counter> {

  // end::replication-filter[]

  @Override
  public Counter emptyState() {
    return new Counter(0);
  }


  // tag::replication-filter-update-state[]
  public Effect<Counter> increaseBy(int increaseBy) {
    var selfRegion = commandContext().selfRegion();
    Counter newCounter = currentState().increment(increaseBy);
    return effects()
      .updateState(newCounter)
      .updateReplicationFilter(ReplicationFilter.includeRegion(selfRegion))
      .thenReply(newCounter);
  }

  // end::replication-filter-update-state[]
  // tag::replication-filter[]

  public Effect<Done> replicateTo(String region) {
    return effects()
        .updateReplicationFilter(ReplicationFilter.includeRegion(region)) // <2>
        .thenReply(Done.getInstance());
  }

}
// end::replication-filter[]

record Counter(int value) {
  public Counter increment(int delta) {
    return new Counter(value + delta);
  }
}

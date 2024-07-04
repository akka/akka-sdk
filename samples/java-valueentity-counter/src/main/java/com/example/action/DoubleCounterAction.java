package com.example.action;

import com.example.CounterEntity;
import com.example.Number;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.client.ComponentClient;


@Consume.FromValueEntity(CounterEntity.class)
public class DoubleCounterAction extends Action {

  final private ComponentClient componentClient;

  public DoubleCounterAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // FIXME no longer shows side effect, that was dropped
  // tag::controller-side-effect[]
  public Action.Effect<Confirmed> increaseWithSideEffect(Integer increase) {
    var counterId = actionContext().eventSubject().get(); // <1>
    var doubleIncrease = increase * 2; // <2>
    var increaseResult = componentClient.forValueEntity(counterId)
      .method(CounterEntity::increaseBy)
      .invokeAsync(new Number(doubleIncrease));
    var reply = increaseResult.thenApply(__ -> Confirmed.instance);
    return effects().asyncReply(reply);  // <3>
  }
  // end::controller-side-effect[]
}

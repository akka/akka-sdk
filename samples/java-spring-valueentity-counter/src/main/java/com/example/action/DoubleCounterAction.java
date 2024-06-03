package com.example.action;

import com.example.CounterEntity;
import com.example.Number;
import kalix.javasdk.SideEffect;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;


@Subscribe.ValueEntity(CounterEntity.class)
public class DoubleCounterAction extends Action {

  final private ComponentClient componentClient;

  public DoubleCounterAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::controller-side-effect[]
  public Action.Effect<Confirmed> increaseWithSideEffect(Integer increase) {
    var counterId = actionContext().eventSubject().get(); // <1>
    var doubleIncrease = increase * 2; // <2>
    var deferredCall = componentClient.forValueEntity(counterId)
      .methodRef(CounterEntity::increaseBy)
      .deferred(new Number(doubleIncrease));
    return effects().reply(Confirmed.instance).addSideEffect(SideEffect.of(deferredCall));  // <3>
  }
  // end::controller-side-effect[]
}

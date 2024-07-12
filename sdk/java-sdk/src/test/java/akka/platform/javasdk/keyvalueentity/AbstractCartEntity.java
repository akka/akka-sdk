/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.keyvalueentity;

import com.example.valueentity.shoppingcart.ShoppingCartApi;
import com.example.valueentity.shoppingcart.domain.ShoppingCartDomain;
import com.google.protobuf.Empty;

public abstract class AbstractCartEntity extends KeyValueEntity<ShoppingCartDomain.Cart> {

  public abstract Effect<Empty> addItem(
      ShoppingCartDomain.Cart currentState, ShoppingCartApi.AddLineItem addLineItem);

  public abstract Effect<Empty> removeItem(
      ShoppingCartDomain.Cart currentState, ShoppingCartApi.RemoveLineItem removeLineItem);

  public abstract Effect<ShoppingCartApi.Cart> getCart(
      ShoppingCartDomain.Cart currentState, ShoppingCartApi.GetShoppingCart getShoppingCart);

  public abstract Effect<Empty> removeCart(
      ShoppingCartDomain.Cart currentState, ShoppingCartApi.RemoveShoppingCart removeShoppingCart);
}

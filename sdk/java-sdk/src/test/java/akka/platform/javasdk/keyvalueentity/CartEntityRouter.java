/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.keyvalueentity;

import akka.platform.javasdk.impl.keyvalueentity.KeyValueEntityRouter;
import com.example.valueentity.shoppingcart.ShoppingCartApi;
import com.example.valueentity.shoppingcart.domain.ShoppingCartDomain;

/** A key value entity handler */
public class CartEntityRouter extends KeyValueEntityRouter<ShoppingCartDomain.Cart, CartEntity> {

  public CartEntityRouter(CartEntity entity) {
    super(entity);
  }

  @Override
  public KeyValueEntity.Effect<?> handleCommand(
      String commandName, ShoppingCartDomain.Cart state, Object command, CommandContext context) {
    switch (commandName) {
      case "AddItem":
        return entity().addItem(state, (ShoppingCartApi.AddLineItem) command);
      case "RemoveItem":
        return entity().removeItem(state, (ShoppingCartApi.RemoveLineItem) command);
      case "GetCart":
        return entity().getCart(state, (ShoppingCartApi.GetShoppingCart) command);
      case "RemoveCart":
        return entity().removeCart(state, (ShoppingCartApi.RemoveShoppingCart) command);
      default:
        throw new KeyValueEntityRouter.CommandHandlerNotFound(commandName);
    }
  }
}

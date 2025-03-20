package shoppingcart.application;

import java.util.ArrayList;
import java.util.List;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import shoppingcart.domain.ShoppingCartEvent;

@ComponentId("shopping_cart_view")
public class ShoppingCartView extends View {

  @Query("SELECT * FROM shopping_cart_view WHERE cartId = :cartId") // <1>
  public QueryEffect<Cart> getCart(String cartId) {
    return queryResult();
  }

  @Query("SELECT * FROM shopping_cart_view WHERE userId = :userId") // <2>
  public QueryEffect<Cart> getUserCart(String userId) {
    return queryResult();
  }

  public record Cart(String cartId,
      String userId,
      List<Item> items,
      boolean checkedout) { // <3>

    public Cart addItem(String itemId, String name,
        int quantity, String description) {
      var newItems = items;
      newItems.add(new Item(itemId, name, quantity, description));

      return new Cart(cartId, userId, newItems, false);
    }

    public Cart withCartId(String nCartId) {
      return new Cart(nCartId, userId, items, checkedout);
    }

    public Cart withUserId(String nUserId) {
      return new Cart(cartId, nUserId, items, checkedout);
    }

    public Cart removeItem(String itemId) {
      var newItems = items;
      newItems.removeIf(i -> i.itemId() == itemId);

      return new Cart(cartId, userId, newItems, false);
    }

    public Cart checkout() {
      return new Cart(cartId, userId, items, true);
    }

    public record Item(String itemId, String name,
        int quantity, String description) {
    }

  }

  @Consume.FromEventSourcedEntity(ShoppingCartEntity.class) // <4>
  public static class CartsTable extends TableUpdater<Cart> {

    public Cart rowStateOrNew(String userId) {
      if (rowState() == null) {
        return new Cart(
            updateContext().eventSubject().get(),
            userId,
            new ArrayList<Cart.Item>(),
            false);
      } else {
        return rowState();
      }
    }

    public Effect<Cart> onEvent(ShoppingCartEvent event) {
      return switch (event) {
        case ShoppingCartEvent.ItemAdded added -> addItem(added);
        case ShoppingCartEvent.ItemRemoved removed -> removeItem(removed);
        case ShoppingCartEvent.CheckedOut checkedOut -> checkout(checkedOut);
      };
    }

    private Effect<Cart> addItem(ShoppingCartEvent.ItemAdded added) {
      return effects().updateRow(
          rowStateOrNew(added.userId()) // <5>
              .addItem(added.productId(),
                  added.name(), added.quantity(), added.description()));
    }

    private Effect<Cart> removeItem(ShoppingCartEvent.ItemRemoved removed) {
      return effects().updateRow(rowState().removeItem(removed.productId()));
    }

    private Effect<Cart> checkout(ShoppingCartEvent.CheckedOut checkedOut) {
      return effects().updateRow(rowState().checkout());
    }

  }
}

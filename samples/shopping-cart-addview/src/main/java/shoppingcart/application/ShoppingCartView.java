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
  @Query("SELECT * FROM shopping_cart_view WHERE cartId = :cartId")
  public QueryEffect<Cart> getCart(String cartId) {
    return queryResult();
  }

  public record Carts(List<Cart> carts) {
  }

  public record Cart(String cartId, List<Item> items, boolean checkedout) {

    public Cart addItem(String itemId, String name, int quantity, String description) {
      var newItems = items;
      newItems.add(new Item(itemId, name, quantity, description));

      return new Cart(cartId, newItems, false);
    }

    public Cart withCartId(String nCartId) {
      return new Cart(nCartId, items, checkedout);
    }

    public Cart removeItem(String itemId) {
      var newItems = items;
      newItems.removeIf(i -> i.itemId() == itemId);

      return new Cart(cartId, newItems, false);
    }

    public Cart checkout() {
      return new Cart(cartId, items, true);
    }

    public record Item(String itemId, String name, int quantity, String description) {
    }

  }

  @Consume.FromEventSourcedEntity(ShoppingCartEntity.class)
  public static class CartsTable extends TableUpdater<Cart> {

    @Override
    public Cart emptyRow() {
      return new Cart(updateContext().eventSubject().get(), new ArrayList<Cart.Item>(), false);
    }

    public Effect<Cart> onEvent(ShoppingCartEvent event) {
      return switch (event) {
        case ShoppingCartEvent.ItemAdded added -> addItem(added);
        case ShoppingCartEvent.ItemRemoved removed -> removeItem(removed);
        case ShoppingCartEvent.CheckedOut checkedOut -> checkout(checkedOut);
      };
    }

    private Effect<Cart> addItem(ShoppingCartEvent.ItemAdded added) {
      return effects().updateRow(rowState().addItem(added.productId(),
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

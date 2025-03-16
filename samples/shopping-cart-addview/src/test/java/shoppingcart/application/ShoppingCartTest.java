package shoppingcart.application;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import shoppingcart.api.ShoppingCartEndpoint;
import shoppingcart.domain.ShoppingCartState;

import java.util.List;

import static shoppingcart.domain.ShoppingCartEvent.ItemAdded;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShoppingCartTest {

  private final ShoppingCartEndpoint.LineItemRequest akkaTshirt = new ShoppingCartEndpoint.LineItemRequest(
      "akka-tshirt",
      "Akka Tshirt", 10,
      "The best t-shirt");

  private final ShoppingCartEndpoint.LineItemRequest akkaTshirt5 = new ShoppingCartEndpoint.LineItemRequest(
      "akka-tshirt",
      "Akka Tshirt", 5,
      "The best t-shirt");

  @Test
  public void testAddLineItem() {

    var testKit = EventSourcedTestKit.of(ShoppingCartEntity::new);

    {
      var result = testKit.method(ShoppingCartEntity::addItem).invoke(akkaTshirt);
      assertEquals(Done.getInstance(), result.getReply());

      var itemAdded = result.getNextEventOfType(ItemAdded.class);
      assertEquals(10, itemAdded.quantity());
    }

    // actually we want more akka tshirts
    {
      var result = testKit.method(ShoppingCartEntity::addItem).invoke(akkaTshirt5);
      assertEquals(Done.getInstance(), result.getReply());

      var itemAdded = result.getNextEventOfType(ItemAdded.class);
      assertEquals(5, itemAdded.quantity());
    }

    {
      var tshirt15 = new ShoppingCartState.LineItem("akka-tshirt", 15);

      assertEquals(testKit.getAllEvents().size(), 2);
      var result = testKit.method(ShoppingCartEntity::getCart).invoke();
      assertEquals(
          List.of(tshirt15),
          result.getReply().items());
    }

  }

}

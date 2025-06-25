<!-- <nav> -->
- [Akka](../index.html)
- [Shopping cart part 2: Authenticated user-specific lookup](addview.html)

<!-- </nav> -->

# Shopping cart part 2: Authenticated user-specific lookup

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |
This guide walks you through the design and implementation of an enhancement to the shopping cart service example, illustrating model refactoring, the use of Views, and user authentication.

## <a href="about:blank#_overview"></a> Overview

In this step in the shopping cart sample tour, we’ll be taking a look at the event and domain models created in the previous step. We’ll decide on what we’d like to change and then implement that change in the form of a few refactors and adding a new `View`.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- An [Akka account](https://console.akka.io/register)
- [Docker Engine](https://docs.docker.com/get-started/get-docker/) 27 or later

## <a href="about:blank#_clone_the_sample"></a> Clone the sample

1. Clone the full source code of the Shopping Cart (with View) sample from [Github](https://github.com/akka-samples/shopping-cart-with-view).

## <a href="about:blank#_re_evaluating_the_shopping_cart_structure"></a> Re-evaluating the shopping cart structure

The first version of the shopping cart had a bit of an issue blurring the lines between tiers or responsibilities. The data type used to represent the `LineItem` in the `POST` request to the HTTP endpoint is the same data type sent to the `ShoppingCartEntity` as a command. This is *also* the exact same data type used by the entity for its own internal state storage.

For small applications or prototypes, this isn’t that big of a problem. But this kind of pattern might create headaches in the future. Any change to the way the entity internally stores its internal state will potentially break the API endpoint and maybe even cause migration issues with events.

It might seem like overkill at first, but separating these data types is one of the first steps toward supporting evolutionary architecture and clean, easily-maintained code. If we adopt the rule that we can’t reuse the same data type across roles, then we end up with 3 distinct types:

- The data used to shape the body of the `POST` request to add an item to a cart
- The data used as a *command* to be sent to the entity to handle that request
- The data used *internally* by the entity to represent its own state.
We want to make sure that the data the entity is storing for its state contains *only* the information the entity needs in order to validate incoming commands.

The other change we want to make is adding a `userId` attribute to the shopping cart. While the first version using just `cartId` is fine, on the road to production this isn’t good enough. We need to be able to ensure that one user can’t read, modify, or delete a cart owned by another user. Further, we want the option to retrieve a cart by user ID from the new view we’re adding.

### <a href="about:blank#_managing_cart_and_user_ids"></a> Managing cart and user IDs

With the addition of a `userId` to the shopping cart, we’ve now got a bit of a gray area in the model. How do we create new cart IDs? The last version of the sample relied on the clients to generate and remember the cart IDs, which isn’t an ideal experience. Further, how do we ensure that users only have 1 active cart while we can potentially retrieve old carts for historical data?

The solution used in this sample is to create another entity, the `UserEntity`. This entity will manage just one piece of information: the user’s currently active shopping cart ID. When a cart gets checked out, we’ll "close" the old ID and generate a new one. This ensures the right ratio of users to carts while also alleviating the burden of ID maintenance from the clients.

|  | Avoid random numbers in emptyState.
It might be tempting to try and generate a random number or UUID in the user entity’s `emptyState()` function. The consequences of this are far-reaching and subtle. If the `emptyState()` function generates a new cart UUID every time it is called, then whenever a client asks for a user entity *that has not yet received any events*, we get a new UUID. This means that if we add three items to the cart for the same not-yet-persisted user, we’ll actually create three different carts. To mitigate this, we instead used a simple monotonically increasing integer for each user. This way, not-yet-persisted users will always use cart ID `1`. |

## <a href="about:blank#_creating_isolated_data_types"></a> Creating isolated data types

To work on the separation of concerns, we’ll work our way in from the outermost edge, which in this case is the HTTP endpoint. This one new record represents the line items that can be added via `POST` to `/my/item`.

[ShoppingCartEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-with-view/src/main/java/shoppingcart/api/ShoppingCartEndpoint.java)
```java
public record LineItemRequest(String productId, String name, int quantity, String description) {
}
```
From the client standpoint, they’re supplying both the name and description of the product in the request. In subsequent tutorials, we might illustrate a better place to put product metadata like that.

Next, we need a *command* type for the entity to handle. Remember that calling `invoke` from the endpoint will only take a single parameter, so our command has to hold all of the necessary data.

[ShoppingCartEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-with-view/src/main/java/shoppingcart/application/ShoppingCartEntity.java)
```java
public record AddLineItemCommand(String userId, String productId, String name, int quantity, String description) {
}
```
Next we modify the shape of the internal state used by the entity. To illustrate the different roles of entities and views, we’ve modified the state so that it doesn’t store the `name` or `description` fields, since those aren’t needed for decision making during command processing.

[ShoppingCartState.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-with-view/src/main/java/shoppingcart/domain/ShoppingCartState.java)
```java
public record ShoppingCartState(String cartId, List<LineItem> items, boolean checkedOut) {

  public record LineItem(String productId, int quantity) {
    public LineItem withQuantity(int quantity) {
      return new LineItem(productId, quantity);
    }
  }

}
```

## <a href="about:blank#_adding_a_shopping_cart_view"></a> Adding a shopping cart view

Now that we’ve improved the separation of concerns/layers with the various data types being used in the application, we can create the `View`. A view can contain multiple tables, and each one of those tables can be thought of as roughly equivalent to a table in a traditional RDBMS, except you don’t have to worry about where or how that data is stored.

For our new view, we want all of the information on the shopping cart contents, including the name and description (which have also been added to the appropriate `ShoppingCartEvent`).

[ShoppingCartView.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-with-view/src/main/java/shoppingcart/application/ShoppingCartView.java)
```java
@ComponentId("shopping-cart-view")
public class ShoppingCartView extends View {

  @Query("SELECT * FROM shopping_carts WHERE cartId = :cartId") // (1)
  public QueryEffect<Cart> getCart(String cartId) {
    return queryResult();
  }

  @Query("SELECT * FROM shopping_carts WHERE " +
      "userId = :userId AND checkedout = false") // (2)
  public QueryEffect<Optional<Cart>> getUserCart(String userId) {
    return queryResult();
  }

  public record Cart(String cartId,
      String userId,
      List<Item> items,
      boolean checkedout) { // (3)
  }

  @Consume.FromEventSourcedEntity(ShoppingCartEntity.class) // (4)
  public static class ShoppingCartsUpdater extends TableUpdater<Cart> {

    public Effect<Cart> onEvent(ShoppingCartEvent event) {
      return switch (event) {
        case ShoppingCartEvent.ItemAdded added -> addItem(added);
        case ShoppingCartEvent.ItemRemoved removed -> removeItem(removed);
        case ShoppingCartEvent.CheckedOut checkedOut -> checkout(checkedOut);
      };
    }

    Cart rowStateOrNew(String userId) {
      if (rowState() == null) {
        var cartId = updateContext().eventSubject().get();
        return new Cart(
            cartId,
            userId,
            new ArrayList<Cart.Item>(),
            false);
      } else {
        return rowState();
      }
    }

    private Effect<Cart> addItem(ShoppingCartEvent.ItemAdded added) {
      return effects().updateRow(
          rowStateOrNew(added.userId()) // (5)
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
```

| **1** | Return a single shopping cart based on its unique ID. |
| **2** | Return a single shopping cart based on its user ID. |
| **3** | The data type for a single row of the table. |
| **4** | This view gets it data from events emitted by `ShoppingCartEntity`. |
| **5** | Either reusing the existing row state or creating a new `Cart`. |
With a newly refactored set of data types, clear boundaries between the various components, and a view in hand, there’s one more thing to do—​add the concept of a user.

## <a href="about:blank#_adding_users_to_the_app"></a> Adding users to the app

There’s a couple of things that need to be done in order to add users to the application. We’ll need a `UserEntity` that manages the current shopping cart IDs, and we’ll need to add user authentication and context to the API endpoint.

### <a href="about:blank#_creating_a_user_entity"></a> Creating a user entity

The user entity in this sample is quite small (but easily enhanced later). It maintains a `currentCartId` on behalf of a user and whenever a cart is "closed" (as a result of a checkout), we increment the cart ID.

[UserEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-with-view/src/main/java/shoppingcart/application/UserEntity.java)
```java
@ComponentId("user")
public class UserEntity extends EventSourcedEntity<UserState, UserEvent> {
  private final String entityId;

  private static final Logger logger = LoggerFactory.getLogger(UserEntity.class);

  public record CloseCartCommand(String cartId) {
  }

  public UserEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  public ReadOnlyEffect<String> currentCartId() {
    return effects().reply(entityId + "-" + currentState().currentCartId());
  }

  public Effect<Done> closeCart(CloseCartCommand command) {
    return effects()
        .persist(new UserEvent.UserCartClosed(entityId, command.cartId()))
        .thenReply(__ -> Done.done());
  }

  @Override
  public UserState emptyState() {
    int newCartId = 1;
    return new UserState(entityId, newCartId);
  }

  @Override
  public UserState applyEvent(UserEvent event) {
    logger.debug("Applying user event to user id={}", entityId);

    return switch (event) {
      case UserEvent.UserCartClosed closed -> currentState().onCartClosed(closed);
    };
  }
}
```
Incrementing the cart ID is done simply in the `onCartClosed` function of the `UserState`:

```java
public UserState onCartClosed(UserEvent.UserCartClosed closed) {
  return new UserState(userId, currentCartId + 1);
}
```

### <a href="about:blank#_adding_a_cart_consumer"></a> Adding a cart consumer

Given the preceding entity, we still need *something* to call the `closeCart` function. Since we want to close carts and bump IDs whenever a cart is checked out, we’ll create a consumer that receives `ShoppingCartEvent` events and calls the appropriate user entity method.

[CartCloser.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-with-view/src/main/java/shoppingcart/application/CartCloser.java)
```java
@ComponentId("cart-closer-consumer")
@Consume.FromEventSourcedEntity(value = ShoppingCartEntity.class, ignoreUnknown = true)
public class CartCloser extends Consumer {

  private Logger logger = LoggerFactory.getLogger(CartCloser.class);
  protected final ComponentClient componentClient;

  public CartCloser(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onCheckedOut(ShoppingCartEvent.CheckedOut event) {
    logger.debug("Closing cart for user {} due to checkout", event.userId());

    componentClient.forEventSourcedEntity(event.userId())
        .method(UserEntity::closeCart)
        .invoke(new CloseCartCommand(event.cartId()));

    return effects().done();
  }
}
```

### <a href="about:blank#_securing_the_http_endpoint"></a> Securing the HTTP endpoint

Adding the concept of a user context to an endpoint in traditional applications can be a nightmare. The refactoring can bleed into all sorts of unexpected places and building or buying—​or both—​authentication and authorization solutions can bog down entire teams.

In the following code, we add support for **[JWT](../java/auth-with-jwts.html)** -based bearer tokens to the HTTP endpoint with just a single line. While not shown here, you can define all kinds of rules based on the claims supplied in a token.

[ShoppingCartEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-with-view/src/main/java/shoppingcart/api/ShoppingCartEndpoint.java)
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)
@HttpEndpoint("/carts")
public class ShoppingCartEndpoint extends AbstractHttpEndpoint {
```
Extracting the user ID from context is quite easy. Let’s modify the `get` function so that it rejects attempts to query a shopping cart that doesn’t belong to the caller.

[ShoppingCartEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-with-view/src/main/java/shoppingcart/api/ShoppingCartEndpoint.java)
```java
@Get("/{cartId}")
public ShoppingCartView.Cart get(String cartId) {
    logger.info("Get cart id={}", cartId);

    var userId = requestContext().getJwtClaims().subject().get();

    var cart=  componentClient.forView()
      .method(ShoppingCartView::getCart) // (1)
      .invoke(cartId);

    if (cart.userId().trim().equals(userId)) {
      return cart;
    } else {
      throw HttpException.error(StatusCodes.NOT_FOUND, "no such cart");
    }
}
```

| **1** | Invoke the view’s `getCart` function to retrieve by cart ID |
We return a `404/Not Found` here for when there’s a cart ownership mismatch rather than returning the authorization-related codes of either `400` or `401`. This is to prevent malicious intruders from being able to discover the IDs of other people’s carts.

We can also add a new convenience route, `/my`, which will retrieve the cart for the currently authenticated user. This eases the burden on the UI a bit since it won’t have to do a pre-fetch to convert a user ID into a cart ID.

[ShoppingCartEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-with-view/src/main/java/shoppingcart/api/ShoppingCartEndpoint.java)
```java
@Get("/my")
public ShoppingCartView.Cart getByUser() {
    var userId = requestContext().getJwtClaims().subject().get();

    logger.info("Get cart userId={}", userId);

    var result = componentClient.forView()
            .method(ShoppingCartView::getUserCart) // (1)
            .invoke(userId);

    return result.orElseThrow(() -> HttpException.error(StatusCodes.NOT_FOUND, "no such cart"));
}
```

| **1** | Invoke the view’s `getUserCart` function to retrieve the cart by user ID |
Now we can modify all of the other data-mutating routes to use the special token `my` rather than accept an arbitrary cart ID. This has the net effect of preventing any client from making changes to anything other than the currently active cart for the current user.

This table reflects the new status of the shopping cart service’s routes:

| Path | Method | Description |
| --- | --- | --- |
| `/carts/{cartId}` | `GET` | Retrieves the cart corresponding to the supplied ID. Returns `404` if the calling user does not own the shopping cart in question |
| `/carts/my` | `GET` | Retrieves the currently active shopping cart for the current user, or `404` if the cart hasn’t been created |
| `/carts/my/item` | `PUT` | Adds a line item to the user’s current shopping cart |
| `/carts/my/item/{productId}` | `DELETE` | Removes a line item from the user’s current shopping cart |
| `/carts/my/checkout` | `POST` | Checks out the user’s current shopping cart |

### <a href="about:blank#_exercising_the_service"></a> Exercising the service

With JWT authentication in place, it’s now slightly more difficult to invoke the service via `curl`, but only because we have to generate a valid token. Since this sample doesn’t validate for specific issuers, any valid token will be fine. You can create your own tokens on [JWT.io](https://jwt.io/), or you can use the one from the following `curl` example, which interrogates the user’s current shopping cart.

```command
curl http://localhost:9000/carts/my -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJib2IiLCJuYW1lIjoiQm9iIEJvYmJlcnNvbiIsImlzcyI6ImFsaWNlIiwiaWF0IjoxNTE2MjM5MDIyfQ.wIxafOw2k4TgdCm2pH4abupetKRKS4ItOKlsNTY-pzc'
```

## <a href="about:blank#_next_steps"></a> Next steps

Now that you’ve added a view *and* user authentication to the shopping cart sample, take your Akka skills to the next level:

1. **Install and build**: Before moving on, download the code for this sample, compile it, and make sure you can run and utilize the new service.
2. **Expand on your own**: Explore [other Akka components](../concepts/architecture-model.html#_akka_components) to enhance your application with additional features.
3. **Explore other Akka samples**: Discover more about Akka by exploring [different use cases](samples.html) for inspiration.

<!-- <footer> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->
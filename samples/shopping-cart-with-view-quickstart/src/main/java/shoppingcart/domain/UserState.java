package shoppingcart.domain;

public record UserState(String userId, String currentCartId) {

  public UserState onCartClosed(UserEvent.UserCartClosed closed) {
    return new UserState(userId, closed.newCartId());
  }
}

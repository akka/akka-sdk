package shoppingcart.domain;

public record UserState(String userId, int currentCartId) {

  public UserState onCartClosed(UserEvent.UserCartClosed closed) {
    return new UserState(userId, currentCartId + 1);
  }
}

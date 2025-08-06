package shoppingcart.domain;

public record User(String userId, int currentCartId) {
  public User closeCart() {
    return new User(userId, currentCartId + 1);
  }
}

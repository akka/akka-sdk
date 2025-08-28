package store.product.domain;

public record Product(String name, Money price) {
  public Product withName(String newName) {
    return new Product(newName, price);
  }

  public Product withPrice(Money newPrice) {
    return new Product(name, newPrice);
  }
}

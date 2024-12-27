package store.order.application;

public record CreateOrder(String productId, String customerId, int quantity) {
}

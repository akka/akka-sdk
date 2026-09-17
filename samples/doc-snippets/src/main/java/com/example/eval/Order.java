package com.example.eval;

/**
 * An order as the shop's order system reports it. Amounts are in cents.
 * The note is the text the customer wrote when placing the order.
 */
public record Order(String id, String status, int totalCents, String note) {}

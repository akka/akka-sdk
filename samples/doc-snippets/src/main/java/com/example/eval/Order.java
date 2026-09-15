package com.example.eval;

/**
 * An order as the shop's order system reports it. Amounts are in cents, the note is the text the
 * customer wrote when the order was placed.
 */
public record Order(String id, String status, int totalCents, String note) {}

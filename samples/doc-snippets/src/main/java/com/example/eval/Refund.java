package com.example.eval;

/** A refund the order system issued. Amounts are in cents. */
public record Refund(String orderId, int amountCents, String reference) {}

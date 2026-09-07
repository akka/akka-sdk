package com.example.eval;

/** An order as the shop's order system reports it. Amounts are in cents. */
public record Order(String id, String status, int totalCents) {}

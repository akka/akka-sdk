package com.example.multiregion;

// tag::event[]
public sealed interface OrderEvent permits OrderPlaced {}
// end::event[]

package com.example.fibonacci;

import com.example.fibonacci.api.RequestValidator;

public record MyDependency(RequestValidator requestValidator) {
}

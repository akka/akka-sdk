package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "non-public-component")
class NonPublicComponent extends KeyValueEntity<String> {
  // This component is not public and should fail validation
}

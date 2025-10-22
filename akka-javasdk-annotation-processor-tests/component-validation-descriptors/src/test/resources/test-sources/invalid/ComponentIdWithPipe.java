package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "invalid|id")
public class ComponentIdWithPipe extends KeyValueEntity<String> {
  // This component has a pipe character in the ID and should fail validation
}

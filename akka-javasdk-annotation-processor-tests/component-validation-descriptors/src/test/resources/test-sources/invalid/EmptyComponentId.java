package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "")
public class EmptyComponentId extends KeyValueEntity<String> {
  // This component has an empty ID and should fail validation
}

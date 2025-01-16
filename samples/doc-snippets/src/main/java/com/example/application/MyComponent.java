package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@ComponentId("my-component")
public class MyComponent extends KeyValueEntity<String> {

}

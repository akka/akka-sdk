/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.badwiring.valueentity;

import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.annotations.TypeId;
import org.springframework.stereotype.Component;

@TypeId("test")
@Component
public class IllDefinedValueEntity extends ValueEntity<String> {}

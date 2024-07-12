/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.keyvalueentity;

import com.example.valueentity.shoppingcart.ShoppingCartApi;
import com.example.valueentity.shoppingcart.domain.ShoppingCartDomain;
import com.google.protobuf.Descriptors;
import com.google.protobuf.EmptyProto;

import java.util.function.Function;

/** A key value entity provider */
public class CartEntityProvider
    implements KeyValueEntityProvider<ShoppingCartDomain.Cart, CartEntity> {

  private final Function<KeyValueEntityContext, CartEntity> entityFactory;
  private final KeyValueEntityOptions options;

  /** Factory method of ShoppingCartProvider */
  public static CartEntityProvider of(Function<KeyValueEntityContext, CartEntity> entityFactory) {
    return new CartEntityProvider(entityFactory, KeyValueEntityOptions.defaults());
  }

  private CartEntityProvider(
    Function<KeyValueEntityContext, CartEntity> entityFactory, KeyValueEntityOptions options) {
    this.entityFactory = entityFactory;
    this.options = options;
  }

  @Override
  public final KeyValueEntityOptions options() {
    return options;
  }

  public final CartEntityProvider withOptions(KeyValueEntityOptions options) {
    return new CartEntityProvider(entityFactory, options);
  }

  @Override
  public final Descriptors.ServiceDescriptor serviceDescriptor() {
    return ShoppingCartApi.getDescriptor().findServiceByName("ShoppingCartService");
  }

  @Override
  public final String typeId() {
    return "shopping-cart";
  }

  @Override
  public final CartEntityRouter newRouter(KeyValueEntityContext context) {
    return new CartEntityRouter(entityFactory.apply(context));
  }

  @Override
  public final Descriptors.FileDescriptor[] additionalDescriptors() {
    return new Descriptors.FileDescriptor[] {
      ShoppingCartApi.getDescriptor(),
      ShoppingCartDomain.getDescriptor(),
      EmptyProto.getDescriptor()
    };
  }
}

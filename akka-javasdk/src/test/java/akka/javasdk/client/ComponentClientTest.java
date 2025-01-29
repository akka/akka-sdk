/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.NotUsed;
import akka.javasdk.impl.*;
import akka.javasdk.impl.serialization.JsonSerializer;
import akka.javasdk.impl.view.ViewDescriptorFactory;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.any.Any;
import akka.javasdk.JsonSupport;
import akka.javasdk.Metadata;
import akka.javasdk.impl.client.ComponentClientImpl;
import akka.javasdk.impl.client.DeferredCallImpl;
import akka.javasdk.impl.telemetry.Telemetry;
import akka.runtime.sdk.spi.TimedActionClient;
import akka.runtime.sdk.spi.TimedActionType$;
import akka.runtime.sdk.spi.ComponentClients;
import akka.runtime.sdk.spi.EntityClient;
import akka.runtime.sdk.spi.TimerClient;
import akka.runtime.sdk.spi.ViewClient;
import akka.javasdk.testmodels.Number;
import akka.javasdk.testmodels.action.ActionsTestModels.ActionWithOneParam;
import akka.javasdk.testmodels.action.ActionsTestModels.ActionWithoutParam;
import akka.javasdk.testmodels.keyvalueentity.Counter;
import akka.javasdk.testmodels.keyvalueentity.User;
import akka.javasdk.testmodels.view.ViewTestModels;
import akka.javasdk.testmodels.view.ViewTestModels.UserByEmailWithGet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.Option;
import scala.concurrent.ExecutionContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;


class ComponentClientTest {

  private final JsonSerializer serializer = new JsonSerializer();
  private ComponentClientImpl componentClient;

  @BeforeEach
  public void initEach() {
    // FIXME what are we actually testing here?
    var dummyComponentClients = new ComponentClients() {

      @Override
      public EntityClient eventSourcedEntityClient() {
        return null;
      }

      @Override
      public EntityClient keyValueEntityClient() {
        return null;
      }

      @Override
      public EntityClient workFlowClient() { return null; }

      @Override
      public TimerClient timerClient() { return null; }

      @Override
      public ViewClient viewClient() {
        return null;
      }

      @Override
      public TimedActionClient timedActionClient() {
        return null;
      }
    };
    componentClient = new ComponentClientImpl(dummyComponentClients, serializer, Option.empty(), ExecutionContext.global());
  }

  @Test
  public void shouldReturnDeferredCallForCallWithNoParameter() {
    //given
    //when
    DeferredCallImpl<NotUsed, Object> call = (DeferredCallImpl<NotUsed, Object>) componentClient.forTimedAction()
      .method(ActionWithoutParam::message)
      .deferred();

    //then
    assertEquals(call.componentType(), TimedActionType$.MODULE$);
  }

  @Test
  public void shouldReturnDeferredCallForCallWithOneParameter() {
    //given
    //when
    DeferredCallImpl<String, Object> call = (DeferredCallImpl<String, Object>)
            componentClient.forTimedAction()
                    .method(ActionWithOneParam::message)
                    .deferred("Message");

    //then
    assertEquals("Message", call.message());
  }

  @Test
  public void shouldReturnDeferredCallWithTraceParent() {
    //given
    var action = descriptorFor(ActionWithoutParam.class, serializer);
    String traceparent = "074c4c8d-d87c-4573-847f-77951ce4e0a4";
    Metadata metadata = MetadataImpl.Empty().set(Telemetry.TRACE_PARENT_KEY(), traceparent);
    //when
    DeferredCallImpl<NotUsed, Object> call = (DeferredCallImpl<NotUsed, Object>)
      componentClient.forTimedAction()
        .method(ActionWithoutParam::message)
        .withMetadata(metadata)
        .deferred();

    //then
    assertThat(call.metadata().get(Telemetry.TRACE_PARENT_KEY()).get()).isEqualTo(traceparent);
  }

  @Test
  public void shouldReturnDeferredCallForValueEntity() throws InvalidProtocolBufferException {
    //given
    Integer param = 10;
    var id = "abc123";

    //when
    DeferredCallImpl<Integer, Number> call = (DeferredCallImpl<Integer, Number>)
      componentClient.forKeyValueEntity(id)
        .method(Counter::randomIncrease)
        .deferred(param);

    //then
    assertThat(call.componentId()).isEqualTo(ComponentDescriptorFactory.readComponentIdIdValue(Counter.class));
    assertEquals(10, call.message());
  }



  @Test
  public void shouldReturnNonDeferrableCallForViewRequest() {
    //given
    String email = "email@example.com";

    ViewTestModels.ByEmail body = new ViewTestModels.ByEmail(email);
    //when
    ComponentInvokeOnlyMethodRef1<ViewTestModels.ByEmail, User> call =
      componentClient.forView()
      .method(UserByEmailWithGet::getUser);

    // not much to assert here

  }

  private ComponentDescriptor descriptorFor(Class<?> clazz, JsonSerializer serializer) {
    Validations.validate(clazz).failIfInvalid();
    return ComponentDescriptor.descriptorFor(clazz, serializer);
  }

  private <T> T getBody(Descriptors.MethodDescriptor targetMethod, Any message, Class<T> clazz) throws InvalidProtocolBufferException {
    var dynamicMessage = DynamicMessage.parseFrom(targetMethod.getInputType(), message.value());
    var body = (DynamicMessage) targetMethod.getInputType()
      .getFields().stream()
      .filter(f -> f.getName().equals("json_body"))
      .map(dynamicMessage::getField)
      .findFirst().orElseThrow();

    return decodeJson(body, clazz);
  }

  private <T> T decodeJson(DynamicMessage dm, Class<T> clazz) {
    String typeUrl = (String) dm.getField(Any.javaDescriptor().findFieldByName("type_url"));
    ByteString bytes = (ByteString) dm.getField(Any.javaDescriptor().findFieldByName("value"));

    var any = com.google.protobuf.Any.newBuilder().setTypeUrl(typeUrl).setValue(bytes).build();

    return JsonSupport.decodeJson(clazz, any);
  }

  private void assertMethodParamsMatch(Descriptors.MethodDescriptor targetMethod, Object message) throws InvalidProtocolBufferException {
    assertThat(message.getClass()).isEqualTo(targetMethod.getInputType().getFullName());
  }
}

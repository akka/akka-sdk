/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.client;

import akka.NotUsed;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.any.Any;
import akka.platform.javasdk.JsonSupport;
import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.impl.ComponentDescriptor;
import akka.platform.javasdk.impl.JsonMessageCodec;
import akka.platform.javasdk.impl.MetadataImpl;
import akka.platform.javasdk.impl.Validations;
import akka.platform.javasdk.impl.client.ComponentClientImpl;
import akka.platform.javasdk.impl.client.DeferredCallImpl;
import akka.platform.javasdk.impl.telemetry.Telemetry;
import akka.platform.javasdk.spi.ActionClient;
import akka.platform.javasdk.spi.ActionType$;
import akka.platform.javasdk.spi.ComponentClients;
import akka.platform.javasdk.spi.EntityClient;
import akka.platform.javasdk.spi.TimerClient;
import akka.platform.javasdk.spi.ViewClient;
import akka.platform.spring.testmodels.Message;
import akka.platform.spring.testmodels.Number;
import akka.platform.spring.testmodels.action.ActionsTestModels.ActionWithOneParam;
import akka.platform.spring.testmodels.action.ActionsTestModels.ActionWithoutParam;
import akka.platform.spring.testmodels.keyvalueentity.Counter;
import akka.platform.spring.testmodels.keyvalueentity.User;
import akka.platform.spring.testmodels.view.ViewTestModels;
import akka.platform.spring.testmodels.view.ViewTestModels.UserByEmailWithGet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.Option;
import scala.concurrent.ExecutionContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;


class ComponentClientTest {

  private final JsonMessageCodec messageCodec = new JsonMessageCodec();
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
      public ActionClient actionClient() {
        return null;
      }
    };
    componentClient = new ComponentClientImpl(dummyComponentClients, Option.empty(), ExecutionContext.global());
  }

  @Test
  public void shouldReturnDeferredCallForCallWithNoParameter() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(ActionWithoutParam.class, messageCodec);
    var targetMethod = action.serviceDescriptor().findMethodByName("Message");

    //when
    DeferredCallImpl<NotUsed, Message> call = (DeferredCallImpl<NotUsed, Message>)
      componentClient.forAction()
        .method(ActionWithoutParam::message)
        .deferred();

    //then
    assertEquals(call.componentType(), ActionType$.MODULE$);
  }

  @Test
  public void shouldReturnDeferredCallForCallWithOneParameter() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(ActionWithoutParam.class, messageCodec);
    var targetMethod = action.serviceDescriptor().findMethodByName("Message");

    //when
    DeferredCallImpl<String, Message> call = (DeferredCallImpl<String, Message>)
            componentClient.forAction()
                    .method(ActionWithOneParam::message)
                    .deferred("Message");

    //then
    assertEquals("Message", call.message());
  }

  @Test
  public void shouldReturnDeferredCallWithTraceParent() {
    //given
    var action = descriptorFor(ActionWithoutParam.class, messageCodec);
    String traceparent = "074c4c8d-d87c-4573-847f-77951ce4e0a4";
    Metadata metadata = MetadataImpl.Empty().set(Telemetry.TRACE_PARENT_KEY(), traceparent);
    //when
    DeferredCallImpl<NotUsed, Message> call = (DeferredCallImpl<NotUsed, Message>)
      componentClient.forAction()
        .method(ActionWithoutParam::message)
        .withMetadata(metadata)
        .deferred();

    //then
    assertThat(call.metadata().get(Telemetry.TRACE_PARENT_KEY()).get()).isEqualTo(traceparent);
  }

  @Test
  public void shouldReturnDeferredCallForValueEntity() throws InvalidProtocolBufferException {
    //given
    var counterVE = descriptorFor(Counter.class, messageCodec);
    var targetMethod = counterVE.serviceDescriptor().findMethodByName("RandomIncrease");
    Integer param = 10;

    var id = "abc123";
    //when
    DeferredCallImpl<Integer, Number> call = (DeferredCallImpl<Integer, Number>)
      componentClient.forKeyValueEntity(id)
        .method(Counter::randomIncrease)
        .deferred(param);

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertEquals(10, call.message());
  }



  @Test
  public void shouldReturnNonDeferrableCallForViewRequest() throws InvalidProtocolBufferException {
    //given
    var view = descriptorFor(UserByEmailWithGet.class, messageCodec);
    var targetMethod = view.serviceDescriptor().findMethodByName("GetUser");
    String email = "email@example.com";

    ViewTestModels.ByEmail body = new ViewTestModels.ByEmail(email);
    //when
    ComponentInvokeOnlyMethodRef1<ViewTestModels.ByEmail, User> call =
      componentClient.forView()
      .method(UserByEmailWithGet::getUser);

    // not much to assert here

  }

  private ComponentDescriptor descriptorFor(Class<?> clazz, JsonMessageCodec messageCodec) {
    Validations.validate(clazz).failIfInvalid();
    return ComponentDescriptor.descriptorFor(clazz, messageCodec);
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
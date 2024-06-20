/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.any.Any;
import kalix.javasdk.JsonSupport;
import kalix.javasdk.Metadata;
import kalix.javasdk.impl.AnySupport;
import kalix.javasdk.impl.ComponentDescriptor;
import kalix.javasdk.impl.JsonMessageCodec;
import kalix.javasdk.impl.MetadataImpl;
import kalix.javasdk.impl.RestDeferredCall;
import kalix.javasdk.impl.Validations;
import kalix.javasdk.impl.client.ComponentClientImpl;
import kalix.javasdk.impl.client.EmbeddedDeferredCall;
import kalix.javasdk.impl.client.EmbeddedDeferredCall$;
import kalix.javasdk.impl.telemetry.Telemetry;
import kalix.javasdk.spi.ActionClient;
import kalix.javasdk.spi.ComponentClients;
import kalix.javasdk.spi.EntityClient;
import kalix.javasdk.spi.TimerClient;
import kalix.javasdk.spi.ViewClient;
import kalix.spring.impl.RestKalixClientImpl;
import kalix.spring.testmodels.Message;
import kalix.spring.testmodels.Number;
import kalix.spring.testmodels.action.ActionsTestModels.GetClassLevel;
import kalix.spring.testmodels.action.ActionsTestModels.GetWithOneParam;
import kalix.spring.testmodels.action.ActionsTestModels.GetWithoutParam;
import kalix.spring.testmodels.action.ActionsTestModels.PostWithOneQueryParam;
import kalix.spring.testmodels.action.ActionsTestModels.PostWithTwoParam;
import kalix.spring.testmodels.action.ActionsTestModels.PostWithoutParam;
import kalix.spring.testmodels.valueentity.Counter;
import kalix.spring.testmodels.valueentity.User;
import kalix.spring.testmodels.view.ViewTestModels;
import kalix.spring.testmodels.view.ViewTestModels.UserByEmailWithGet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import scala.concurrent.ExecutionContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;


class ComponentClientTest {

  private final JsonMessageCodec messageCodec = new JsonMessageCodec();
  private RestKalixClientImpl restKalixClient;
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
      public EntityClient valueEntityClient() {
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
    restKalixClient = new RestKalixClientImpl(messageCodec, dummyComponentClients, ExecutionContext.global());
    componentClient = new ComponentClientImpl(restKalixClient);
  }

  @Test
  public void shouldNotReturnDeferredCallMethodNotAnnotatedAsRESTEndpoint() {
    assertThatThrownBy(() -> componentClient.forAction().method(GetWithoutParam::missingRestAnnotation).deferred())
      .hasMessage("Method [missingRestAnnotation] is not annotated as a REST endpoint.");
  }


  @Test
  public void shouldReturnDeferredCallForSimpleGETRequest() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(GetWithoutParam.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    var targetMethod = action.serviceDescriptor().findMethodByName("Message");

    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
        .method(GetWithoutParam::message)
        .deferred();

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertMethodParamsMatch(targetMethod, call.message());
  }

  @Test
  public void shouldReturnDeferredCallForGETRequestWithParam() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(GetWithOneParam.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    var targetMethod = action.serviceDescriptor().findMethodByName("Message");
    String param = "a b&c@d";

    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
      .method(GetWithOneParam::message)
      .deferred(param);

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertMethodParamsMatch(targetMethod, call.message(), param);
  }

  @Test
  public void shouldReturnDeferredCallForGETRequestWithTwoParams() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(GetClassLevel.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    var targetMethod = action.serviceDescriptor().findMethodByName("Message");
    String param = "a b&c@d";
    Long param2 = 2L;

    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
      .method(GetClassLevel::message)
      .deferred(param, param2);

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertMethodParamsMatch(targetMethod, call.message(), param, param2);
  }

  @Test
  public void shouldReturnDeferredCallForGETRequestWithTwoPathParamsAnd2ReqParams() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(GetClassLevel.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    var targetMethod = action.serviceDescriptor().findMethodByName("Message2");
    String param = "a b&c@d";
    Long param2 = 2L;
    String param3 = "!@!#$%^%++___";
    int param4 = 4;

    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
      .method(GetClassLevel::message2)
      .deferred(param, param2, param3, param4);

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertMethodParamsMatch(targetMethod, call.message(), param, param2, param3, param4);
  }

  @Test
  public void shouldReturnDeferredCallForGETRequestWithListAsReqParam() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(GetClassLevel.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    var targetMethod = action.serviceDescriptor().findMethodByName("Message3");
    String param = "a b&c@d";
    Long param2 = 2L;
    String param3 = "!@!#$%^%++___";
    List<String> param4 = List.of("1", "2");

    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
      .method(GetClassLevel::message3)
      .deferred(param, param2, param3, param4);

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertMethodParamsMatch(targetMethod, call.message(), param, param2, param3, param4);
  }

  @Test
  public void shouldReturnDeferredCallForSimplePOSTRequest() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(PostWithoutParam.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    var targetMethod = action.serviceDescriptor().findMethodByName("Message");
    Message body = new Message("hello world");

    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
        .method(PostWithoutParam::message)
        .deferred(body);

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertThat(getBody(targetMethod, call.message(), Message.class)).isEqualTo(body);
  }

  @Test
  public void shouldReturnDeferredCallForPOSTRequestWithTwoParamsAndBody() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(PostWithTwoParam.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    var targetMethod = action.serviceDescriptor().findMethodByName("Message");
    String param = "a b&c@d";
    Long param2 = 2L;
    Message body = new Message("hello world");

    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
      .method(PostWithTwoParam::message)
      .deferred(param, param2, body);

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertMethodParamsMatch(targetMethod, call.message(), param, param2);
    assertThat(getBody(targetMethod, call.message(), Message.class)).isEqualTo(body);
  }

  @Test
  public void shouldReturnDeferredCallForPOSTRequestWhenMultipleMethodsAreAvailable() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(PostWithoutParam.class, messageCodec);
    var action2 = descriptorFor(PostWithTwoParam.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    restKalixClient.registerComponent(action2.serviceDescriptor());
    var targetMethod = action.serviceDescriptor().findMethodByName("Message");
    Message body = new Message("hello world");

    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
        .method(PostWithoutParam::message)
        .deferred(body);

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertThat(getBody(targetMethod, call.message(), Message.class)).isEqualTo(body);
  }

  @Test
  public void shouldReturnDeferredCallForPOSTWithRequestParams() throws InvalidProtocolBufferException {
    //given
    var action = descriptorFor(PostWithOneQueryParam.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    var targetMethod = action.serviceDescriptor().findMethodByName("Message");
    String param = "a b&c@d";
    Message body = new Message("hello world");

    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
      .method(PostWithOneQueryParam::message)
      .deferred(param, body);

    //then
    assertThat(call.fullServiceName()).isEqualTo(targetMethod.getService().getFullName());
    assertThat(call.methodName()).isEqualTo(targetMethod.getName());
    assertMethodParamsMatch(targetMethod, call.message(), param);
    assertThat(getBody(targetMethod, call.message(), Message.class)).isEqualTo(body);
  }

  @Test
  public void shouldReturnDeferredCallWithTraceParent() {
    //given
    var action = descriptorFor(PostWithOneQueryParam.class, messageCodec);
    restKalixClient.registerComponent(action.serviceDescriptor());
    String param = "a b&c@d";
    Message body = new Message("hello world");
    String traceparent = "074c4c8d-d87c-4573-847f-77951ce4e0a4";
    Metadata metadata = MetadataImpl.Empty().set(Telemetry.TRACE_PARENT_KEY(), traceparent);
    componentClient.setCallMetadata(metadata);
    //when
    RestDeferredCall<Any, Message> call = (RestDeferredCall<Any, Message>)
      componentClient.forAction()
        .method(PostWithOneQueryParam::message)
        .deferred(param, body);

    //then
    assertThat(call.metadata().get(Telemetry.TRACE_PARENT_KEY()).get()).isEqualTo(traceparent);
  }

  @Test
  public void shouldReturnDeferredCallForValueEntity() throws InvalidProtocolBufferException {
    //given
    var counterVE = descriptorFor(Counter.class, messageCodec);
    restKalixClient.registerComponent(counterVE.serviceDescriptor());
    var targetMethod = counterVE.serviceDescriptor().findMethodByName("RandomIncrease");
    Integer param = 10;

    var id = "abc123";
    //when
    EmbeddedDeferredCall<Integer, Number> call = (EmbeddedDeferredCall<Integer, Number>)
      componentClient.forValueEntity(id)
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
    restKalixClient.registerComponent(view.serviceDescriptor());
    var targetMethod = view.serviceDescriptor().findMethodByName("GetUser");
    String email = "email@example.com";

    ViewTestModels.ByEmail body = new ViewTestModels.ByEmail(email);
    //when
    NativeComponentInvokeOnlyMethodRef1<ViewTestModels.ByEmail, User> call =
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

  private void assertMethodParamsMatch(Descriptors.MethodDescriptor targetMethod, Any message, Object... methodArgs) throws InvalidProtocolBufferException {
    assertThat(message.typeUrl()).isEqualTo(AnySupport.DefaultTypeUrlPrefix() + "/" + targetMethod.getInputType().getFullName());
    var dynamicMessage = DynamicMessage.parseFrom(targetMethod.getInputType(), message.value());

    List<Object> args = targetMethod.getInputType().getFields().stream().filter(f -> !f.getName().equals("json_body")).map(dynamicMessage::getField).toList();

    assertThat(args).containsOnly(methodArgs);
  }
}
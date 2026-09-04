/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static akka.javasdk.testkit.TestKit.Settings.EventingSupport.TEST_BROKER;

import akka.actor.ExtendedActorSystem;
import akka.actor.typed.ActorSystem;
import akka.grpc.javadsl.AkkaGrpcClient;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.DependencyProvider;
import akka.javasdk.Metadata;
import akka.javasdk.Principal;
import akka.javasdk.Sanitizer;
import akka.javasdk.ServiceSetup;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.grpc.GrpcClientProvider;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.impl.ErrorHandling;
import akka.javasdk.impl.Sdk;
import akka.javasdk.impl.SdkRunner;
import akka.javasdk.impl.client.ComponentClientImpl;
import akka.javasdk.impl.grpc.GrpcClientProviderImpl;
import akka.javasdk.impl.http.HttpClientImpl;
import akka.javasdk.impl.serialization.Serializer;
import akka.javasdk.impl.timer.TimerSchedulerImpl;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.testkit.impl.MockedGrpcServicesImpl;
import akka.javasdk.testkit.impl.MockedHttpServicesImpl;
import akka.javasdk.testkit.impl.SseRouteTesterImpl;
import akka.javasdk.testkit.impl.WebSocketRouteTesterImpl;
import akka.javasdk.timer.TimerScheduler;
import akka.javasdk.workflow.Workflow;
import akka.runtime.sdk.spi.ComponentClients;
import akka.runtime.sdk.spi.EventLogClient;
import akka.runtime.sdk.spi.SpiBackofficeSettings$;
import akka.runtime.sdk.spi.SpiDevModeSettings;
import akka.runtime.sdk.spi.SpiDevObjectStorageBucketConfig;
import akka.runtime.sdk.spi.SpiDevObjectStorageFilesystemBucketConfig;
import akka.runtime.sdk.spi.SpiDevObjectStorageGcsBucketConfig;
import akka.runtime.sdk.spi.SpiDevObjectStorageGcsCredentials;
import akka.runtime.sdk.spi.SpiDevObjectStorageGcsNativeCredentials$;
import akka.runtime.sdk.spi.SpiDevObjectStorageGcsServiceAccountKeyCredentials;
import akka.runtime.sdk.spi.SpiDevObjectStorageS3BucketConfig;
import akka.runtime.sdk.spi.SpiDevObjectStorageS3Credentials;
import akka.runtime.sdk.spi.SpiDevObjectStorageS3NativeCredentials$;
import akka.runtime.sdk.spi.SpiDevObjectStorageS3ProfileCredentials;
import akka.runtime.sdk.spi.SpiDevObjectStorageS3StaticCredentials;
import akka.runtime.sdk.spi.SpiEventingSupportSettings;
import akka.runtime.sdk.spi.SpiMockedEventingSettings;
import akka.runtime.sdk.spi.SpiSettings;
import akka.runtime.sdk.spi.SpiTestSettings;
import akka.runtime.sdk.spi.tracing.InMemorySpanExporter;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import kalix.runtime.AkkaRuntimeMain;
import kalix.runtime.Serve;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.Some;
import scala.concurrent.duration.FiniteDuration;
import scala.jdk.javaapi.FutureConverters;

/**
 * Testkit for running services locally.
 *
 * <p>Create a TestKit and then {@link #start} the testkit before testing the service with HTTP
 * clients. Call {@link #stop} after tests are complete.
 */
public class TestKit {

  public static class MockedEventing {
    public static final String KEY_VALUE_ENTITY = "key-value-entity";
    public static final String EVENT_SOURCED_ENTITY = "event-sourced-entity";
    public static final String WORKFLOW = "workflow";
    public static final String STREAM = "stream";
    public static final String TOPIC = "topic";
    private final Map<String, Set<String>> mockedIncomingEvents; // Subscriptions
    private final Map<String, Set<String>> mockedOutgoingEvents; // Destination

    private MockedEventing() {
      this(new HashMap<>(), new HashMap<>());
    }

    private MockedEventing(
        Map<String, Set<String>> mockedIncomingEvents,
        Map<String, Set<String>> mockedOutgoingEvents) {
      this.mockedIncomingEvents = mockedIncomingEvents;
      this.mockedOutgoingEvents = mockedOutgoingEvents;
    }

    public static final MockedEventing EMPTY = new MockedEventing();

    public MockedEventing withKeyValueEntityIncomingMessages(String componentId) {
      return updateIncomingMessages(KEY_VALUE_ENTITY, componentId);
    }

    public MockedEventing withEventSourcedIncomingMessages(String componentId) {
      return updateIncomingMessages(EVENT_SOURCED_ENTITY, componentId);
    }

    public MockedEventing withWorkflowIncomingMessages(String componentId) {
      return updateIncomingMessages(WORKFLOW, componentId);
    }

    public MockedEventing withStreamIncomingMessages(String service, String streamId) {
      return updateIncomingMessages(STREAM, service + "/" + streamId);
    }

    public MockedEventing withTopicIncomingMessages(String topic) {
      return updateIncomingMessages(TOPIC, topic);
    }

    private MockedEventing updateIncomingMessages(String keyValueEntity, String componentId) {
      Map<String, Set<String>> copy = new HashMap<>(mockedIncomingEvents);
      copy.compute(keyValueEntity, updateValues(componentId));
      return new MockedEventing(copy, new HashMap<>(mockedOutgoingEvents));
    }

    public MockedEventing withTopicOutgoingMessages(String topic) {
      Map<String, Set<String>> copy = new HashMap<>(mockedOutgoingEvents);
      copy.compute(TOPIC, updateValues(topic));
      return new MockedEventing(new HashMap<>(mockedIncomingEvents), copy);
    }

    public MockedEventing withStreamOutgoingMessages(String service, String streamId) {
      Map<String, Set<String>> copy = new HashMap<>(mockedOutgoingEvents);
      copy.compute(STREAM, updateValues(service + "/" + streamId));
      return new MockedEventing(new HashMap<>(mockedIncomingEvents), copy);
    }

    @NotNull
    private BiFunction<String, Set<String>, Set<String>> updateValues(String componentId) {
      return (key, currentValues) -> {
        if (currentValues == null) {
          LinkedHashSet<String> values = new LinkedHashSet<>(); // order is relevant only for tests
          values.add(componentId);
          return values;
        } else {
          currentValues.add(componentId);
          return currentValues;
        }
      };
    }

    @Override
    public String toString() {
      return "MockedEventing{"
          + "mockedIncomingEvents="
          + mockedIncomingEvents
          + ", mockedOutgoingEvents="
          + mockedOutgoingEvents
          + '}';
    }

    public boolean hasIncomingConfig() {
      return !mockedIncomingEvents.isEmpty();
    }

    public boolean hasConfig() {
      return hasIncomingConfig() || hasOutgoingConfig();
    }

    public boolean hasOutgoingConfig() {
      return !mockedOutgoingEvents.isEmpty();
    }

    public String toIncomingFlowConfig() {
      return toConfig(mockedIncomingEvents);
    }

    public String toOutgoingFlowConfig() {
      return toConfig(mockedOutgoingEvents);
    }

    private String toConfig(Map<String, Set<String>> configs) {
      return configs.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .flatMap(
              entry -> {
                String subscriptionType = entry.getKey();
                return entry.getValue().stream().map(name -> subscriptionType + "," + name);
              })
          .collect(Collectors.joining(";"));
    }

    boolean hasKeyValueEntitySubscription(String componentId) {
      return checkExistence(KEY_VALUE_ENTITY, componentId);
    }

    boolean hasEventSourcedEntitySubscription(String componentId) {
      return checkExistence(EVENT_SOURCED_ENTITY, componentId);
    }

    boolean hasWorkflowSubscription(String componentId) {
      return checkExistence(WORKFLOW, componentId);
    }

    boolean hasStreamSubscription(String service, String streamId) {
      return checkExistence(STREAM, service + "/" + streamId);
    }

    boolean hasTopicSubscription(String topic) {
      return checkExistence(TOPIC, topic);
    }

    boolean hasTopicDestination(String topic) {
      Set<String> values = mockedOutgoingEvents.get(TOPIC);
      return values != null && values.contains(topic);
    }

    boolean hasStreamDestination(String service, String streamId) {
      Set<String> values = mockedOutgoingEvents.get(STREAM);
      return values != null && values.contains(service + "/" + streamId);
    }

    private boolean checkExistence(String type, String name) {
      Set<String> values = mockedIncomingEvents.get(type);
      return values != null && values.contains(name);
    }
  }

  /** Settings for testkit. */
  public static class Settings {
    /** Default settings for testkit. */
    public static Settings DEFAULT =
        new Settings(
            "",
            true,
            TEST_BROKER,
            MockedEventing.EMPTY,
            Optional.empty(),
            ConfigFactory.empty(),
            Set.of(),
            false,
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            Collections.emptyList(),
            false);

    /** The name of this service when deployed. */
    public final String serviceName;

    /** Whether ACL checking is enabled. */
    public final boolean aclEnabled;

    /**
     * Whether the runtime is asked for a port assigned by the operating system rather than the
     * configured one. See {@link #withEphemeralPort()}.
     */
    public final boolean ephemeralPort;

    public final EventingSupport eventingSupport;

    public final MockedEventing mockedEventing;

    public final Config additionalConfig;

    public final Optional<DependencyProvider> dependencyProvider;

    public final Set<Class<?>> disabledComponents;

    /**
     * When true, the testkit disabled components override any disabled components from
     * ServiceSetup, rather than being combined with them.
     */
    public final boolean overrideDisabledComponents;

    public final Map<String, ModelProvider> modelProvidersByAgentId;

    /**
     * Map from service name to a synchronous HTTP request handler that mocks responses for that
     * service.
     */
    public final Map<String, Function<HttpRequest, HttpResponse>> httpMocks;

    /**
     * Map from service name to a map of gRPC service client class -> mock instance, used to mock
     * gRPC service calls for the given service name.
     */
    public final Map<String, Map<Class<? extends AkkaGrpcClient>, AkkaGrpcClient>> grpcMocks;

    public final List<ObjectStorageBucketConfig> objectStorageBuckets;

    public enum EventingSupport {
      /**
       * This is the default type used and allows the testing eventing integrations without an
       * external broker dependency running.
       */
      TEST_BROKER,

      /**
       * Used if you want to use an external Google PubSub (or its Emulator) on your tests.
       *
       * <p>Note: the Google PubSub broker instance needs to be started independently.
       */
      GOOGLE_PUBSUB,

      /**
       * Used if you want to use an external Kafka broker on your tests.
       *
       * <p>Note: the Kafka broker instance needs to be started independently.
       */
      KAFKA
    }

    private Settings(
        String serviceName,
        boolean aclEnabled,
        EventingSupport eventingSupport,
        MockedEventing mockedEventing,
        Optional<DependencyProvider> dependencyProvider,
        Config additionalConfig,
        Set<Class<?>> disabledComponents,
        boolean overrideDisabledComponents,
        Map<String, ModelProvider> modelProvidersByAgentId,
        Map<String, Function<HttpRequest, HttpResponse>> httpMocks,
        Map<String, Map<Class<? extends AkkaGrpcClient>, AkkaGrpcClient>> grpcMocks,
        List<ObjectStorageBucketConfig> objectStorageBuckets,
        boolean ephemeralPort) {
      this.serviceName = serviceName;
      this.aclEnabled = aclEnabled;
      this.eventingSupport = eventingSupport;
      this.mockedEventing = mockedEventing;
      this.dependencyProvider = dependencyProvider;
      this.additionalConfig = additionalConfig;
      this.disabledComponents = disabledComponents;
      this.overrideDisabledComponents = overrideDisabledComponents;
      this.modelProvidersByAgentId = modelProvidersByAgentId;
      this.httpMocks = httpMocks;
      this.grpcMocks = grpcMocks;
      this.objectStorageBuckets = objectStorageBuckets;
      this.ephemeralPort = ephemeralPort;
    }

    /**
     * Set the name of this service. This will be used by the service when making calls on other
     * services run by the testkit to authenticate itself, allowing those services to apply ACLs
     * based on that name. If not defined, the value from configuration key {@code
     * akka.javasdk.dev-mode.service-name} will be used in the test.
     *
     * @param serviceName The name of this service.
     * @return The updated settings.
     */
    public Settings withServiceName(final String serviceName) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Disable ACL checking in this service.
     *
     * @return The updated settings.
     */
    public Settings withAclDisabled() {
      return new Settings(
          serviceName,
          false,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Enable ACL checking in this service (this is the default).
     *
     * @return The updated settings.
     */
    public Settings withAclEnabled() {
      return new Settings(
          serviceName,
          true,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Ask the operating system for a free port instead of using the configured {@code
     * akka.javasdk.testkit.http-port}. Read the assigned port with {@link TestKit#getPort()}.
     *
     * <p>Use this when several test classes run in the same JVM: they otherwise take turns on one
     * configured port, and a class fails to start while the previous runtime still holds it.
     *
     * <p>The service is reached on one port, whether the request is HTTP or gRPC, so this is the
     * port for both.
     *
     * <p>The assigned port cannot be named in configuration, because a {@code
     * ${akka.javasdk.testkit.http-port}} interpolation is resolved when the config is loaded, long
     * before a port is assigned. Under this setting that key reads {@code 0}, so a client
     * configured that way fails rather than reaching some other service. The entry the testkit
     * writes for calling the service under test over gRPC is set from the assigned port and works
     * either way.
     *
     * <p>Setting {@code akka.javasdk.testkit.http-port = 0} does the same thing.
     *
     * @return The updated settings.
     */
    public Settings withEphemeralPort() {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          true);
    }

    /** Mock the incoming messages flow from a KeyValueEntity. */
    public Settings withKeyValueEntityIncomingMessages(
        Class<? extends KeyValueEntity<?>> keyValueEntityClass) {
      String componentId = getComponentId(keyValueEntityClass);
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withKeyValueEntityIncomingMessages(componentId),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /** Mock the incoming events flow from an EventSourcedEntity. */
    public Settings withEventSourcedEntityIncomingMessages(
        Class<? extends EventSourcedEntity<?, ?>> eventSourcedEntityClass) {
      String componentId = getComponentId(eventSourcedEntityClass);
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withEventSourcedIncomingMessages(componentId),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /** Mock the incoming state updates flow from a Workflow. */
    public Settings withWorkflowIncomingMessages(Class<? extends Workflow<?>> workflowClass) {
      String componentId = getComponentId(workflowClass);
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withWorkflowIncomingMessages(componentId),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Mock the incoming messages flow from a Stream (eventing.in.direct in case of protobuf SDKs).
     */
    public Settings withStreamIncomingMessages(String service, String streamId) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withStreamIncomingMessages(service, streamId),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /** Mock the incoming events flow from a Topic. */
    public Settings withTopicIncomingMessages(String topic) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withTopicIncomingMessages(topic),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /** Mock the outgoing events flow for a Topic. */
    public Settings withTopicOutgoingMessages(String topic) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withTopicOutgoingMessages(topic),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Capture the outgoing messages produced by a {@code @Produce.ServiceStream} for test
     * assertions.
     */
    public Settings withStreamOutgoingMessages(String service, String streamId) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withStreamOutgoingMessages(service, streamId),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    public Settings withEventingSupport(EventingSupport eventingSupport) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Specify additional config that will override the application-test.conf or application.conf
     * configuration in a particular test.
     */
    public Settings withAdditionalConfig(Config additionalConfig) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Specify additional config that will override the application-test.conf or application.conf
     * configuration in a particular test.
     */
    public Settings withAdditionalConfig(String additionalConfig) {
      return withAdditionalConfig(ConfigFactory.parseString(additionalConfig));
    }

    /**
     * Set a dependency provider that will be used for looking up arbitrary dependencies, useful to
     * provide mocks for production dependencies in tests rather than calling the real thing.
     */
    public Settings withDependencyProvider(DependencyProvider dependencyProvider) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          Optional.of(dependencyProvider),
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Disable components from running, useful for testing components in isolation. This set of
     * disabled components will be added to {@link ServiceSetup#disabledComponents()} if configured.
     */
    public Settings withDisabledComponents(Set<Class<?>> disabledComponents) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Disable components from running, useful for testing components in isolation. This set of
     * disabled components will override the {@link ServiceSetup#disabledComponents()} if
     * configured.
     */
    public Settings withOverriddenDisabledComponents(Set<Class<?>> disabledComponents) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          true,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Enable all components to run, overriding any disabled components from {@link
     * ServiceSetup#disabledComponents()}. This is useful for integration tests that need to test
     * all components even if some are disabled in the Bootstrap configuration.
     *
     * @return The updated settings with all components enabled.
     */
    public Settings withAllComponentsEnabled() {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          Set.of(),
          true,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Override the model provider for an {@link Agent} or {@link AutonomousAgent} component. The
     * class must be annotated with {@code @Component(id = "...")} or {@code @ComponentId}.
     */
    public Settings withModelProvider(Class<?> agentClass, ModelProvider modelProvider) {
      var componentId = getComponentId(agentClass);
      var newModelProvidersByAgentId = new HashMap<>(modelProvidersByAgentId);
      newModelProvidersByAgentId.put(componentId, modelProvider);
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          newModelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Mock an HTTP service for calls made through {@link HttpClientProvider#httpClientFor(String)}
     * with the given service name. The handler runs synchronously on the SDK dispatcher (virtual
     * threads), so blocking in the handler is safe. Calls for service names that are not mocked
     * continue to go through normal resolution.
     *
     * <p>Handler invocations can overlap when the service under test issues concurrent requests, so
     * any state shared between the handler and the test class (captured fields, counters, queues)
     * must be thread-safe.
     *
     * @param mockServiceName The service name (or full URL) that the service under test will pass
     *     to {@link HttpClientProvider#httpClientFor(String)}.
     * @param handler A function that maps an incoming {@link HttpRequest} to the {@link
     *     HttpResponse} the mock should return.
     * @return The updated settings.
     */
    public Settings withMockedHttpService(
        String mockServiceName, Function<HttpRequest, HttpResponse> handler) {
      var newHttpMocks = new HashMap<>(httpMocks);
      newHttpMocks.put(mockServiceName, handler);
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          newHttpMocks,
          grpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    /**
     * Add a named object-storage bucket for use in dev mode / integration tests. When any bucket is
     * configured, only the named buckets are accessible (same as production).
     *
     * @param bucket the bucket configuration
     * @return The updated settings.
     */
    public Settings withObjectStorageBucket(ObjectStorageBucketConfig bucket) {
      var newBuckets = new ArrayList<>(objectStorageBuckets);
      newBuckets.add(bucket);
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          grpcMocks,
          newBuckets,
          ephemeralPort);
    }

    /**
     * Mock a gRPC service for calls made through {@link
     * akka.javasdk.grpc.GrpcClientProvider#grpcClientFor(Class, String)} with the given service
     * name and generated service client interface. The mock is a user-provided instance of the
     * generated Akka gRPC client interface. Calls for service-class/name combinations that are not
     * mocked continue to go through normal resolution.
     *
     * <p>The mock instance is shared across calls and method invocations can overlap when the
     * service under test issues concurrent requests, so any state shared between the mock and the
     * test class (captured fields, counters, queues) must be thread-safe.
     *
     * @param mockServiceName The service name that the service under test will pass to {@link
     *     akka.javasdk.grpc.GrpcClientProvider#grpcClientFor(Class, String)}.
     * @param serviceClass The Akka gRPC generated client interface.
     * @param mockInstance A user-provided implementation of the generated client interface.
     * @return The updated settings.
     */
    public <T extends AkkaGrpcClient> Settings withMockedGrpcService(
        String mockServiceName, Class<T> serviceClass, T mockInstance) {
      var newGrpcMocks =
          new HashMap<String, Map<Class<? extends AkkaGrpcClient>, AkkaGrpcClient>>();
      grpcMocks.forEach(
          (k, v) ->
              newGrpcMocks.put(k, new HashMap<Class<? extends AkkaGrpcClient>, AkkaGrpcClient>(v)));
      newGrpcMocks
          .computeIfAbsent(mockServiceName, k -> new HashMap<>())
          .put(serviceClass, mockInstance);
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing,
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          overrideDisabledComponents,
          modelProvidersByAgentId,
          httpMocks,
          newGrpcMocks,
          objectStorageBuckets,
          ephemeralPort);
    }

    @Override
    public String toString() {
      return "Settings("
          + "serviceName='"
          + serviceName
          + '\''
          + ", aclEnabled="
          + aclEnabled
          + ", eventingSupport="
          + eventingSupport
          + ", mockedEventing="
          + mockedEventing
          + ", dependencyProvider="
          + dependencyProvider
          + ')';
    }
  }

  private static final Logger log = LoggerFactory.getLogger(TestKit.class);

  /**
   * How long to wait for the runtime to report the address it bound. The runtime completes that as
   * soon as it binds or as soon as it knows it cannot, so this bound only comes into play when the
   * runtime hangs before getting that far.
   */
  private static final Duration RUNTIME_BINDING_TIMEOUT = Duration.ofSeconds(60);

  /**
   * How long to wait for the runtime to answer the dev mode startup check on the address it bound.
   */
  private static final Duration RUNTIME_STARTED_TIMEOUT = Duration.ofSeconds(10);

  private final Settings settings;

  private EventingTestKit.MessageBuilder messageBuilder;
  private boolean started = false;
  private String runtimeHost;
  private int runtimePort;
  private boolean ephemeralPort;
  private EventingTestKit eventingTestKit;
  private ActorSystem<?> runtimeActorSystem;
  private ComponentClient componentClient;

  /** Package-private on purpose: only test-internal helpers in this package may read this. */
  EventLogClient eventLogClient;

  /** Package-private on purpose: only test-internal helpers in this package may read this. */
  Serializer serializer;

  private HttpClientProvider httpClientProvider;
  private GrpcClientProviderImpl grpcClientProvider;
  private MockedHttpServicesImpl mockedHttpServices;
  private MockedGrpcServicesImpl mockedGrpcServices;
  private HttpClient selfHttpClient;
  private TimerScheduler timerScheduler;
  private Optional<DependencyProvider> dependencyProvider;
  private AgentRegistry agentRegistry;
  private Sanitizer sanitizer;
  private int eventingTestKitPort = -1;
  private Config applicationConfig;
  private String serviceName;
  private Optional<InMemorySpanExporter> inMemorySpanExporter = Optional.empty();

  /** Create a new testkit for a service descriptor with the default settings. */
  public TestKit() {
    this(Settings.DEFAULT);
  }

  /**
   * Create a new testkit for a service descriptor with custom settings.
   *
   * @param settings custom testkit settings
   */
  public TestKit(final Settings settings) {
    this.settings = settings;
  }

  /**
   * Start this testkit with default configuration. The default configuration is loaded from {@code
   * application-test.conf} if that exists, otherwise from {@code application.conf}.
   *
   * @return this TestKit instance
   */
  public TestKit start() {
    if (started) throw new IllegalStateException("Testkit already started");

    eventingTestKitPort = availableEventingTestKitPort();
    startRuntime(settings.additionalConfig);
    started = true;

    if (log.isDebugEnabled())
      log.debug("TestKit using [{}:{}] for calls to the service", runtimeHost, runtimePort);

    return this;
  }

  private void startEventingTestkit() {
    if (settings.eventingSupport == TEST_BROKER || settings.mockedEventing.hasConfig()) {
      log.info("Eventing TestKit booting up on port: {}", eventingTestKitPort);
      // actual message codec instance not available until runtime/sdk started, thus this is called
      // after discovery happens
      eventingTestKit =
          akka.javasdk.testkit.impl.EventingTestKitImpl.start(
              runtimeActorSystem,
              "0.0.0.0",
              eventingTestKitPort,
              scala.Option.apply(runtimeHost),
              scala.Option.apply((Integer) runtimePort),
              scala.Option.apply("impersonate-service"),
              scala.Option.apply("testkit"),
              new Serializer());
    }
  }

  private void startRuntime(final Config config) {
    try {
      log.debug("Config from user: {}", config);
      runtimeHost = "localhost";

      mockedHttpServices = new MockedHttpServicesImpl(settings.httpMocks);
      mockedGrpcServices = new MockedGrpcServicesImpl(settings.grpcMocks);
      SdkRunner runner =
          new SdkRunner(
              settings.dependencyProvider,
              settings.disabledComponents,
              settings.overrideDisabledComponents,
              name -> mockedHttpServices.lookup(name),
              key -> mockedGrpcServices.lookup(key)) {
            @Override
            public Config applicationConfig() {
              var userConfig = config.withFallback(super.applicationConfig());
              runtimePort = userConfig.getInt("akka.javasdk.testkit.http-port");
              // 0 in configuration asks for the same thing as the setting
              ephemeralPort = settings.ephemeralPort || runtimePort == 0;
              // There is no port to point the self client at yet, and 0 is a port no client can
              // reach, so one built from it fails instead of finding some other service. The real
              // port is written to this entry once the runtime has bound it.
              if (ephemeralPort) runtimePort = 0;

              if (settings.serviceName.isEmpty())
                serviceName = userConfig.getString("akka.javasdk.dev-mode.service-name");
              else serviceName = settings.serviceName;

              var grpcClientSelfConfigPrefix = "akka.javasdk.grpc.client." + serviceName;
              var defaultConfigMap =
                  Map.of(
                      "akka.javasdk.dev-mode.enabled",
                      true,
                      // used by the gRPC endpoint test client to call itself
                      grpcClientSelfConfigPrefix + ".host",
                      runtimeHost,
                      grpcClientSelfConfigPrefix + ".port",
                      runtimePort,
                      grpcClientSelfConfigPrefix + ".use-tls",
                      false);

              return ConfigFactory.parseMap(defaultConfigMap).withFallback(userConfig);
            }

            @Override
            public SpiSettings getSettings() {
              SpiSettings s = super.getSettings();

              SpiEventingSupportSettings eventingSettings =
                  switch (settings.eventingSupport) {
                    case TEST_BROKER ->
                        new SpiEventingSupportSettings.TestBroker(eventingTestKitPort);
                    case GOOGLE_PUBSUB ->
                        SpiEventingSupportSettings.fromConfigValue("google-pubsub-emulator");
                    case KAFKA -> SpiEventingSupportSettings.fromConfigValue("kafka");
                  };
              SpiMockedEventingSettings mockedEventingSettings =
                  SpiMockedEventingSettings.create(
                      settings.mockedEventing.mockedIncomingEvents,
                      settings.mockedEventing.mockedOutgoingEvents);

              if (s.devMode().isEmpty())
                throw new IllegalStateException(
                    "dev-mode must be enabled"); // it's set from overridden applicationConfig
              // method

              scala.collection.immutable.List<SpiDevObjectStorageBucketConfig>
                  spiObjectStorageBuckets =
                      scala.jdk.javaapi.CollectionConverters.asScala(
                              toSpiObjectStorageBuckets(settings.objectStorageBuckets))
                          .toList();

              SpiDevModeSettings devModeSettings =
                  new SpiDevModeSettings(
                      runtimePort,
                      settings.aclEnabled,
                      false,
                      serviceName + "-IT-" + System.currentTimeMillis(),
                      eventingSettings,
                      mockedEventingSettings,
                      new SpiTestSettings(true, true),
                      Some.apply(serviceName),
                      SpiBackofficeSettings$.MODULE$.empty(),
                      spiObjectStorageBuckets,
                      ephemeralPort);

              return s.withDevMode(devModeSettings);
            }
          };

      applicationConfig = runner.applicationConfig();

      Config runtimeConfig = ConfigFactory.empty();
      runtimeActorSystem = AkkaRuntimeMain.start(Some.apply(runtimeConfig), runner);
      // wait for SDK to get on start callback (or fail starting), we need it to set up the
      // component client
      final Sdk.StartupContext startupContext;
      try {
        startupContext = runner.started().toCompletableFuture().get(20, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        throw new IllegalStateException(
            "Timed out after 20 seconds waiting for the service components to start.", e);
      }
      final ComponentClients componentClients = startupContext.componentClients();
      serializer = startupContext.serializer();
      dependencyProvider =
          Optional.ofNullable(startupContext.dependencyProvider().getOrElse(() -> null));
      sanitizer = startupContext.sanitizer();

      settings.modelProvidersByAgentId.forEach(
          (agentId, modelProvider) ->
              startupContext
                  .overrideModelProvider()
                  .setModelProviderForAgent(agentId, modelProvider));

      // The runtime completes this with the address it bound, or fails it with the reason it
      // could not. It can also stay pending, when the runtime hangs before it gets as far as
      // either, so the wait is bounded. This runs on the thread that called start(), after the
      // runtime's own startup chain has returned, where awaiting cannot deadlock it.
      final int configuredPort = runtimePort;
      log.debug("Waiting for the runtime to bind its HTTP endpoint");
      final InetSocketAddress boundAddress;
      try {
        boundAddress =
            FutureConverters.asJava(startupContext.httpEndpointBound())
                .toCompletableFuture()
                .get(RUNTIME_BINDING_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // the runtime's own failure, which already names the interface and the port
        throw ErrorHandling.unwrapExecutionException(e);
      } catch (TimeoutException e) {
        throw new IllegalStateException(
            "Timed out after "
                + RUNTIME_BINDING_TIMEOUT.toSeconds()
                + " seconds waiting for the runtime to bind its HTTP endpoint on port "
                + configuredPort
                + ".",
            e);
      }

      // A configured port is used as configured. An operating system assigned one becomes a real
      // number only here. Everything that reads runtimePort runs after this.
      runtimePort = boundAddress.getPort();
      if (!ephemeralPort && configuredPort != runtimePort)
        throw new IllegalStateException(
            "Runtime was configured with port "
                + configuredPort
                + " but bound "
                + boundAddress
                + ". A configured port is always used as configured, so this should not happen.");
      log.info("Runtime bound {}", boundAddress);

      confirmRuntimeStarted(boundAddress);

      // In case of failed validations in the runtime the ActorSystem will be terminated
      if (runtimeActorSystem.whenTerminated().isCompleted())
        throw new IllegalStateException(
            "Runtime bound "
                + boundAddress
                + " and was then terminated. The reason is in the runtime log above.");

      // once runtime is started

      startEventingTestkit();

      // The runtime surfaces the in-memory tracing exporter (when the test tracing setup is active)
      // through the SPI StartupContext, so the testkit reads captured spans without referencing
      // runtime-internal telemetry types.
      this.inMemorySpanExporter =
          Optional.ofNullable(startupContext.inMemorySpanExporter().getOrElse(() -> null));

      componentClient =
          new ComponentClientImpl(
              componentClients,
              serializer,
              startupContext.agentRegistry().agentClassById(),
              Option.apply(startupContext.agentCapabilityConverter()),
              Option.empty(),
              runtimeActorSystem.executionContext(),
              runtimeActorSystem);
      agentRegistry = startupContext.agentRegistry();
      eventLogClient = startupContext.eventLogClient();
      selfHttpClient =
          new HttpClientImpl(
              runtimeActorSystem,
              "http://" + runtimeHost + ":" + runtimePort,
              runtimeActorSystem.executionContext());
      httpClientProvider = startupContext.httpClientProvider();
      grpcClientProvider = startupContext.grpcClientProvider();
      // The entry written in applicationConfig() above could not carry the port when the configured
      // port was
      // 0. gRPC clients are created lazily, so setting it here, before any test can ask for one, is
      // in time.
      // Applied unconditionally: with a configured port the value is the same one.
      grpcClientProvider.overrideClientConfigFor(serviceName, runtimeHost, runtimePort, false);
      timerScheduler = new TimerSchedulerImpl(componentClients.timerClient(), Metadata.EMPTY);
      this.messageBuilder = new EventingTestKit.MessageBuilder(serializer);

    } catch (Exception ex) {
      throw new RuntimeException("Error while starting testkit", ex);
    }
  }

  /**
   * Confirms that the runtime this testkit started is the one serving on the address it bound.
   *
   * <p>The binding tells us the socket is this runtime's, but not that the endpoint serves
   * requests. The runtime's dev mode startup check answers affirmatively only when the ActorSystem
   * uid in the request is its own, so a different runtime on the same address is reported as such
   * instead of being mistaken for this one. The expected uid is read off the ActorSystem this
   * testkit started, so nothing has to hand it out.
   *
   * <p>This is a startup confirmation only. It says nothing about the health of user components, so
   * a slow component does not look like a startup failure.
   */
  private void confirmRuntimeStarted(InetSocketAddress boundAddress)
      throws ExecutionException, InterruptedException {
    long expectedUid = ((ExtendedActorSystem) runtimeActorSystem.classicSystem()).uid();
    String url =
        "http://"
            + boundAddress.getHostString()
            + ":"
            + boundAddress.getPort()
            + Serve.DevModeStartedPath()
            + "?uid="
            + expectedUid;

    HttpResponse response;
    String body;
    try {
      response =
          Http.get(runtimeActorSystem)
              .singleRequest(HttpRequest.GET(url))
              .toCompletableFuture()
              .get(RUNTIME_STARTED_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      body =
          response
              .entity()
              .toStrict(
                  RUNTIME_STARTED_TIMEOUT.toMillis(),
                  SystemMaterializer.get(runtimeActorSystem).materializer())
              .toCompletableFuture()
              .get(RUNTIME_STARTED_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
              .getData()
              .utf8String();
    } catch (TimeoutException e) {
      throw new IllegalStateException(
          "Timed out after "
              + RUNTIME_STARTED_TIMEOUT.toSeconds()
              + " seconds waiting for the runtime at "
              + boundAddress
              + " to answer the startup check.",
          e);
    }

    if (response.status().intValue() != 200) {
      throw new IllegalStateException(
          "The runtime serving "
              + boundAddress
              + " is not the one this testkit started. Expected the runtime with ActorSystem uid ["
              + expectedUid
              + "], the startup check answered "
              + response.status().intValue()
              + ": "
              + body);
    }
  }

  /**
   * Get the host name/IP address where the service is available. This is relevant in certain
   * Continuous Integration environments.
   */
  public String getHost() {
    if (!started)
      throw new IllegalStateException("Need to start the testkit before accessing the host name");

    return runtimeHost;
  }

  /**
   * Get the local port where the service is available, for HTTP and gRPC alike.
   *
   * <p>This is the port from {@code akka.javasdk.testkit.http-port}, which is used exactly as
   * configured, unless the operating system was asked to assign one with {@link
   * Settings#withEphemeralPort()}, in which case this is the assigned port.
   *
   * <p>An assigned port cannot be named in configuration: a hand written entry that interpolates
   * {@code ${akka.javasdk.testkit.http-port}} resolves to {@code 0}, because HOCON resolves it when
   * the config is loaded, long before a port is assigned. This method is the only way to learn it.
   * The entry the testkit writes for calling the service under test over gRPC is set from the
   * assigned port and works either way.
   */
  public int getPort() {
    if (!started)
      throw new IllegalStateException("Need to start the testkit before accessing the port");

    return runtimePort;
  }

  /**
   * @return The config as the components of the service under test sees it, if injected.
   */
  public Config getApplicationConfig() {
    return applicationConfig;
  }

  /**
   * An Akka Stream materializer to use for running streams. Needed for example in a command handler
   * which accepts streaming elements but returns a single async reply once all streamed elements
   * has been consumed.
   */
  public Materializer getMaterializer() {
    return SystemMaterializer.get(getActorSystem()).materializer();
  }

  /**
   * Get an {@link ActorSystem} for creating Akka HTTP clients.
   *
   * @return test actor system
   */
  public ActorSystem<?> getActorSystem() {
    if (!started)
      throw new IllegalStateException("Need to start the testkit before accessing actor system");
    return runtimeActorSystem;
  }

  /**
   * Get an {@link ComponentClient} for interacting "internally" with the components of a service.
   */
  public ComponentClient getComponentClient() {
    return componentClient;
  }

  /** Get a {@link TimerScheduler} for scheduling TimedAction. */
  public TimerScheduler getTimerScheduler() {
    return timerScheduler;
  }

  /**
   * Get a {@link HttpClientProvider} for looking up HTTP clients to interact with other services
   * than the current. Requests will appear as coming from this service from an ACL perspective.
   */
  public HttpClientProvider getHttpClientProvider() {
    return httpClientProvider;
  }

  /**
   * Get a {@link GrpcClientProvider} for looking up gRPC clients to interact with services other
   * than the current. Requests will appear as coming from this service from an ACL perspective.
   */
  public GrpcClientProvider getGrpcClientProvider() {
    return grpcClientProvider;
  }

  /**
   * Registry for managing HTTP service mocks on the running testkit. Lets individual tests install
   * or replace handlers without re-creating the testkit; call {@link MockedHttpServices#reset()} in
   * an {@code @AfterEach} to restore the mocks declared via {@link
   * Settings#withMockedHttpService(String, java.util.function.Function)}.
   */
  public MockedHttpServices getMockedHttpServices() {
    return mockedHttpServices;
  }

  /**
   * Registry for managing gRPC service mocks on the running testkit. Lets individual tests install
   * or replace mock instances without re-creating the testkit; call {@link
   * MockedGrpcServices#reset()} in an {@code @AfterEach} to restore the mocks declared via {@link
   * Settings#withMockedGrpcService(String, Class, AkkaGrpcClient)}.
   */
  public MockedGrpcServices getMockedGrpcServices() {
    return mockedGrpcServices;
  }

  /**
   * Get a gRPC client for an endpoint provided by this service. Requests will appear as coming from
   * this service itself from an ACL perspective.
   *
   * @param grpcClientClass The generated Akka gRPC client interface for a gRPC endpoint in this
   *     service
   */
  public <T extends AkkaGrpcClient> T getGrpcEndpointClient(Class<T> grpcClientClass) {
    return grpcClientProvider.grpcClientFor(grpcClientClass, serviceName);
  }

  /**
   * Get a gRPC client for an endpoint provided by this service but specify the client principal for
   * the ACLs.
   *
   * @param grpcClientClass The generated Akka gRPC client interface for a gRPC endpoint in this
   *     service
   * @param requestPrincipal A principal that any request from the returned service will have when
   *     requests are handled in the endpoint.
   */
  @SuppressWarnings("unchecked")
  public <T extends AkkaGrpcClient> T getGrpcEndpointClient(
      Class<T> grpcClientClass, Principal requestPrincipal) {
    var client = grpcClientProvider.createNewClientFor(grpcClientClass, serviceName);
    if (requestPrincipal == Principal.SELF) {
      // no need to use this method, but let's allow it
      return getGrpcEndpointClient(grpcClientClass);
    } else if (requestPrincipal instanceof Principal.LocalService service) {
      return (T) client.addRequestHeader("impersonate-service", service.getName());
    } else if (requestPrincipal == Principal.INTERNET) {
      // no principal / as from internet
      return client;
    } else {
      throw new IllegalArgumentException(
          "Specified principal [" + requestPrincipal + "] not supported by testkit");
    }
  }

  // FIXME do we need a "get client with this principal" kind of method? Not sure that is useful
  // (unlike in Kalix SDKs)

  /**
   * Get a {@link HttpClient} for interacting with the service itself, the client will not be
   * authenticated and will appear to the service as a request with the internet principal.
   */
  public HttpClient getSelfHttpClient() {
    return selfHttpClient;
  }

  public SseRouteTester getSelfSseRouteTester() {
    return new SseRouteTesterImpl(runtimeHost, runtimePort, runtimeActorSystem);
  }

  public WebSocketRouteTester getSelfWebSocketRouteTester() {
    return new WebSocketRouteTesterImpl(runtimeHost, runtimePort, runtimeActorSystem);
  }

  public InMemorySpanExporter getInMemorySpanExporter() {
    return inMemorySpanExporter.orElseThrow(
        () ->
            new IllegalStateException(
                "No in-memory span exporter configured. Tracing may not be enabled."));
  }

  /** Get incoming messages for KeyValueEntity. */
  public IncomingMessages getKeyValueEntityIncomingMessages(
      Class<? extends KeyValueEntity<?>> keyValueEntityClass) {
    String componentId = getComponentId(keyValueEntityClass);
    if (!settings.mockedEventing.hasKeyValueEntitySubscription(componentId)) {
      throwMissingConfigurationException("KeyValueEntity " + componentId);
    }
    return eventingTestKit.getKeyValueEntityIncomingMessages(componentId);
  }

  /** Get incoming messages for EventSourcedEntity. */
  public IncomingMessages getEventSourcedEntityIncomingMessages(
      Class<? extends EventSourcedEntity<?, ?>> eventSourcedEntityClass) {
    String componentId = getComponentId(eventSourcedEntityClass);
    if (!settings.mockedEventing.hasEventSourcedEntitySubscription(componentId)) {
      throwMissingConfigurationException("EventSourcedEntity " + componentId);
    }
    return eventingTestKit.getEventSourcedEntityIncomingMessages(componentId);
  }

  private static List<SpiDevObjectStorageBucketConfig> toSpiObjectStorageBuckets(
      List<ObjectStorageBucketConfig> buckets) {
    List<SpiDevObjectStorageBucketConfig> result = new ArrayList<>(buckets.size());
    for (ObjectStorageBucketConfig bucket : buckets) {
      if (bucket instanceof ObjectStorageBucketConfig.Impl.Filesystem) {
        ObjectStorageBucketConfig.Impl.Filesystem fs =
            (ObjectStorageBucketConfig.Impl.Filesystem) bucket;
        scala.Option<String> scalaDir = scala.Option.apply(fs.directory.orElse(null));
        result.add(new SpiDevObjectStorageFilesystemBucketConfig(fs.name, scalaDir));
      } else if (bucket instanceof ObjectStorageBucketConfig.Impl.S3) {
        ObjectStorageBucketConfig.Impl.S3 s3 = (ObjectStorageBucketConfig.Impl.S3) bucket;
        SpiDevObjectStorageS3Credentials creds;
        if (s3.credentials instanceof ObjectStorageBucketConfig.S3StaticCredentials) {
          ObjectStorageBucketConfig.S3StaticCredentials sc =
              (ObjectStorageBucketConfig.S3StaticCredentials) s3.credentials;
          creds = new SpiDevObjectStorageS3StaticCredentials(sc.accessKeyId, sc.secretAccessKey);
        } else if (s3.credentials instanceof ObjectStorageBucketConfig.S3ProfileCredentials) {
          ObjectStorageBucketConfig.S3ProfileCredentials pc =
              (ObjectStorageBucketConfig.S3ProfileCredentials) s3.credentials;
          creds = new SpiDevObjectStorageS3ProfileCredentials(pc.profileName);
        } else {
          creds = SpiDevObjectStorageS3NativeCredentials$.MODULE$;
        }
        result.add(new SpiDevObjectStorageS3BucketConfig(s3.name, s3.bucket, s3.region, creds));
      } else if (bucket instanceof ObjectStorageBucketConfig.Impl.Gcs) {
        ObjectStorageBucketConfig.Impl.Gcs gcs = (ObjectStorageBucketConfig.Impl.Gcs) bucket;
        SpiDevObjectStorageGcsCredentials creds;
        if (gcs.credentials instanceof ObjectStorageBucketConfig.GcsServiceAccountKeyCredentials) {
          ObjectStorageBucketConfig.GcsServiceAccountKeyCredentials sak =
              (ObjectStorageBucketConfig.GcsServiceAccountKeyCredentials) gcs.credentials;
          creds = new SpiDevObjectStorageGcsServiceAccountKeyCredentials(sak.path);
        } else {
          creds = SpiDevObjectStorageGcsNativeCredentials$.MODULE$;
        }
        result.add(new SpiDevObjectStorageGcsBucketConfig(gcs.name, gcs.bucket, creds));
      } else {
        throw new IllegalArgumentException(
            "Unknown ObjectStorageBucketConfig type: " + bucket.getClass());
      }
    }
    return result;
  }

  @SuppressWarnings("deprecation")
  private static String getComponentId(Class<?> componentClass) {
    Component componentAnnotation = componentClass.getAnnotation(Component.class);
    if (componentAnnotation != null) {
      return componentAnnotation.id();
    }

    throw new IllegalArgumentException(
        "Component [" + componentClass + "] is missing @Component annotation");
  }

  /**
   * Utility method to get the component ID from a component class.
   *
   * @param componentClass the component class annotated with @Component
   * @return the component ID
   */
  public static String getComponentIdValue(Class<?> componentClass) {
    return getComponentId(componentClass);
  }

  /**
   * Utility method to get the component name from a component class.
   *
   * @param componentClass the component class annotated with @Component
   * @return the component name, or null if not specified or using @ComponentId
   */
  public static String getComponentName(Class<?> componentClass) {
    Component componentAnnotation = componentClass.getAnnotation(Component.class);
    if (componentAnnotation != null && !componentAnnotation.name().isEmpty()) {
      return componentAnnotation.name();
    }
    return null;
  }

  /**
   * Utility method to get the component description from a component class.
   *
   * @param componentClass the component class annotated with @Component
   * @return the component description, or null if not specified or using @ComponentId
   */
  public static String getComponentDescription(Class<?> componentClass) {
    Component componentAnnotation = componentClass.getAnnotation(Component.class);
    if (componentAnnotation != null && !componentAnnotation.description().isEmpty()) {
      return componentAnnotation.description();
    }
    return null;
  }

  /** Get incoming messages for Workflow. */
  public IncomingMessages getWorkflowIncomingMessages(Class<? extends Workflow<?>> workflowClass) {
    String componentId = getComponentId(workflowClass);
    if (!settings.mockedEventing.hasWorkflowSubscription(componentId)) {
      throwMissingConfigurationException("Workflow " + componentId);
    }
    return eventingTestKit.getWorkflowIncomingMessages(componentId);
  }

  /**
   * Get incoming messages for Consume.ServiceStream.
   *
   * @param service service name
   * @param streamId service stream id
   */
  public IncomingMessages getStreamIncomingMessages(String service, String streamId) {
    if (!settings.mockedEventing.hasStreamSubscription(service, streamId)) {
      throwMissingConfigurationException("Stream " + service + "/" + streamId);
    }
    return eventingTestKit.getStreamIncomingMessages(service, streamId);
  }

  /**
   * Get incoming messages for Topic.
   *
   * @param topic topic name
   */
  public IncomingMessages getTopicIncomingMessages(String topic) {
    if (!settings.mockedEventing.hasTopicSubscription(topic)) {
      throwMissingConfigurationException("Topic " + topic);
    }
    return eventingTestKit.getTopicIncomingMessages(topic);
  }

  /**
   * Get mocked topic destination.
   *
   * @param topic topic name
   */
  public EventingTestKit.OutgoingMessages getTopicOutgoingMessages(String topic) {
    if (!settings.mockedEventing.hasTopicDestination(topic)) {
      throwMissingConfigurationException("Topic " + topic);
    }
    return eventingTestKit.getTopicOutgoingMessages(topic);
  }

  /**
   * Get outgoing messages produced by a {@code @Produce.ServiceStream} producer in the service
   * under test. The {@code service} must match the service name used by consumers in their
   * {@code @Consume.FromServiceStream} annotation.
   *
   * <p>Note: the {@code service} value is used by the testkit as a lookup key (and must match what
   * was registered via {@link Settings#withStreamOutgoingMessages(String, String)}); the underlying
   * subscription itself is resolved against the service-under-test by {@code streamId}.
   *
   * <p>Each call returns a handle whose subscription replays events from the beginning of the
   * stream. In a suite that shares a single {@code TestKit} across multiple tests, call {@link
   * EventingTestKit.OutgoingMessages#clear()} at the start of each test to drop events produced by
   * prior tests.
   *
   * @param service service name
   * @param streamId service stream id
   */
  public EventingTestKit.OutgoingMessages getStreamOutgoingMessages(
      String service, String streamId) {
    if (!settings.mockedEventing.hasStreamDestination(service, streamId)) {
      throwMissingConfigurationException("Stream " + service + "/" + streamId);
    }
    return eventingTestKit.getStreamOutgoingMessages(service, streamId);
  }

  private void throwMissingConfigurationException(String hint) {
    throw new IllegalStateException(
        "Currently configured mocked eventing is ["
            + settings.mockedEventing
            + "]. To use the MockedEventing API, to configure mocking of "
            + hint);
  }

  public AgentRegistry getAgentRegistry() {
    return agentRegistry;
  }

  /**
   * @return The configured sanitizer for the service, for test assertions that the expected
   *     anonymization is applied. Will always return an instance, if no sanitization rules are
   *     configured, the returned sanitizer will return all text fed to it as is.
   */
  public Sanitizer getSanitizer() {
    return sanitizer;
  }

  /** Stop the testkit and local runtime. */
  public void stop() {
    try {
      if (runtimeActorSystem != null) {
        akka.testkit.javadsl.TestKit.shutdownActorSystem(
            runtimeActorSystem.classicSystem(), FiniteDuration.create(10, TimeUnit.SECONDS), true);
      }
    } catch (Exception e) {
      log.error("TestKit runtime failed to terminate", e);
    }
    started = false;
  }

  /**
   * Get an available local port for testing.
   *
   * @return available local port
   */
  public static int availableLocalPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Couldn't get available local port", e);
    }
  }

  /**
   * A band below the range operating systems draw ephemeral ports from: 32768 and up on Linux,
   * 49152 and up on macOS and Windows.
   */
  private static final int EVENTING_PORT_BAND_FIRST = 20000;

  private static final int EVENTING_PORT_BAND_LAST = 32767;

  private static final int EVENTING_PORT_ATTEMPTS = 50;

  /**
   * The eventing testkit port is chosen when the testkit starts but only bound once the runtime has
   * booted, and the runtime opens plenty of outbound connections in between. A port from the
   * ephemeral range can be taken for one of those in that gap, so the port is drawn from a band
   * below that range instead. This narrows the window rather than closing it: the port is handed to
   * the runtime through the settings long before anything binds it.
   */
  private static int availableEventingTestKitPort() {
    for (int attempt = 0; attempt < EVENTING_PORT_ATTEMPTS; attempt++) {
      int port =
          ThreadLocalRandom.current()
              .nextInt(EVENTING_PORT_BAND_FIRST, EVENTING_PORT_BAND_LAST + 1);
      // bound on the wildcard address, the same as the eventing testkit binds it on
      try (ServerSocket socket = new ServerSocket()) {
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        return port;
      } catch (IOException taken) {
        // try another one
      }
    }
    log.warn(
        "Found no free port in [{}-{}] for the eventing testkit, asking for any free port instead",
        EVENTING_PORT_BAND_FIRST,
        EVENTING_PORT_BAND_LAST);
    return availableLocalPort();
  }

  /**
   * Returns {@link EventingTestKit.MessageBuilder} utility to create {@link
   * EventingTestKit.Message}s for the eventing testkit.
   */
  public EventingTestKit.MessageBuilder getMessageBuilder() {
    return messageBuilder;
  }

  /**
   * @return The custom dependency provider used in this test, if one is defined, when overriding
   *     the dependency provided through {@link Settings#withDependencyProvider(DependencyProvider)}
   *     the overridden provider is returned.
   */
  public Optional<DependencyProvider> getDependencyProvider() {
    return dependencyProvider;
  }
}

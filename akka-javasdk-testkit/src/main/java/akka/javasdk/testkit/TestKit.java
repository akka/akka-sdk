/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static akka.javasdk.testkit.TestKit.Settings.EventingSupport.TEST_BROKER;

import akka.actor.typed.ActorSystem;
import akka.grpc.javadsl.AkkaGrpcClient;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.javasdk.DependencyProvider;
import akka.javasdk.Metadata;
import akka.javasdk.Principal;
import akka.javasdk.Sanitizer;
import akka.javasdk.ServiceSetup;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.impl.ErrorHandling;
import akka.javasdk.impl.Sdk;
import akka.javasdk.impl.SdkRunner;
import akka.javasdk.impl.client.ComponentClientImpl;
import akka.javasdk.impl.grpc.GrpcClientProviderImpl;
import akka.javasdk.impl.http.HttpClientImpl;
import akka.javasdk.impl.serialization.JsonSerializer;
import akka.javasdk.impl.timer.TimerSchedulerImpl;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.timer.TimerScheduler;
import akka.javasdk.workflow.Workflow;
import akka.pattern.Patterns;
import akka.runtime.sdk.spi.ComponentClients;
import akka.runtime.sdk.spi.SpiDevModeSettings;
import akka.runtime.sdk.spi.SpiEventingSupportSettings;
import akka.runtime.sdk.spi.SpiMockedEventingSettings;
import akka.runtime.sdk.spi.SpiSettings;
import akka.runtime.sdk.spi.SpiTestSettings;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import kalix.runtime.AkkaRuntimeMain;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.Some;
import scala.concurrent.duration.FiniteDuration;

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
            new HashMap<>());

    /** The name of this service when deployed. */
    public final String serviceName;

    /** Whether ACL checking is enabled. */
    public final boolean aclEnabled;

    public final EventingSupport eventingSupport;

    public final MockedEventing mockedEventing;

    public final Config additionalConfig;

    public final Optional<DependencyProvider> dependencyProvider;

    public final Set<Class<?>> disabledComponents;

    public final Map<String, ModelProvider> modelProvidersByAgentId;

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
        Map<String, ModelProvider> modelProvidersByAgentId) {
      this.serviceName = serviceName;
      this.aclEnabled = aclEnabled;
      this.eventingSupport = eventingSupport;
      this.mockedEventing = mockedEventing;
      this.dependencyProvider = dependencyProvider;
      this.additionalConfig = additionalConfig;
      this.disabledComponents = disabledComponents;
      this.modelProvidersByAgentId = modelProvidersByAgentId;
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
          modelProvidersByAgentId);
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
          modelProvidersByAgentId);
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
          modelProvidersByAgentId);
    }

    /**
     * Mock the incoming messages flow from a KeyValueEntity.
     *
     * @deprecated use {@link #withKeyValueEntityIncomingMessages(Class)} instead.
     */
    @Deprecated(since = "3.4.2", forRemoval = true)
    public Settings withKeyValueEntityIncomingMessages(String componentId) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withKeyValueEntityIncomingMessages(componentId),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          modelProvidersByAgentId);
    }

    public Settings withKeyValueEntityIncomingMessages(
        Class<? extends KeyValueEntity<?>> keyValueEntityClass) {
      String componentId = getComponentId(keyValueEntityClass);
      return withKeyValueEntityIncomingMessages(componentId);
    }

    /**
     * Mock the incoming events flow from an EventSourcedEntity.
     *
     * @deprecated use {@link #withEventSourcedEntityIncomingMessages(Class)} instead.
     */
    @Deprecated(since = "3.4.2", forRemoval = true)
    public Settings withEventSourcedEntityIncomingMessages(String componentId) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withEventSourcedIncomingMessages(componentId),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          modelProvidersByAgentId);
    }

    public Settings withEventSourcedEntityIncomingMessages(
        Class<? extends EventSourcedEntity<?, ?>> eventSourcedEntityClass) {
      String componentId = getComponentId(eventSourcedEntityClass);
      return withEventSourcedEntityIncomingMessages(componentId);
    }

    /**
     * Mock the incoming state updates flow from a Workflow.
     *
     * @deprecated use {@link #withWorkflowIncomingMessages(Class)} instead.
     */
    @Deprecated(since = "3.4.2", forRemoval = true)
    public Settings withWorkflowIncomingMessages(String componentId) {
      return new Settings(
          serviceName,
          aclEnabled,
          eventingSupport,
          mockedEventing.withWorkflowIncomingMessages(componentId),
          dependencyProvider,
          additionalConfig,
          disabledComponents,
          modelProvidersByAgentId);
    }

    public Settings withWorkflowIncomingMessages(Class<? extends Workflow<?>> workflowClass) {
      String componentId = getComponentId(workflowClass);
      return withWorkflowIncomingMessages(componentId);
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
          modelProvidersByAgentId);
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
          modelProvidersByAgentId);
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
          modelProvidersByAgentId);
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
          modelProvidersByAgentId);
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
          modelProvidersByAgentId);
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
          modelProvidersByAgentId);
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
          modelProvidersByAgentId);
    }

    public Settings withModelProvider(
        Class<? extends Agent> agentClass, ModelProvider modelProvider) {
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
          newModelProvidersByAgentId);
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

  private final Settings settings;

  private EventingTestKit.MessageBuilder messageBuilder;
  private boolean started = false;
  private String runtimeHost;
  private int runtimePort;
  private EventingTestKit eventingTestKit;
  private ActorSystem<?> runtimeActorSystem;
  private ComponentClient componentClient;
  private HttpClientProvider httpClientProvider;
  private GrpcClientProviderImpl grpcClientProvider;
  private HttpClient selfHttpClient;
  private TimerScheduler timerScheduler;
  private Optional<DependencyProvider> dependencyProvider;
  private AgentRegistry agentRegistry;
  private Sanitizer sanitizer;
  private int eventingTestKitPort = -1;
  private Config applicationConfig;
  private String serviceName;

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

    eventingTestKitPort = availableLocalPort();
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
          EventingTestKit.start(
              runtimeActorSystem, "0.0.0.0", eventingTestKitPort, new JsonSerializer());
    }
  }

  private void startRuntime(final Config config) {
    try {
      log.debug("Config from user: {}", config);
      runtimeHost = "localhost";

      SdkRunner runner =
          new SdkRunner(settings.dependencyProvider, settings.disabledComponents) {
            @Override
            public Config applicationConfig() {
              var userConfig = config.withFallback(super.applicationConfig());
              runtimePort = userConfig.getInt("akka.javasdk.testkit.http-port");

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

              SpiDevModeSettings devModeSettings =
                  new SpiDevModeSettings(
                      runtimePort,
                      settings.aclEnabled,
                      false,
                      serviceName + "-IT-" + System.currentTimeMillis(),
                      eventingSettings,
                      mockedEventingSettings,
                      new SpiTestSettings(true, false),
                      Some.apply(serviceName));

              return s.withDevMode(devModeSettings);
            }
          };

      applicationConfig = runner.applicationConfig();

      Config runtimeConfig = ConfigFactory.empty();
      runtimeActorSystem = AkkaRuntimeMain.start(Some.apply(runtimeConfig), runner);
      // wait for SDK to get on start callback (or fail starting), we need it to set up the
      // component client
      final Sdk.StartupContext startupContext =
          runner.started().toCompletableFuture().get(20, TimeUnit.SECONDS);
      final ComponentClients componentClients = startupContext.componentClients();
      final JsonSerializer serializer = startupContext.serializer();
      dependencyProvider =
          Optional.ofNullable(startupContext.dependencyProvider().getOrElse(() -> null));
      sanitizer = startupContext.sanitizer();

      settings.modelProvidersByAgentId.forEach(
          (agentId, modelProvider) ->
              startupContext
                  .overrideModelProvider()
                  .setModelProviderForAgent(agentId, modelProvider));

      startEventingTestkit();

      Http http = Http.get(runtimeActorSystem);
      log.info("Checking runtime status");
      CompletionStage<String> checkingProxyStatus =
          Patterns.retry(
              () ->
                  http.singleRequest(
                          HttpRequest.GET(
                              "http://"
                                  + runtimeHost
                                  + ":"
                                  + runtimePort
                                  + "/akka/dev-mode/health-check"))
                      .thenCompose(
                          response -> {
                            int responseCode = response.status().intValue();
                            if (responseCode == 404) {
                              log.info("Runtime started");
                              return CompletableFuture.completedStage("Ok");
                            } else {
                              log.info(
                                  "Waiting for runtime, current response code is {}", responseCode);
                              return CompletableFuture.failedFuture(
                                  new IllegalStateException("Runtime not started."));
                            }
                          })
                      .exceptionally(
                          ex -> {
                            log.error("Failed to connect to runtime:", ex);
                            throw new IllegalStateException("Runtime not started.", ex);
                          }),
              10,
              Duration.ofSeconds(1),
              runtimeActorSystem);

      try {
        CompletableFuture.anyOf(
                checkingProxyStatus.toCompletableFuture(),
                runtimeActorSystem.getWhenTerminated().toCompletableFuture())
            .get(60, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        RuntimeException cause = ErrorHandling.unwrapExecutionException(e);
        log.error("Failed to connect to Runtime with:", cause);
        throw cause;
      } catch (InterruptedException | TimeoutException e) {
        log.error("Failed to connect to Runtime with:", e);
        throw new RuntimeException(e);
      }

      // In case of failed validations in the runtime the ActorSystem will be terminated
      if (runtimeActorSystem.whenTerminated().isCompleted())
        throw new IllegalStateException("Runtime was terminated.");

      // once runtime is started

      componentClient =
          new ComponentClientImpl(
              componentClients,
              serializer,
              startupContext.agentRegistry().agentClassById(),
              Option.empty(),
              runtimeActorSystem.executionContext(),
              runtimeActorSystem);
      agentRegistry = startupContext.agentRegistry();
      selfHttpClient =
          new HttpClientImpl(runtimeActorSystem, "http://" + runtimeHost + ":" + runtimePort);
      httpClientProvider = startupContext.httpClientProvider();
      grpcClientProvider = startupContext.grpcClientProvider();
      timerScheduler = new TimerSchedulerImpl(componentClients.timerClient(), Metadata.EMPTY);
      this.messageBuilder = new EventingTestKit.MessageBuilder(serializer);

    } catch (Exception ex) {
      throw new RuntimeException("Error while starting testkit", ex);
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

  /** Get the local port where the service is available. */
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
    var client = grpcClientProvider.createNewClientFor(grpcClientClass, serviceName, false);
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

  /**
   * Get incoming messages for KeyValueEntity.
   *
   * @param componentId As annotated with @Component on the KeyValueEntity
   * @deprecated use {@link #getKeyValueEntityIncomingMessages(Class)} instead.
   */
  @Deprecated(since = "3.4.2", forRemoval = true)
  public IncomingMessages getKeyValueEntityIncomingMessages(String componentId) {
    if (!settings.mockedEventing.hasKeyValueEntitySubscription(componentId)) {
      throwMissingConfigurationException("KeyValueEntity " + componentId);
    }
    return eventingTestKit.getKeyValueEntityIncomingMessages(componentId);
  }

  public IncomingMessages getKeyValueEntityIncomingMessages(
      Class<? extends KeyValueEntity<?>> keyValueEntityClass) {
    String componentId = getComponentId(keyValueEntityClass);
    return getKeyValueEntityIncomingMessages(componentId);
  }

  /**
   * Get incoming messages for EventSourcedEntity.
   *
   * @param componentId As annotated with @Component on the EventSourcedEntity
   * @deprecated use {@link #getEventSourcedEntityIncomingMessages(Class)} instead.
   */
  @Deprecated(since = "3.4.2", forRemoval = true)
  public IncomingMessages getEventSourcedEntityIncomingMessages(String componentId) {
    if (!settings.mockedEventing.hasEventSourcedEntitySubscription(componentId)) {
      throwMissingConfigurationException("EventSourcedEntity " + componentId);
    }
    return eventingTestKit.getEventSourcedEntityIncomingMessages(componentId);
  }

  public IncomingMessages getEventSourcedEntityIncomingMessages(
      Class<? extends EventSourcedEntity<?, ?>> eventSourcedEntityClass) {
    String componentId = getComponentId(eventSourcedEntityClass);
    return getEventSourcedEntityIncomingMessages(componentId);
  }

  @SuppressWarnings("deprecation")
  private static String getComponentId(Class<?> componentClass) {
    // First check for the new Component annotation
    Component componentAnnotation = componentClass.getAnnotation(Component.class);
    if (componentAnnotation != null) {
      return componentAnnotation.id();
    }

    // Fallback to the old ComponentId annotation for backward compatibility
    ComponentId componentIdAnnotation = componentClass.getAnnotation(ComponentId.class);
    if (componentIdAnnotation != null) {
      return componentIdAnnotation.value();
    }

    throw new IllegalArgumentException(
        "Component [" + componentClass + "] is missing @Component or @ComponentId annotation");
  }

  /**
   * Utility method to get the component ID from a component class.
   *
   * @param componentClass the component class annotated with @Component or @ComponentId
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

  /**
   * Get incoming messages for Workflow.
   *
   * @param componentId As annotated with @Component on the EventSourcedEntity
   * @deprecated use {@link #getWorkflowIncomingMessages(Class)} instead.
   */
  @Deprecated(since = "3.4.2", forRemoval = true)
  public IncomingMessages getWorkflowIncomingMessages(String componentId) {
    if (!settings.mockedEventing.hasWorkflowSubscription(componentId)) {
      throwMissingConfigurationException("Workflow " + componentId);
    }
    return eventingTestKit.getWorkflowIncomingMessages(componentId);
  }

  public IncomingMessages getWorkflowIncomingMessages(Class<? extends Workflow<?>> workflowClass) {
    String componentId = getComponentId(workflowClass);
    return getWorkflowIncomingMessages(componentId);
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

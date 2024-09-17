/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.actor.typed.ActorSystem;
import akka.annotation.InternalApi;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.javasdk.DependencyProvider;
import akka.javasdk.Metadata;
import akka.javasdk.Principal;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.impl.ApplicationConfig;
import akka.javasdk.impl.GrpcClients;
import akka.javasdk.impl.JsonMessageCodec;
import akka.javasdk.impl.MessageCodec;
import akka.javasdk.impl.ProxyInfoHolder;
import akka.javasdk.impl.SdkRunner;
import akka.javasdk.impl.client.ComponentClientImpl;
import akka.javasdk.impl.timer.TimerSchedulerImpl;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.timer.TimerScheduler;
import akka.pattern.Patterns;
import akka.runtime.sdk.spi.SpiDevModeSettings;
import akka.runtime.sdk.spi.SpiEventingSupportSettings;
import akka.runtime.sdk.spi.SpiMockedEventingSettings;
import akka.runtime.sdk.spi.SpiSettings;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import kalix.runtime.KalixRuntimeMain;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.Some;
import scala.concurrent.duration.FiniteDuration;

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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static akka.javasdk.testkit.TestKit.Settings.EventingSupport.TEST_BROKER;

/**
 * Testkit for running services locally.
 *
 * <p>Requires Docker for starting a local instance of the runtime.
 *
 * <p>Create a AkkaSdkTestkit and then {@link #start} the
 * testkit before testing the service with HTTP clients. Call {@link #stop} after tests are
 * complete.
 */
public class TestKit {

  public static class MockedEventing {
    public static final String KEY_VALUE_ENTITY = "key-value-entity";
    public static final String EVENT_SOURCED_ENTITY = "event-sourced-entity";
    public static final String STREAM = "stream";
    public static final String TOPIC = "topic";
    private final Map<String, Set<String>> mockedIncomingEvents; //Subscriptions
    private final Map<String, Set<String>> mockedOutgoingEvents; //Destination

    private MockedEventing() {
      this(new HashMap<>(), new HashMap<>());
    }

    private MockedEventing(Map<String, Set<String>> mockedIncomingEvents, Map<String, Set<String>> mockedOutgoingEvents) {
      this.mockedIncomingEvents = mockedIncomingEvents;
      this.mockedOutgoingEvents = mockedOutgoingEvents;
    }

    public static MockedEventing EMPTY = new MockedEventing();

    public MockedEventing withKeyValueEntityIncomingMessages(String typeId) {
      Map<String, Set<String>> copy = new HashMap<>(mockedIncomingEvents);
      copy.compute(KEY_VALUE_ENTITY, updateValues(typeId));
      return new MockedEventing(copy, new HashMap<>(mockedOutgoingEvents));
    }

    public MockedEventing withEventSourcedIncomingMessages(String typeId) {
      Map<String, Set<String>> copy = new HashMap<>(mockedIncomingEvents);
      copy.compute(EVENT_SOURCED_ENTITY, updateValues(typeId));
      return new MockedEventing(copy, new HashMap<>(mockedOutgoingEvents));
    }

    public MockedEventing withStreamIncomingMessages(String service, String streamId) {
      Map<String, Set<String>> copy = new HashMap<>(mockedIncomingEvents);
      copy.compute(STREAM, updateValues(service + "/" + streamId));
      return new MockedEventing(copy, new HashMap<>(mockedOutgoingEvents));
    }

    public MockedEventing withTopicIncomingMessages(String topic) {
      Map<String, Set<String>> copy = new HashMap<>(mockedIncomingEvents);
      copy.compute(TOPIC, updateValues(topic));
      return new MockedEventing(copy, new HashMap<>(mockedOutgoingEvents));
    }

    public MockedEventing withTopicOutgoingMessages(String topic) {
      Map<String, Set<String>> copy = new HashMap<>(mockedOutgoingEvents);
      copy.compute(TOPIC, updateValues(topic));
      return new MockedEventing(new HashMap<>(mockedIncomingEvents), copy);
    }

    @NotNull
    private BiFunction<String, Set<String>, Set<String>> updateValues(String typeId) {
      return (key, currentValues) -> {
        if (currentValues == null) {
          LinkedHashSet<String> values = new LinkedHashSet<>(); //order is relevant only for tests
          values.add(typeId);
          return values;
        } else {
          currentValues.add(typeId);
          return currentValues;
        }
      };
    }

    @Override
    public String toString() {
      return "MockedEventing{" +
          "mockedIncomingEvents=" + mockedIncomingEvents +
          ", mockedOutgoingEvents=" + mockedOutgoingEvents +
          '}';
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
          .flatMap(entry -> {
            String subscriptionType = entry.getKey();
            return entry.getValue().stream().map(name -> subscriptionType + "," + name);
          }).collect(Collectors.joining(";"));
    }

    boolean hasKeyValueEntitySubscription(String typeId) {
      return checkExistence(KEY_VALUE_ENTITY, typeId);
    }

    boolean hasEventSourcedEntitySubscription(String typeId) {
      return checkExistence(EVENT_SOURCED_ENTITY, typeId);
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

  /**
   * Settings for testkit.
   */
  public static class Settings {
    /**
     * Default settings for testkit.
     */
    public static Settings DEFAULT = new Settings("self", true, TEST_BROKER, MockedEventing.EMPTY, ConfigFactory.empty());

    /**
     * The name of this service when deployed.
     */
    public final String serviceName;

    /**
     * Whether ACL checking is enabled.
     */
    public final boolean aclEnabled;

    public final EventingSupport eventingSupport;

    public final MockedEventing mockedEventing;

    public final Config additionalConfig;

    public enum EventingSupport {
      /**
       * This is the default type used and allows the testing eventing integrations without an external broker dependency
       * running.
       */
      TEST_BROKER,

      /**
       * Used if you want to use an external Google PubSub (or its Emulator) on your tests.
       * <p>
       * Note: the Google PubSub broker instance needs to be started independently.
       */
      GOOGLE_PUBSUB,

      /**
       * Used if you want to use an external Kafka broker on your tests.
       * <p>
       * Note: the Kafka broker instance needs to be started independently.
       */
      KAFKA
    }

    private Settings(
        final String serviceName,
        final boolean aclEnabled,
        final EventingSupport eventingSupport,
        final MockedEventing mockedEventing,
        Config additionalConfig
      ) {
      this.serviceName = serviceName;
      this.aclEnabled = aclEnabled;
      this.eventingSupport = eventingSupport;
      this.mockedEventing = mockedEventing;
      this.additionalConfig = additionalConfig;
    }

    /**
     * Set the name of this service. This will be used by the service when making calls on other
     * services run by the testkit to authenticate itself, allowing those services to apply ACLs
     * based on that name.
     *
     * @param serviceName The name of this service.
     * @return The updated settings.
     */
    public Settings withServiceName(final String serviceName) {
      return new Settings(serviceName, aclEnabled, eventingSupport, mockedEventing, additionalConfig);
    }

    /**
     * Disable ACL checking in this service.
     *
     * @return The updated settings.
     */
    public Settings withAclDisabled() {
      return new Settings(serviceName, false, eventingSupport, mockedEventing, additionalConfig);
    }

    /**
     * Enable ACL checking in this service (this is the default).
     *
     * @return The updated settings.
     */
    public Settings withAclEnabled() {
      return new Settings(serviceName, true, eventingSupport, mockedEventing, additionalConfig);
    }

    /**
     * Mock the incoming messages flow from a KeyValueEntity.
     */
    public Settings withKeyValueEntityIncomingMessages(String typeId) {
      return new Settings(serviceName, aclEnabled, eventingSupport,
          mockedEventing.withKeyValueEntityIncomingMessages(typeId), additionalConfig);
    }

    /**
     * Mock the incoming events flow from an EventSourcedEntity.
     */
    public Settings withEventSourcedEntityIncomingMessages(String typeId) {
      return new Settings(serviceName, aclEnabled, eventingSupport,
          mockedEventing.withEventSourcedIncomingMessages(typeId), additionalConfig);
    }

    /**
     * Mock the incoming messages flow from a Stream (eventing.in.direct in case of protobuf SDKs).
     */
    public Settings withStreamIncomingMessages(String service, String streamId) {
      return new Settings(serviceName, aclEnabled, eventingSupport,
          mockedEventing.withStreamIncomingMessages(service, streamId), additionalConfig);
    }

    /**
     * Mock the incoming events flow from a Topic.
     */
    public Settings withTopicIncomingMessages(String topic) {
      return new Settings(serviceName, aclEnabled, eventingSupport,
          mockedEventing.withTopicIncomingMessages(topic), additionalConfig);
    }

    /**
     * Mock the outgoing events flow for a Topic.
     */
    public Settings withTopicOutgoingMessages(String topic) {
      return new Settings(serviceName, aclEnabled, eventingSupport,
          mockedEventing.withTopicOutgoingMessages(topic), additionalConfig);
    }

    public Settings withEventingSupport(EventingSupport eventingSupport) {
      return new Settings(serviceName, aclEnabled, eventingSupport, mockedEventing, additionalConfig);
    }

    /**
     * Specify additional config that will override the application-test.conf or application.conf configuration
     * in a particular test.
     */
    public Settings withAdditionalConfig(Config additionalConfig) {
      return new Settings(serviceName, aclEnabled, eventingSupport, mockedEventing, additionalConfig);
    }

    @Override
    public String toString() {
      return "Settings(" +
          "serviceName='" + serviceName + '\'' +
          ", aclEnabled=" + aclEnabled +
          ", eventingSupport=" + eventingSupport +
          ", mockedEventing=" + mockedEventing +
          ')';
    }
  }

  private static final Logger log = LoggerFactory.getLogger(TestKit.class);

  private final Settings settings;

  private EventingTestKit.MessageBuilder messageBuilder;
  private MessageCodec messageCodec;
  private boolean started = false;
  private String proxyHost;
  private int proxyPort;
  private EventingTestKit eventingTestKit;
  private ActorSystem<?> runtimeActorSystem;
  private ComponentClient componentClient;
  private HttpClientProvider httpClientProvider;
  private HttpClient selfHttpClient;
  private TimerScheduler timerScheduler;
  private Optional<DependencyProvider> dependencyProvider;
  private int eventingTestKitPort = -1;

  /**
   * Create a new testkit for a service descriptor with the default settings.
   */
  public TestKit() {
    this(Settings.DEFAULT);
  }

  /**
   * Create a new testkit for a service descriptor with custom settings.
   *
   * @param settings     custom testkit settings
   */
  public TestKit(final Settings settings) {
    this.settings = settings;
  }

  /**
   * Start this testkit with default configuration.
   * The default configuration is loaded from {@code application-test.conf} if that exists, otherwise
   * from {@code application.conf}.
   *
   * @return this TestKit instance
   */
  public TestKit start() {
    if (started)
      throw new IllegalStateException("Testkit already started");

    eventingTestKitPort = availableLocalPort();
    startRuntime(settings.additionalConfig);
    started = true;

    if (log.isDebugEnabled())
      log.debug("TestKit using [{}:{}] for calls to proxy from service", proxyHost, proxyPort);

    return this;
  }

  private void startEventingTestkit() {
    if (settings.eventingSupport == TEST_BROKER || settings.mockedEventing.hasConfig()) {
      log.info("Eventing TestKit booting up on port: " + eventingTestKitPort);
      // actual message codec instance not available until runtime/sdk started, thus this is called after discovery happens
      eventingTestKit = EventingTestKit.start(runtimeActorSystem, "0.0.0.0", eventingTestKitPort, new JsonMessageCodec());
    }
  }

  private void startRuntime(final Config config)  {
    try {
      log.debug("Config from user: {}", config);

      SdkRunner runner = new SdkRunner() {
        @Override
        public Config applicationConfig() {
          return ConfigFactory.parseString("akka.javasdk.dev-mode.enabled = true")
              .withFallback(config)
              .withFallback(super.applicationConfig());
        }

        @Override
        public SpiSettings getSettings() {
          SpiSettings s = super.getSettings();

          SpiEventingSupportSettings eventingSettings =
              switch (settings.eventingSupport) {
                case TEST_BROKER -> new SpiEventingSupportSettings.TestBroker(eventingTestKitPort);
                case GOOGLE_PUBSUB -> SpiEventingSupportSettings.fromConfigValue("google-pubsub-emulator");
                case KAFKA -> SpiEventingSupportSettings.fromConfigValue("kafka");
              };
          SpiMockedEventingSettings mockedEventingSettings =
              SpiMockedEventingSettings.create(settings.mockedEventing.mockedIncomingEvents, settings.mockedEventing.mockedOutgoingEvents);


          if (s.devMode().isEmpty())
            throw new IllegalStateException("dev-mode must be enabled"); // it's set from overridden applicationConfig method

          SpiDevModeSettings devModeSettings = s.devMode().get()
              .withTestMode(true)
              .withAclEnabled(settings.aclEnabled).withServiceName(settings.serviceName)
              .withEventingSupport(eventingSettings)
              .withMockedEventing(mockedEventingSettings);

          return s.withDevMode(devModeSettings);
        }

      };

      Config runtimeConfig = ConfigFactory.empty();
      runtimeActorSystem = KalixRuntimeMain.start(Some.apply(runtimeConfig), Some.apply(runner));
      // wait for SDK to get on start callback (or fail starting), we need it to set up the component client
      var startupContext = runner.started().toCompletableFuture().get(20, TimeUnit.SECONDS);
      var componentClients = startupContext.componentClients();
      dependencyProvider = Optional.ofNullable(startupContext.dependencyProvider().getOrElse(() -> null));

      startEventingTestkit();

      proxyPort = ApplicationConfig.get(runtimeActorSystem).getConfig().getInt("akka.javasdk.dev-mode.http-port");
      proxyHost = "localhost";

      Http http = Http.get(runtimeActorSystem);
      log.info("Checking runtime status");
      CompletionStage<String> checkingProxyStatus = Patterns.retry(() ->
        http.singleRequest(HttpRequest.GET("http://" + proxyHost + ":" + proxyPort + "/akka/dev-mode/health-check")).thenCompose(response -> {
          int responseCode = response.status().intValue();
          if (responseCode == 404) {
            log.info("Runtime started");
            return CompletableFuture.completedStage("Ok");
          } else {
            log.info("Waiting for runtime, current response code is {}", responseCode);
            return CompletableFuture.failedFuture(new IllegalStateException("Runtime not started."));
          }
        }), 10, Duration.ofSeconds(1), runtimeActorSystem);

      try {
        checkingProxyStatus.toCompletableFuture().get();
      } catch (InterruptedException | ExecutionException e) {
        log.error("Failed to connect to Runtime with:", e);
        throw new RuntimeException(e);
      }

      // FIXME what of this is still needed
      // the proxy will announce its host and default port, but to communicate with it,
      // we need to use the port and host that testcontainers will expose
      // therefore, we set a port override in ProxyInfoHolder to allow for inter-component communication
      ProxyInfoHolder holder = ProxyInfoHolder.get(runtimeActorSystem);
      holder.overridePort(proxyPort);
      holder.overrideProxyHost(proxyHost);
      holder.overrideTracingCollectorEndpoint(""); //emulating ProxyInfo with disabled tracing.

      // once runtime is started
      componentClient = new ComponentClientImpl(componentClients, Option.empty(), runtimeActorSystem.executionContext());
      selfHttpClient = new HttpClient(runtimeActorSystem, "http://localhost:" + proxyPort);
      httpClientProvider = startupContext.httpClientProvider();
      var codec = new JsonMessageCodec();
      timerScheduler = new TimerSchedulerImpl(codec, componentClients.timerClient(), Metadata.EMPTY);
      this.messageBuilder = new EventingTestKit.MessageBuilder(codec);

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

    return proxyHost;
  }

  /**
   * Get the local port where the service is available.
   */
  public int getPort() {
    if (!started)
      throw new IllegalStateException("Need to start the testkit before accessing the port");

    return proxyPort;
  }

  /**
   * Get an Akka gRPC client for the given service name. The same client instance is shared for the
   * test. The lifecycle of the client is managed by the SDK and it should not be stopped by user
   * code.
   *
   * @param <T>         The "service" interface generated for the service by Akka gRPC
   * @param clientClass The class of a gRPC service generated by Akka gRPC
   */
  public <T> T getGrpcClient(Class<T> clientClass) {
    return GrpcClients.get(getActorSystem()).getGrpcClient(clientClass, getHost(), getPort());
  }

  /**
   * Get an Akka gRPC client for the given service name, authenticating using the given principal.
   * The same client instance is shared for the test. The lifecycle of the client is managed by the
   * SDK and it should not be stopped by user code.
   *
   * @param <T>         The "service" interface generated for the service by Akka gRPC
   * @param clientClass The class of a gRPC service generated by Akka gRPC
   * @param principal   The principal to authenticate calls to the service as.
   */
  public <T> T getGrpcClientForPrincipal(Class<T> clientClass, Principal principal) {
    String serviceName = null;
    if (principal == Principal.SELF) {
      serviceName = settings.serviceName;
    } else if (principal instanceof Principal.LocalService) {
      serviceName = ((Principal.LocalService) principal).getName();
    }
    if (serviceName != null) {
      return GrpcClients.get(getActorSystem())
          .getGrpcClient(clientClass, getHost(), getPort(), serviceName);
    } else {
      return GrpcClients.get(getActorSystem()).getGrpcClient(clientClass, getHost(), getPort());
    }
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

  /**
   * Get a {@link TimerScheduler} for scheduling TimedAction.
   */
  public TimerScheduler getTimerScheduler() {
    return timerScheduler;
  }

  /**
   * Get a {@link HttpClientProvider} for looking up HTTP clients to interact with other services than the current.
   * Requests will appear as coming from this service from an ACL perspective.
   */
  public HttpClientProvider getHttpClientProvider() {
    return httpClientProvider;
  }

  /**
   * Get a {@link HttpClient} for interacting with the service itself, the client will not be authenticated
   * and will appear to the service as a request with the internet principal.
   */
  public HttpClient getSelfHttpClient() {
    return selfHttpClient;
  }

  /**
   * Get incoming messages for KeyValueEntity.
   *
   * @param typeId @TypeId or entity_type of the KeyValueEntity (depending on the used SDK)
   */
  public IncomingMessages getKeyValueEntityIncomingMessages(String typeId) {
    if (!settings.mockedEventing.hasKeyValueEntitySubscription(typeId)) {
      throwMissingConfigurationException("KeyValueEntity " + typeId);
    }
    return eventingTestKit.getKeyValueEntityIncomingMessages(typeId);
  }

  /**
   * Get incoming messages for EventSourcedEntity.
   *
   * @param typeId @TypeId or entity_type of the EventSourcedEntity (depending on the used SDK)
   */
  public IncomingMessages getEventSourcedEntityIncomingMessages(String typeId) {
    if (!settings.mockedEventing.hasEventSourcedEntitySubscription(typeId)) {
      throwMissingConfigurationException("EventSourcedEntity " + typeId);
    }
    return eventingTestKit.getEventSourcedEntityIncomingMessages(typeId);
  }

  /**
   * Get incoming messages for Stream (eventing.in.direct in case of protobuf SDKs).
   *
   * @param service  service name
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
    throw new IllegalStateException("Currently configured mocked eventing is [" + settings.mockedEventing +
        "]. To use the MockedEventing API, to configure mocking of " + hint);
  }

  /**
   * Stop the testkit and local runtime.
   */
  public void stop() {
    try {
      if (runtimeActorSystem != null) {
        akka.testkit.javadsl.TestKit.shutdownActorSystem(runtimeActorSystem.classicSystem(), FiniteDuration.create(10, TimeUnit.SECONDS), true);
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
   * INTERNAL API
   */
  @InternalApi
  public MessageCodec getMessageCodec() {
    return messageCodec;
  }
  
  /**
   * Returns {@link EventingTestKit.MessageBuilder} utility
   * to create {@link EventingTestKit.Message}s for the eventing testkit.
   */
  public EventingTestKit.MessageBuilder getMessageBuilder() {
    return messageBuilder;
  }

  public Optional<DependencyProvider> getDependencyContext() {
    return dependencyProvider;
  }
}

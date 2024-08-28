/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.actor.ActorSystem;
import akka.annotation.InternalApi;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.pattern.Patterns;
import akka.javasdk.DependencyProvider;
import akka.javasdk.Metadata;
import akka.javasdk.impl.timer.TimerSchedulerImpl;
import akka.javasdk.timer.TimerScheduler;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.testkit.javadsl.TestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import akka.javasdk.Kalix;
import akka.javasdk.Principal;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.GrpcClients;
import akka.javasdk.impl.JsonMessageCodec;
import akka.javasdk.impl.MessageCodec;
import akka.javasdk.impl.NextGenKalixJavaApplication;
import akka.javasdk.impl.ProxyInfoHolder;
import akka.javasdk.impl.client.ComponentClientImpl;
import akka.platform.javasdk.spi.ComponentClients;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import kalix.runtime.KalixRuntimeMain;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.Some;
import scala.Tuple3;
import scala.concurrent.Await;
import scala.concurrent.Promise;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static akka.javasdk.testkit.KalixTestKit.Settings.EventingSupport.TEST_BROKER;

/**
 * Testkit for running Kalix services locally.
 *
 * <p>Requires Docker for starting a local instance of the Kalix Runtime.
 *
 * <p>Create a KalixTestkit with an {@link Kalix} service descriptor, and then {@link #start} the
 * testkit before testing the service with gRPC or HTTP clients. Call {@link #stop} after tests are
 * complete.
 */
public class KalixTestKit {

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
   * Settings for KalixTestkit.
   */
  public static class Settings {
    /**
     * Default stop timeout (10 seconds).
     */
    public static Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(10);
    /**
     * Default settings for KalixTestkit.
     */
    public static Settings DEFAULT = new Settings(DEFAULT_STOP_TIMEOUT);

    /**
     * Timeout setting for stopping the local Kalix test instance.
     */
    public final Duration stopTimeout;

    /**
     * The name of this service when deployed.
     */
    public final String serviceName;

    /**
     * Whether ACL checking is enabled.
     */
    public final boolean aclEnabled;

    /**
     * Whether advanced View features are enabled.
     */
    public final boolean advancedViews;

    /**
     * To override workflow tick interval for integration tests
     */
    public final Optional<Duration> workflowTickInterval;

    /**
     * Service port mappings from serviceName to host:port
     */
    public final Map<String, String> servicePortMappings;

    public final EventingSupport eventingSupport;

    public final MockedEventing mockedEventing;

    /**
     * Create new settings for KalixTestkit.
     *
     * @param stopTimeout timeout to use when waiting for Kalix to stop
     * @deprecated Use Settings.DEFAULT.withStopTimeout() instead.
     */
    @Deprecated
    public Settings(final Duration stopTimeout) {
      this(stopTimeout, "self", false, false, Optional.empty(), Collections.emptyMap(), TEST_BROKER, MockedEventing.EMPTY);
    }

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
        final Duration stopTimeout,
        final String serviceName,
        final boolean aclEnabled,
        final boolean advancedViews,
        final Optional<Duration> workflowTickInterval,
        final Map<String, String> servicePortMappings,
        final EventingSupport eventingSupport,
        final MockedEventing mockedEventing) {
      this.stopTimeout = stopTimeout;
      this.serviceName = serviceName;
      this.aclEnabled = aclEnabled;
      this.advancedViews = advancedViews;
      this.workflowTickInterval = workflowTickInterval;
      this.servicePortMappings = servicePortMappings;
      this.eventingSupport = eventingSupport;
      this.mockedEventing = mockedEventing;
    }

    /**
     * Set a custom stop timeout, for stopping the local Kalix test instance.
     *
     * @param stopTimeout timeout to use when waiting for Kalix to stop
     * @return updated Settings
     */
    public Settings withStopTimeout(final Duration stopTimeout) {
      return new Settings(stopTimeout, serviceName, aclEnabled, advancedViews, workflowTickInterval, servicePortMappings, eventingSupport, mockedEventing);
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
      return new Settings(stopTimeout, serviceName, aclEnabled, advancedViews, workflowTickInterval, servicePortMappings, eventingSupport, mockedEventing);
    }

    /**
     * Disable ACL checking in this service.
     *
     * @return The updated settings.
     */
    public Settings withAclDisabled() {
      return new Settings(stopTimeout, serviceName, false, advancedViews, workflowTickInterval, servicePortMappings, eventingSupport, mockedEventing);
    }

    /**
     * Enable ACL checking in this service.
     *
     * @return The updated settings.
     */
    public Settings withAclEnabled() {
      return new Settings(stopTimeout, serviceName, true, advancedViews, workflowTickInterval, servicePortMappings, eventingSupport, mockedEventing);
    }

    /**
     * Enable advanced View features for this service.
     *
     * @return The updated settings.
     */
    public Settings withAdvancedViews() {
      return new Settings(stopTimeout, serviceName, aclEnabled, true, workflowTickInterval, servicePortMappings, eventingSupport, mockedEventing);
    }

    /**
     * Overrides workflow tick interval
     *
     * @return The updated settings.
     */
    public Settings withWorkflowTickInterval(Duration tickInterval) {
      return new Settings(stopTimeout, serviceName, aclEnabled, true, Optional.of(tickInterval), servicePortMappings, eventingSupport, mockedEventing);
    }

    /**
     * Mock the incoming messages flow from a KeyValueEntity.
     */
    public Settings withKeyValueEntityIncomingMessages(String typeId) {
      return new Settings(stopTimeout, serviceName, aclEnabled, true, workflowTickInterval, servicePortMappings, eventingSupport,
          mockedEventing.withKeyValueEntityIncomingMessages(typeId));
    }

    /**
     * Mock the incoming events flow from an EventSourcedEntity.
     */
    public Settings withEventSourcedEntityIncomingMessages(String typeId) {
      return new Settings(stopTimeout, serviceName, aclEnabled, true, workflowTickInterval, servicePortMappings, eventingSupport,
          mockedEventing.withEventSourcedIncomingMessages(typeId));
    }

    /**
     * Mock the incoming messages flow from a Stream (eventing.in.direct in case of protobuf SDKs).
     */
    public Settings withStreamIncomingMessages(String service, String streamId) {
      return new Settings(stopTimeout, serviceName, aclEnabled, true, workflowTickInterval, servicePortMappings, eventingSupport,
          mockedEventing.withStreamIncomingMessages(service, streamId));
    }

    /**
     * Mock the incoming events flow from a Topic.
     */
    public Settings withTopicIncomingMessages(String topic) {
      return new Settings(stopTimeout, serviceName, aclEnabled, true, workflowTickInterval, servicePortMappings, eventingSupport,
          mockedEventing.withTopicIncomingMessages(topic));
    }

    /**
     * Mock the outgoing events flow for a Topic.
     */
    public Settings withTopicOutgoingMessages(String topic) {
      return new Settings(stopTimeout, serviceName, aclEnabled, true, workflowTickInterval, servicePortMappings, eventingSupport,
          mockedEventing.withTopicOutgoingMessages(topic));
    }

    /**
     * Add a service port mapping from serviceName to host:port.
     *
     * @return The updated settings.
     */
    public Settings withServicePortMapping(String serviceName, String host, int port) {
      var updatedMappings = new HashMap<>(servicePortMappings);
      updatedMappings.put(serviceName, host + ":" + port);
      return new Settings(stopTimeout, serviceName, aclEnabled, advancedViews, workflowTickInterval, Map.copyOf(updatedMappings), eventingSupport, mockedEventing);
    }

    public Settings withEventingSupport(EventingSupport eventingSupport) {
      return new Settings(stopTimeout, serviceName, aclEnabled, advancedViews, workflowTickInterval, servicePortMappings, eventingSupport, mockedEventing);
    }

    @Override
    public String toString() {
      var portMappingsRendered =
          servicePortMappings.entrySet().stream()
              .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList());

      return "Settings(" +
          "stopTimeout=" + stopTimeout +
          ", serviceName='" + serviceName + '\'' +
          ", aclEnabled=" + aclEnabled +
          ", advancedViews=" + advancedViews +
          ", workflowTickInterval=" + workflowTickInterval +
          ", servicePortMappings=[" + String.join(", ", portMappingsRendered) + "]" +
          ", eventingSupport=" + eventingSupport +
          ", mockedEventing=" + mockedEventing +
          ')';
    }
  }

  private static final Logger log = LoggerFactory.getLogger(KalixTestKit.class);

  /** Default local port where the Google Pub/Sub emulator is available (8085). */
  public static final int DEFAULT_GOOGLE_PUBSUB_PORT = 8085;

  public static final int DEFAULT_KAFKA_PORT = 9092;

  private final Settings settings;

  private Kalix kalix;
  private EventingTestKit.MessageBuilder messageBuilder;
  private MessageCodec messageCodec;
  private boolean started = false;
  private String proxyHost;
  private int proxyPort;
  private EventingTestKit eventingTestKit;
  private ActorSystem runtimeActorSystem;
  private ComponentClient componentClient;
  private TimerScheduler timerScheduler;
  private Optional<DependencyProvider> dependencyProvider;
  private int eventingTestKitPort = -1;

  /**
   * Create a new testkit for a Kalix service descriptor with the default settings.
   */
  public KalixTestKit() {
    this(Settings.DEFAULT);
  }

  /**
   * Create a new testkit for a Kalix service descriptor with custom settings.
   *
   * @param settings     custom testkit settings
   */
  public KalixTestKit(final Settings settings) {
    this.settings = settings;
  }

  /**
   * Start this testkit with default configuration (loaded from {@code application.conf}).
   *
   * @return this KalixTestkit
   */
  public KalixTestKit start() {
    return start(ConfigFactory.empty());
  }

  /**
   * Start this testkit with custom configuration (overrides {@code application.conf}).
   *
   * @param config custom test configuration for the KalixRunner
   * @return this KalixTestkit
   */
  public KalixTestKit start(final Config config) {
    if (started)
      throw new IllegalStateException("KalixTestkit already started");

    eventingTestKitPort = availableLocalPort();
    startRuntime(config);
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
      final Map<String, Object> runtimeOptions = new HashMap<>();
      runtimeOptions.put("kalix.proxy.acl.local-dev.self-deployment-name", settings.serviceName);
      runtimeOptions.put("kalix.proxy.acl.enabled", settings.aclEnabled);
      runtimeOptions.put("kalix.proxy.view.features.all", settings.advancedViews);
      runtimeOptions.put("kalix.proxy.version-check-on-startup", false);
      if (settings.mockedEventing.hasConfig()) {
        runtimeOptions.put("kalix.proxy.eventing.grpc-backend.host", "localhost");
        runtimeOptions.put("kalix.proxy.eventing.grpc-backend.port", eventingTestKitPort);
      }
      if (settings.eventingSupport == TEST_BROKER) {
        runtimeOptions.put("kalix.proxy.eventing.support", "grpc-backend");
      } else if (settings.eventingSupport == Settings.EventingSupport.KAFKA) {
        runtimeOptions.put("kalix.proxy.eventing.support", "kafka");
        runtimeOptions.put("kalix.proxy.eventing.kafka.bootstrap-servers=host.testcontainers.internal", DEFAULT_KAFKA_PORT);
      } else if (settings.eventingSupport == Settings.EventingSupport.GOOGLE_PUBSUB) {
        runtimeOptions.put("kalix.proxy.eventing.support", "google-pubsub-emulator");
        runtimeOptions.put("kalix.proxy.eventing.google-pubsub-emulator-defaults.host", "host.testcontainers.internal");
        runtimeOptions.put("kalix.proxy.eventing.google-pubsub-emulator-defaults.port", DEFAULT_GOOGLE_PUBSUB_PORT);
      }
      if (settings.mockedEventing.hasIncomingConfig()) {
        runtimeOptions.put("kalix.proxy.eventing.override.sources", settings.mockedEventing.toIncomingFlowConfig());
      }
      if (settings.mockedEventing.hasOutgoingConfig()) {
        runtimeOptions.put("kalix.proxy.eventing.override.destinations", settings.mockedEventing.toOutgoingFlowConfig());
      }
      settings.servicePortMappings.forEach((serviceName, hostPort) ->
        runtimeOptions.put("akka.platform.dev-mode.service-port-mappings." + serviceName, hostPort)
      );
      settings.workflowTickInterval.ifPresent(tickInterval -> runtimeOptions.put("kalix.proxy.workflow-entity.tick-interval", tickInterval.toMillis() + " ms"));


      log.debug("Config for runtime: {}", runtimeOptions);
      log.debug("Config from user: {}", config);
      // rationale: we want to override things with the runtime options, then use dev mode for everything the runtime
      //            needs but then allow user to specify their own settings that are found (Note that users cannot override
      //            runtime config with an application.conf)
      var runtimeConfig = ConfigFactory.parseMap(runtimeOptions)
              .withFallback(config)
              .withFallback(ConfigFactory.load("dev-mode.conf"))
              .withFallback(ConfigFactory.load("application.conf"));

      proxyPort = runtimeConfig.getInt("kalix.proxy.http-port");
      proxyHost = "localhost";

      Promise<Tuple3<Kalix, ComponentClients, Optional<DependencyProvider>>> startedKalix = Promise.apply();
      if (!NextGenKalixJavaApplication.onNextStartCallback().compareAndSet(null, startedKalix)) {
        throw new RuntimeException("Found another integration test run waiting for Kalix to start, multiple tests must not run in parallel");
      }
      // FIXME this can't possibly work, we have already done logging, logback-test should be picked up?
      System.setProperty("logback.configurationFile", "logback-dev-mode.xml");

      runtimeActorSystem = KalixRuntimeMain.start(Some.apply(runtimeConfig)).classicSystem();
      // wait for SDK to get on start callback (or fail starting), we need it to set up the component client
      var tuple = Await.result(startedKalix.future(), scala.concurrent.duration.Duration.create("20s"));
      kalix = tuple._1();
      var componentClients = tuple._2();
      dependencyProvider = tuple._3();

      startEventingTestkit();

      Http http = Http.get(runtimeActorSystem);
      log.info("Checking kalix-runtime status");
      CompletionStage<String> checkingProxyStatus = Patterns.retry(() ->
        http.singleRequest(HttpRequest.GET("http://localhost:" + proxyPort + "/akka/dev-mode/health-check")).thenCompose(response -> {
        int responseCode = response.status().intValue();
        if (responseCode == 404) {
          log.info("Kalix-runtime started");
          return CompletableFuture.completedStage("Ok");
        } else {
          log.info("Waiting for kalix-runtime, current response code is {}", responseCode);
          return CompletableFuture.failedFuture(new IllegalStateException("Kalix Runtime not started."));
        }
      }), 10, Duration.ofSeconds(1), runtimeActorSystem);

      try {
        checkingProxyStatus.toCompletableFuture().get();
      } catch (InterruptedException | ExecutionException e) {
        log.error("Failed to connect to Kalix Runtime with:", e);
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
      componentClient = new ComponentClientImpl(componentClients, Option.empty(), runtimeActorSystem.dispatcher());
      timerScheduler = new TimerSchedulerImpl(kalix.getMessageCodec(), componentClients.timerClient(), Metadata.EMPTY);
      this.messageBuilder = new EventingTestKit.MessageBuilder(kalix.getMessageCodec());

    } catch (Exception ex) {
      throw new RuntimeException("Error while starting Kalix testkit", ex);
    }
  }

  /**
   * Get the host name/IP address where the Kalix service is available. This is relevant in certain
   * Continuous Integration environments.
   *
   * @return Kalix host
   */
  public String getHost() {
    if (!started)
      throw new IllegalStateException("Need to start KalixTestkit before accessing the host name");

    return proxyHost;
  }

  /**
   * Get the local port where the Kalix service is available.
   *
   * @return local Kalix port
   */
  public int getPort() {
    if (!started)
      throw new IllegalStateException("Need to start KalixTestkit before accessing the port");

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
  public ActorSystem getActorSystem() {
    if (!started)
      throw new IllegalStateException("Need to start KalixTestkit before accessing actor system");
    return runtimeActorSystem;
  }

  /**
   * Get an {@link ActorSystem} for interacting "internally" with the components of a service.
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
   * Stop the testkit and local Kalix.
   */
  public void stop() {
    try {
      if (runtimeActorSystem != null) {
        TestKit.shutdownActorSystem(runtimeActorSystem, FiniteDuration.create(settings.stopTimeout.toMillis(), "ms"), true);
      }
    } catch (Exception e) {
      log.error("KalixTestkit Kalix runtime failed to terminate", e);
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

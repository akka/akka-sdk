/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

/** The JSON-RPC method names of A2A. */
public final class A2aMethods {
  private A2aMethods() {}

  public static final String SEND_MESSAGE = "SendMessage";
  public static final String SEND_STREAMING_MESSAGE = "SendStreamingMessage";
  public static final String GET_TASK = "GetTask";
  public static final String LIST_TASKS = "ListTasks";
  public static final String CANCEL_TASK = "CancelTask";
  public static final String SUBSCRIBE_TO_TASK = "SubscribeToTask";
  public static final String CREATE_TASK_PUSH_NOTIFICATION_CONFIG =
      "CreateTaskPushNotificationConfig";
  public static final String GET_TASK_PUSH_NOTIFICATION_CONFIG = "GetTaskPushNotificationConfig";
  public static final String LIST_TASK_PUSH_NOTIFICATION_CONFIGS =
      "ListTaskPushNotificationConfigs";
  public static final String DELETE_TASK_PUSH_NOTIFICATION_CONFIG =
      "DeleteTaskPushNotificationConfig";
  public static final String GET_EXTENDED_AGENT_CARD = "GetExtendedAgentCard";
}

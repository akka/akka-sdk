package demo.multiagent.domain;

public record SessionMessage(String content, MessageType type) {

  public enum MessageType {AI, USER}
}

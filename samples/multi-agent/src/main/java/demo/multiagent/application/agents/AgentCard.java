package demo.multiagent.application.agents;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The AgentCard annotation is used to describe the Agents capabilities.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AgentCard {
  String id();
  String name();
  String description();
}

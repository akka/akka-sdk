package demo.multiagent.application.agents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AgentsRegistry {

  private final Map<String, Agent> availableAgents = new HashMap<>();

  public <A extends Agent> AgentsRegistry register(A agent) {
    var agentCls = agent.getClass();
    if (agentCls.isAnnotationPresent(AgentCard.class)) {
      var anno = agentCls.getAnnotation(AgentCard.class);
      availableAgents.put(anno.id(), agent);
    }
    return this;
  }

  public Agent getAgent(String agentId) {
    return availableAgents.get(agentId);
  }

  public String allAgentsInJson() {
    return genListOfAgents(availableAgents.values());
  }

  public String agentSelectionInJson(List<String> agentsIds) {
    List<Agent> selected = new ArrayList<>();
    for (String agentId : agentsIds) {
      selected.add(availableAgents.get(agentId));
    }
    return genListOfAgents(selected);
  }

  private String genListOfAgents(Collection<Agent> agents) {

    return agents.stream()
      .map(Agent::getClass)
      .filter(agentClass -> agentClass.isAnnotationPresent(AgentCard.class))
      .map(agentClass -> {
        AgentCard annotation = agentClass.getAnnotation(AgentCard.class);
        return String.format("""
            {"id": "%s", "name": "%s", "description": "%s"}""",
          annotation.id(),
          annotation.name(),
          annotation.description());
      })
      .collect(Collectors.joining(", ", "[", "]"));
  }


}

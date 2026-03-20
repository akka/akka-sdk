package akka.policy.llm.pre
import rego.v1

default allow := false

allow if {
  usage_within_session_quota
  usage_within_tool_quota
}

violations contains violation if {
  not usage_within_session_quota
  violation := $"Session '{input.identity.session}' token quota exceeded. Quota is {data.quota.session}, used {session_token_consumption}."
}

violations contains violation if {
  not usage_within_tool_quota
  violation := $"Agent '{input.identity.agent}' tool usage quota exceeded. Quota is {data.quota.toolcall.agents[input.identity.agent]}, used {tool_call_counter}."
}

# usage comes from input, sent by the caller at evaluation time
default session_token_consumption := 0
session_token_consumption := input.usage.currentTokenUsage

default usage_within_session_quota := false
usage_within_session_quota
  if session_token_consumption <=
     data.quota.session

default tool_call_counter := 0
tool_call_counter := input.usage.toolCallCounter

default usage_within_tool_quota := false
usage_within_tool_quota
  if tool_call_counter <=
     data.quota.toolcall.agents[input.identity.agent]


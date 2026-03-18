package akka.policy.model.name

import data.akka.policy.model.name.violations

import rego.v1

default allow := false

allow if {
	some allowed_name in data.quota["allowed-models"]
	input.identity.modelName == allowed_name
}

violations contains violation if {
	not allow
	violation := sprintf("Model '%s' is not allowed. Allowed models: %s.", [input.identity.modelName, concat(", ", data.quota["allowed-models"])])
}

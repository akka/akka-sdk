# A2A interoperability checks

Python tools that check an A2A server or client against implementations that are not written with the Akka SDK. They run in tests only. The SDK does not depend on them.

The folder lives in `akka-javasdk-tests` because that module tests a running service from the outside. The tools need only the URL of the service, so they have no build file and sbt does not run them.

| File | Purpose |
|------|---------|
| `run_tck.sh` | Runs the [A2A compatibility kit](https://github.com/a2aproject/a2a-tck) against a running agent. |
| `adk_client_check.py` | Checks an agent with the [a2a-python](https://github.com/a2aproject/a2a-python) client. |
| `adk_echo_server.py` | An a2a-python server for testing A2A clients. |
| `bearer_proxy.py` | A reverse proxy that adds a bearer token to each request, for agents that require one. |
| `requirements.txt` | The pinned versions of a2a-python and the compatibility kit. |

## Requirements

- Python 3.11 to 3.14. The compatibility kit does not support 3.15 at the pinned commit.
- `git` and `bash`. On Windows, use Git Bash.

`run_tck.sh` creates `.venv` in this folder and installs `requirements.txt` into it. To run the Python scripts directly, create the same environment first:

```shell
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
```

On Windows, the interpreter is `.venv/Scripts/python`.

## Run the compatibility kit

```shell
./run_tck.sh http://localhost:9000/.well-known/agent-card.json
```

The argument is the Agent Card URL or the base URL of the agent. Further arguments go to the kit. The script tests the JSON-RPC and HTTP+JSON bindings and skips gRPC.

1. The first run covers the MUST requirements. The script exits with a non-zero status if one fails.
2. The second run covers all levels. Its report lists the SHOULD and MAY findings. These findings do not change the exit status.

The reports are in `reports/must` and `reports/all`. Open `compatibility.html` for the result per requirement. The script clones the kit into `.cache/a2a-tck` at the commit in `requirements.txt`.

The kit selects the behaviour of the agent by the prefix of the message id, for example `tck-complete-task` or `tck-input-required`. The agent under test must implement these behaviours. `adk_echo_server.py` shows them.

The kit has two checks on unsupported media types that conflict. One sends a part with the media type `application/x-unsupported-tck-type` and expects success. The other expects an error, unless the Agent Card declares that media type as an input mode. An agent passes both by declaring `*/*` as a default input mode.

## Check an agent with the a2a-python client

```shell
.venv/bin/python adk_client_check.py http://localhost:9000/.well-known/agent-card.json
```

The script performs these checks and exits with 1 when one fails:

1. The card has a name, an interface and a skill.
2. A single-turn message completes and the answer contains the sent text.
3. A streamed message delivers several events and ends with a completed task.
4. If a skill has the tag `input-required`, the first message ends in the input-required state and the second message on the same task completes it.

A message selects a skill with the metadata field `skillId`. Options:

| Option | Effect |
|--------|--------|
| `--binding jsonrpc` or `--binding http_json` | Selects the protocol binding. The default is `jsonrpc`. |
| `--token TOKEN` | Sends `Authorization: Bearer TOKEN`. |
| `--skill ID` | Selects the skill for checks 2 and 3. The default is the first skill on the card. |
| `--no-echo-check` | Does not require the answer to contain the sent text. |

## Run the echo server

```shell
.venv/bin/python adk_echo_server.py --port 9000 [--token TOKEN] [--push]
```

The server serves both bindings. The JSON-RPC endpoint is `/` and the HTTP+JSON endpoint is `/a2a/rest`. It has three skills:

- `echo` replies with `echo: <text>` as a streamed artifact and completes the task.
- `ask` returns an input-required task that asks for a name. The next message on the task completes it with a greeting. The skill has the tag `input-required`.
- `slow` works for three seconds, then replies with `slow echo: <text>` as an artifact and completes the task.

With `--push`, the Agent Card offers push notifications, and the server sends them to the push notification configurations of each task.

With `--token` or the environment variable `A2A_ECHO_TOKEN`, the server answers every request except the Agent Card with 401 unless the request has the matching bearer token.

## Check the A2A test service of the SDK

`akkajavasdk.A2aInteropServiceTest` runs the test service of `akka-javasdk-tests` on port 19391 for the number of seconds in `A2A_INTEROP_SECONDS`. The compatibility kit selects no skill, so the first skill on the card answers it: `kit-tasks`, a task workflow (`A2aKitTaskWorkflow`) that implements the kit's message id prefixes and echoes other messages. The workflow also offers the skill `ask`, tagged `input-required`, for check 4 of `adk_client_check.py`. The echo agent follows as the skill `echo`; select it with `--skill echo`. Its A2A routes require a bearer token from the issuer `a2a-test-issuer`, which the testkit accepts without a signature. The compatibility kit has no option for credentials, so run the checks through `bearer_proxy.py`:

```shell
A2A_INTEROP_SECONDS=1200 sbt "akka-javasdk-tests/testOnly akkajavasdk.A2aInteropServiceTest"
.venv/bin/python bearer_proxy.py --port 9000 --target http://localhost:19391 --token <token>
./run_tck.sh http://127.0.0.1:9000/.well-known/agent-card.json
```

The token is an unsigned JWT with the claims `{"iss": "a2a-test-issuer", "sub": "interop"}`. The proxy keeps the `Host` header, so the Agent Card lists the proxy's address.

## Pinned versions

| Tool | Version |
|------|---------|
| a2a-sdk (a2a-python) | 1.3.1, protocol 1.0 |
| a2a-tck | commit `70f6f73c30208e80c889141907e094899e6e9f05` |

To change the kit version, edit the commit in `requirements.txt`. `run_tck.sh` reads it from there.

## CI

The workflow `.github/workflows/a2a-interop.yml` starts a service, runs `run_tck.sh` and runs `adk_client_check.py` on both bindings. It uploads the reports as the artifact `a2a-tck-reports`.

#!/usr/bin/env python3
"""A2A server built on a2a-python, used to test the Akka SDK's A2A client and to check the harness.

Serves the JSON-RPC and HTTP+JSON bindings of protocol 1.0 with two skills:

- echo: replies "echo: <text>" as a streamed artifact and completes the task.
- ask: replies with an input-required task that asks for a name, then completes with a greeting
  when the next message on the task arrives. The skill is tagged "input-required".

A message selects a skill with the metadata field "skillId". Without it the server uses echo.
A message id that starts with a prefix the compatibility kit uses (for example "tck-complete-task")
gets the behaviour the kit expects, so the kit can run against this server.

With --token (or A2A_ECHO_TOKEN) every request except the Agent Card needs the header
"Authorization: Bearer <token>".
"""

import argparse
import asyncio
import os

import uvicorn
from google.protobuf import json_format
from google.protobuf.struct_pb2 import Value
from starlette.applications import Starlette

from a2a.helpers.proto_helpers import get_message_text, new_task_from_user_message
from a2a.server.agent_execution.agent_executor import AgentExecutor
from a2a.server.agent_execution.context import RequestContext
from a2a.server.events.event_queue import EventQueue
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.routes import create_agent_card_routes, create_jsonrpc_routes, create_rest_routes
from a2a.server.tasks.inmemory_task_store import InMemoryTaskStore
from a2a.server.tasks.task_updater import TaskUpdater
from a2a.types import AgentCapabilities, AgentCard, AgentInterface, AgentSkill, Part, TaskState
from a2a.utils.errors import A2AError

REST_PATH = "/a2a/rest"
CARD_PATH = "/.well-known/agent-card.json"
ECHO_ARTIFACT_ID = "echo-reply"


class EchoExecutor(AgentExecutor):
    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        message = context.message
        if message is None or context.task_id is None or context.context_id is None:
            return
        updater = TaskUpdater(event_queue, context.task_id, context.context_id)
        task = context.current_task
        if task is None:
            await event_queue.enqueue_event(new_task_from_user_message(message))

        if await self._kit_behaviour(message.message_id, updater, event_queue):
            return

        if task is not None and task.status.state == TaskState.TASK_STATE_INPUT_REQUIRED:
            await updater.complete(updater.new_agent_message([Part(text=f"Hello, {get_message_text(message)}")]))
            return

        skill = message.metadata["skillId"] if "skillId" in message.metadata else "echo"
        if skill == "ask":
            await updater.requires_input(updater.new_agent_message([Part(text="What is your name?")]))
        elif skill == "echo":
            await self._echo(get_message_text(message), updater)
        else:
            raise A2AError(f"unknown skill {skill}")

    async def _echo(self, text: str, updater: TaskUpdater) -> None:
        await updater.start_work()
        chunks = ["echo: "] + [word + " " for word in text.split()]
        chunks[-1] = chunks[-1].rstrip()
        for index, chunk in enumerate(chunks):
            await updater.add_artifact(
                parts=[Part(text=chunk)],
                artifact_id=ECHO_ARTIFACT_ID,
                append=index > 0,
                last_chunk=index == len(chunks) - 1,
            )
        await updater.complete()

    async def _kit_behaviour(self, message_id: str, updater: TaskUpdater, event_queue: EventQueue) -> bool:
        """Behaviours the compatibility kit selects by message id prefix. Returns true when one applied."""
        if message_id.startswith("tck-stream-artifact-chunked"):
            await updater.start_work()
            await updater.add_artifact(parts=[Part(text="chunk-1 ")], append=True)
            await updater.add_artifact(parts=[Part(text="chunk-2")], append=True, last_chunk=True)
            await updater.complete()
        elif message_id.startswith("test-resubscribe-message-id"):
            await updater.start_work()
            await asyncio.sleep(4)
            await updater.complete()
        elif message_id.startswith("tck-stream-artifact-text") or message_id.startswith("tck-stream-ordering-001"):
            await updater.start_work()
            await updater.add_artifact(parts=[Part(text="Streamed text content")])
            await updater.complete()
        elif message_id.startswith("tck-stream-artifact-file"):
            await updater.start_work()
            await updater.add_artifact(parts=[Part(raw=b"tck", media_type="text/plain", filename="output.txt")])
            await updater.complete()
        elif message_id.startswith("tck-artifact-file-url"):
            await updater.add_artifact(
                parts=[Part(url="https://example.com/output.txt", media_type="text/plain", filename="output.txt")]
            )
            await updater.complete()
        elif message_id.startswith("tck-message-response"):
            await event_queue.enqueue_event(updater.new_agent_message([Part(text="Direct message response")]))
        elif message_id.startswith("tck-input-required"):
            await updater.requires_input()
        elif message_id.startswith("tck-complete-task"):
            await updater.complete(updater.new_agent_message([Part(text="Hello from TCK")]))
        elif message_id.startswith("tck-artifact-text"):
            await updater.add_artifact(parts=[Part(text="Generated text content")])
            await updater.complete()
        elif message_id.startswith("tck-artifact-file"):
            await updater.add_artifact(parts=[Part(raw=b"tck", media_type="text/plain", filename="output.txt")])
            await updater.complete()
        elif message_id.startswith("tck-artifact-data"):
            data = json_format.Parse('{"key": "value", "count": 42}', Value())
            await updater.add_artifact(parts=[Part(data=data)])
            await updater.complete()
        elif message_id.startswith("tck-reject-task"):
            raise A2AError("rejected")
        elif message_id.startswith("tck-stream-001") or message_id.startswith("tck-stream-003"):
            await updater.start_work()
            await updater.add_artifact(parts=[Part(text="Stream text")])
            await updater.complete()
        elif message_id.startswith("tck-stream-002"):
            await updater.complete()
        else:
            return False
        return True

    async def cancel(self, context: RequestContext, event_queue: EventQueue) -> None:
        if context.task_id is None or context.context_id is None:
            return
        await TaskUpdater(event_queue, context.task_id, context.context_id).cancel()


class BearerToken:
    """ASGI middleware that rejects requests without the bearer token. The Agent Card stays public."""

    def __init__(self, app, token: str):
        self.app = app
        self.expected = f"Bearer {token}".encode()

    async def __call__(self, scope, receive, send):
        if scope["type"] == "http" and scope["path"] != CARD_PATH:
            headers = dict(scope["headers"])
            if headers.get(b"authorization") != self.expected:
                await send({"type": "http.response.start", "status": 401, "headers": [(b"content-length", b"0")]})
                await send({"type": "http.response.body", "body": b""})
                return
        await self.app(scope, receive, send)


def build_card(host: str, port: int) -> AgentCard:
    base = f"http://{host}:{port}"
    return AgentCard(
        name="Echo",
        description="Echo and ask skills for interoperability tests",
        version="1.0.0",
        supported_interfaces=[
            AgentInterface(url=base, protocol_binding="JSONRPC", protocol_version="1.0"),
            AgentInterface(url=base + REST_PATH, protocol_binding="HTTP+JSON", protocol_version="1.0"),
        ],
        capabilities=AgentCapabilities(streaming=True, push_notifications=False),
        # The kit sends a part with an unknown media type and expects either success (CORE-SEND-003) or, unless
        # the card declares the type, an error (HTTP_JSON-STATUS-001). A server passes both by declaring "*/*".
        default_input_modes=["*/*"],
        default_output_modes=["text/plain"],
        skills=[
            AgentSkill(id="echo", name="Echo", description="Repeats the text it receives", tags=["echo", "tck"]),
            AgentSkill(
                id="ask",
                name="Ask",
                description="Asks for a name, then greets it",
                tags=["input-required"],
            ),
        ],
    )


def build_app(host: str, port: int, token: str | None):
    card = build_card(host, port)
    handler = DefaultRequestHandler(agent_executor=EchoExecutor(), task_store=InMemoryTaskStore(), agent_card=card)
    # The Agent Card routes come before the REST routes, which mount a catch-all tenant path.
    routes = [
        *create_jsonrpc_routes(request_handler=handler, rpc_url="/"),
        *create_agent_card_routes(agent_card=card),
        *create_rest_routes(request_handler=handler, path_prefix=REST_PATH),
    ]
    app = Starlette(routes=routes)
    return BearerToken(app, token) if token else app


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--token", default=os.environ.get("A2A_ECHO_TOKEN"), help="require this bearer token")
    args = parser.parse_args()
    uvicorn.run(build_app(args.host, args.port, args.token), host=args.host, port=args.port, log_level="warning")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Checks an A2A server with the a2a-python client.

Reads the Agent Card, sends a single-turn message, sends a streamed message and, when the card has a
skill tagged "input-required", answers an input-required task. The script exits with 1 when a check fails.

A message selects a skill with the metadata field "skillId".
"""

import argparse
import asyncio
import sys
import uuid

import httpx
from google.protobuf import json_format

from a2a.client import A2ACardResolver, ClientConfig, create_client
from a2a.helpers.proto_helpers import get_message_text
from a2a.types import Message, Part, Role, SendMessageRequest, TaskState

CARD_SUFFIX = "/.well-known/agent-card.json"
INPUT_REQUIRED_TAG = "input-required"
BINDINGS = {"jsonrpc": "JSONRPC", "http_json": "HTTP+JSON"}


class Failure(Exception):
    pass


def check(condition: bool, what: str) -> None:
    if not condition:
        raise Failure(what)
    print(f"  ok  {what}")


def user_message(text: str, skill: str | None, task=None) -> SendMessageRequest:
    message = Message(message_id=str(uuid.uuid4()), role=Role.ROLE_USER, parts=[Part(text=text)])
    if skill:
        json_format.ParseDict({"skillId": skill}, message.metadata)
    if task is not None:
        message.task_id = task.id
        message.context_id = task.context_id
    return SendMessageRequest(message=message)


def task_text(task) -> str:
    parts = [get_message_text(task.status.message)] if task.status.HasField("message") else []
    for artifact in task.artifacts:
        parts.extend(part.text for part in artifact.parts)
    return "".join(parts)


async def send(client, request):
    """Returns the last task or message the client yields."""
    result = None
    async for response in client.send_message(request):
        result = response.task if response.HasField("task") else response.message
    return result


async def run(args) -> None:
    base_url = args.card_url.removesuffix(CARD_SUFFIX)
    headers = {"Authorization": f"Bearer {args.token}"} if args.token else {}
    async with httpx.AsyncClient(headers=headers, timeout=30) as http:

        async def client_for(streaming: bool):
            config = ClientConfig(
                streaming=streaming,
                httpx_client=http,
                supported_protocol_bindings=[BINDINGS[args.binding]],
            )
            return await create_client(base_url, client_config=config)

        print(f"Agent Card at {base_url}")
        card = await A2ACardResolver(http, base_url).get_agent_card()
        check(bool(card.name), f"card has a name ({card.name})")
        check(len(card.supported_interfaces) > 0, "card declares at least one interface")
        check(len(card.skills) > 0, "card declares at least one skill")
        skill_ids = [skill.id for skill in card.skills]
        echo_skill = args.skill or skill_ids[0]
        check(echo_skill in skill_ids, f"skill {echo_skill} is on the card")

        print("Single-turn message")
        client = await client_for(False)
        result = await send(client, user_message("hello world", echo_skill))
        check(result is not None, "server answered")
        if hasattr(result, "status"):
            check(result.status.state == TaskState.TASK_STATE_COMPLETED, "task completed")
            text = task_text(result)
        else:
            text = get_message_text(result)
        check(bool(text), f"answer has text ({text!r})")
        if not args.no_echo_check:
            check("hello world" in text, "answer contains the sent text")
        await client.close()

        print("Streamed message")
        client = await client_for(True)
        check(card.capabilities.streaming, "card declares streaming")
        events, state, streamed = 0, None, []
        async for response in client.send_message(user_message("hello streaming world", echo_skill)):
            events += 1
            if response.HasField("status_update"):
                state = response.status_update.status.state
            elif response.HasField("artifact_update"):
                streamed.extend(part.text for part in response.artifact_update.artifact.parts)
            elif response.HasField("task"):
                state = response.task.status.state
        check(events > 1, f"client received several events ({events})")
        check(state == TaskState.TASK_STATE_COMPLETED, "stream ended with a completed task")
        if not args.no_echo_check:
            check("hello streaming world" in "".join(streamed), "streamed artifact contains the sent text")
        await client.close()

        input_skills = [s.id for s in card.skills if INPUT_REQUIRED_TAG in s.tags]
        if input_skills:
            print(f"Input-required task ({input_skills[0]})")
            client = await client_for(False)
            first = await send(client, user_message("start", input_skills[0]))
            check(first.status.state == TaskState.TASK_STATE_INPUT_REQUIRED, "task asks for input")
            second = await send(client, user_message("Ada", input_skills[0], task=first))
            check(second.id == first.id, "the answer continues the same task")
            check(second.status.state == TaskState.TASK_STATE_COMPLETED, "task completed after the answer")
            check("Ada" in task_text(second), f"result uses the answer ({task_text(second)!r})")
            await client.close()
        else:
            print(f"No skill tagged {INPUT_REQUIRED_TAG}, input-required check skipped")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("card_url", help="Agent Card URL or the agent's base URL")
    parser.add_argument("--binding", choices=list(BINDINGS), default="jsonrpc", help="protocol binding to use")
    parser.add_argument("--token", help="bearer token to send")
    parser.add_argument("--skill", help="skill for the single-turn and streamed checks; default is the first on the card")
    parser.add_argument("--no-echo-check", action="store_true", help="do not require the answer to contain the sent text")
    args = parser.parse_args()
    try:
        asyncio.run(run(args))
    except Failure as failure:
        print(f"FAILED: {failure}", file=sys.stderr)
        sys.exit(1)
    print("All checks passed")


if __name__ == "__main__":
    main()

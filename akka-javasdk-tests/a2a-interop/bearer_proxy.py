#!/usr/bin/env python3
"""Reverse proxy that adds a bearer token to every request, for agents whose A2A routes require one.

The compatibility kit has no option for credentials. Run the kit against this proxy instead of the agent.
The proxy keeps the Host header of the client, so an agent that builds its Agent Card URLs from the
request host writes the proxy's address into the card.
"""

import argparse

import httpx
import uvicorn
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import StreamingResponse
from starlette.routing import Route

HOP_BY_HOP = {"connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade", "content-length"}


def build_app(target: str, token: str, extra_headers: dict[str, str]) -> Starlette:
    client = httpx.AsyncClient(base_url=target, timeout=httpx.Timeout(10.0, read=None))

    async def forward(request: Request):
        headers = {k: v for k, v in request.headers.items() if k.lower() not in HOP_BY_HOP}
        headers["authorization"] = f"Bearer {token}"
        headers.update(extra_headers)
        upstream = client.build_request(
            request.method, request.url.path, params=request.query_params, headers=headers, content=await request.body()
        )
        response = await client.send(upstream, stream=True)
        response_headers = {k: v for k, v in response.headers.items() if k.lower() not in HOP_BY_HOP}
        return StreamingResponse(
            response.aiter_raw(), status_code=response.status_code, headers=response_headers,
            background=None if response.is_closed else _closer(response),
        )

    methods = ["GET", "POST", "PUT", "PATCH", "DELETE"]
    return Starlette(routes=[Route("/{path:path}", forward, methods=methods)])


def _closer(response: httpx.Response):
    from starlette.background import BackgroundTask

    return BackgroundTask(response.aclose)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, required=True, help="port to listen on")
    parser.add_argument("--target", required=True, help="base URL of the agent, for example http://localhost:9000")
    parser.add_argument("--token", required=True, help="bearer token to add")
    parser.add_argument("--header", action="append", default=[], help="another header to add, as name:value")
    args = parser.parse_args()
    extra = dict(h.split(":", 1) for h in args.header)
    uvicorn.run(build_app(args.target, args.token, extra), host="127.0.0.1", port=args.port, log_level="warning")


if __name__ == "__main__":
    main()

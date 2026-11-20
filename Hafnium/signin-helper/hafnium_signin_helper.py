"""Hafnium sign-in helper.

Mojang won't answer Cloudflare, so the Hafnium server can't check players who sign in
without a chat certificate. This program runs on another computer, like a Raspberry Pi.
It keeps one connection open to the Hafnium server, so no router or port setup is needed.
When the server asks whether a player really joined, it asks Mojang and passes the answer back.

Needs Python 3.9+ and the websockets package. Set HAFNIUM_VERIFIER_SECRET to the same
value as the Worker's VERIFIER_SECRET secret.
"""
import asyncio
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import websockets

SERVER = os.environ.get("HAFNIUM_SERVER", "wss://hafnium-server.rsc-hafnium.workers.dev/connect")
SECRET = os.environ.get("HAFNIUM_VERIFIER_SECRET", "")
NAME = re.compile(r"^[A-Za-z0-9_]{1,16}$")
SERVER_ID = re.compile(r"^[0-9a-f]{40}$")


def log(text):
    print(time.strftime("%Y-%m-%d %H:%M:%S"), text, flush=True)


def has_joined(username, server_id):
    """Asks Mojang whether this player just joined a server with this id."""
    query = urllib.parse.urlencode({"username": username, "serverId": server_id})
    request = urllib.request.Request(
        "https://sessionserver.mojang.com/session/minecraft/hasJoined?" + query,
        headers={"User-Agent": "hafnium-signin-helper"},
    )
    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            body = response.read()
            if response.status == 204 or not body:
                return 204, None
            return response.status, json.loads(body)
    except urllib.error.HTTPError as e:
        return e.code, None
    except Exception as e:
        log(f"Couldn't reach Mojang: {e}")
        return 0, None


async def serve():
    async with websockets.connect(SERVER, ping_interval=20, ping_timeout=20) as ws:
        await ws.send(json.dumps({"type": "verifier_hello", "secret": SECRET}))
        async for raw in ws:
            msg = json.loads(raw)
            kind = msg.get("type")
            if kind == "verifier_ready":
                log("Connected. Hafnium sign-ins without a chat certificate work now.")
            elif kind == "error":
                log(f"The server said: {msg.get('reason')}")
                if msg.get("reason") == "bad_secret":
                    log("The secret doesn't match the Worker's VERIFIER_SECRET.")
            elif kind == "verify":
                username = str(msg.get("username", ""))
                server_id = str(msg.get("serverId", ""))
                if NAME.match(username) and SERVER_ID.match(server_id):
                    status, profile = await asyncio.to_thread(has_joined, username, server_id)
                    log(f"Checked {username}: {'signed in' if status == 200 else 'not confirmed'} ({status})")
                else:
                    status, profile = 400, None
                await ws.send(json.dumps({"type": "verified", "id": msg.get("id"), "status": status, "profile": profile}))


async def main():
    if len(SECRET) < 16:
        sys.exit("Set HAFNIUM_VERIFIER_SECRET to the Worker's VERIFIER_SECRET (16 characters or more).")
    while True:
        try:
            await serve()
            log("The server closed the connection.")
        except Exception as e:
            log(f"Connection lost: {e}")
        await asyncio.sleep(5)


if __name__ == "__main__":
    asyncio.run(main())

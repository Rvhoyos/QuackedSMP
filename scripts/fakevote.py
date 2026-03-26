#!/usr/bin/env python3
"""
Send a fake NuVotifier v2 packet to a local or remote server for testing.

Usage:
  python3 fakevote.py <token> [username] [host] [port]

Defaults:
  username  TestPlayer
  host      127.0.0.1
  port      8192
"""
import sys
import socket
import hmac
import hashlib
import base64
import json

def send_vote(token: str, username: str, host: str, port: int) -> None:
    s = socket.create_connection((host, port), timeout=5)

    # 1. Read greeting: "VOTIFIER 2 <challenge>\n"
    greeting = b""
    while not greeting.endswith(b"\n"):
        chunk = s.recv(1)
        if not chunk:
            raise RuntimeError("Connection closed before greeting")
        greeting += chunk
    parts = greeting.decode().strip().split(" ")
    if len(parts) < 3 or parts[0] != "VOTIFIER":
        raise RuntimeError(f"Unexpected greeting: {greeting!r}")
    challenge = parts[2]
    print(f"  challenge : {challenge}")

    # 2. Build inner payload JSON
    inner = json.dumps({
        "serviceName": "QuackSMP-Test",
        "username": username,
        "address": "127.0.0.1",
        "timestamp": "0",
        "challenge": challenge,
    }, separators=(",", ":"))

    # 3. Sign with HMAC-SHA256
    sig = base64.b64encode(
        hmac.new(token.encode("utf-8"), inner.encode("utf-8"), hashlib.sha256).digest()
    ).decode()

    # 4. Build outer JSON
    outer = json.dumps({"payload": inner, "signature": sig},
                       separators=(",", ":")).encode("utf-8")

    # 5. Send: 2-byte magic + 2-byte big-endian length + payload
    packet = bytes([0x73, 0x3A]) + len(outer).to_bytes(2, "big") + outer
    s.sendall(packet)

    # 6. Read response
    response = s.recv(256).decode("utf-8").strip()
    s.close()
    print(f"  response  : {response}")
    if '"ok"' in response:
        print(f"Vote accepted for {username}!")
    else:
        print("Vote rejected — check token or server logs.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    token    = sys.argv[1]
    username = sys.argv[2] if len(sys.argv) > 2 else "TestPlayer"
    host     = sys.argv[3] if len(sys.argv) > 3 else "127.0.0.1"
    port     = int(sys.argv[4]) if len(sys.argv) > 4 else 8192

    print(f"Sending vote: {username} @ {host}:{port}")
    send_vote(token, username, host, port)

#!/usr/bin/env python3

from __future__ import annotations

import os
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DSL_DIR = ROOT / "targets" / "dsl"
DEFAULT_ADDR = "tcp://127.0.0.1:15334"
FEEDBACK_SAMPLE = b"MODE DEBUG\nAPPEND ipc_smoke\nCHECK OPEN\n"


def connect_addr(addr: str) -> socket.socket:
    if addr.startswith("tcp://"):
        host_port = addr[len("tcp://") :]
        host, port_text = host_port.rsplit(":", 1)
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((host, int(port_text)))
        return sock

    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    if addr.startswith("@"):
        sock.connect("\0" + addr[1:])
    else:
        sock.connect(addr)
    return sock


def recv_exact(sock: socket.socket, size: int) -> bytes:
    chunks: list[bytes] = []
    remaining = size
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise RuntimeError("server closed connection")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def wait_for_server(addr: str, deadline: float) -> socket.socket:
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            return connect_addr(addr)
        except OSError as exc:
            last_error = exc
            time.sleep(0.05)
    raise RuntimeError(f"could not connect to {addr}: {last_error}")


def request_candidate(sock: socket.socket, deadline: float) -> bytes:
    while time.monotonic() < deadline:
        sock.sendall(b"G")
        size = int.from_bytes(recv_exact(sock, 4), "big")
        if size:
            return recv_exact(sock, size)
        time.sleep(0.05)
    raise RuntimeError("worker did not serve a candidate before timeout")


def send_feedback(sock: socket.socket, payload: bytes) -> None:
    sock.sendall(b"A" + len(payload).to_bytes(4, "big") + payload)


def main() -> int:
    addr = os.environ.get("LLM_MUTATOR_ADDR", os.environ.get("LLM_MUTATOR_SOCK", DEFAULT_ADDR))

    with tempfile.TemporaryDirectory(prefix="llm-aflpp-ipc-smoke-") as tmp:
        discovered_dir = Path(tmp) / "discovered"
        env = os.environ.copy()
        env.update(
            {
                "LLM_MUTATOR_ADDR": addr,
                "LLM_MUTATOR_PROMPT_FILE": str(DSL_DIR / "prompt.txt"),
                "LLM_MUTATOR_SEED_DIR": str(DSL_DIR / "seeds"),
                "LLM_MUTATOR_DISCOVERED_DIR": str(discovered_dir),
                "LLM_MUTATOR_QUEUE_SIZE": env.get("LLM_MUTATOR_QUEUE_SIZE", "8"),
                "LLM_MUTATOR_WORKERS": env.get("LLM_MUTATOR_WORKERS", "1"),
                "PYTHONUNBUFFERED": "1",
            }
        )
        env["LLM_API_URL"] = ""

        proc = subprocess.Popen(
            [sys.executable, str(ROOT / "src" / "worker" / "llm_mutator_server.py")],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            env=env,
        )

        try:
            with wait_for_server(addr, time.monotonic() + 5.0) as sock:
                candidate = request_candidate(sock, time.monotonic() + 5.0)
                if not candidate:
                    raise RuntimeError("empty candidate")

                send_feedback(sock, FEEDBACK_SAMPLE)
                time.sleep(0.2)

            persisted = sorted(discovered_dir.glob("*"))
            if not persisted:
                raise RuntimeError("feedback sample was not persisted")

            print(
                "ipc smoke ok: "
                f"received {len(candidate)} bytes; "
                f"persisted feedback in {discovered_dir}"
            )
            return 0
        except Exception as exc:
            proc.terminate()
            try:
                output, _ = proc.communicate(timeout=2.0)
            except subprocess.TimeoutExpired:
                proc.kill()
                output, _ = proc.communicate(timeout=2.0)
            if output:
                print(output, file=sys.stderr, end="")
            print(f"ipc smoke failed: {exc}", file=sys.stderr)
            return 1
        finally:
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=2.0)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=2.0)


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3

from __future__ import annotations

import json
import os
import queue
import random
import socket
import threading
import time
import urllib.request
from pathlib import Path
from typing import Optional

ROOT = Path(__file__).resolve().parents[2]
ADDR = os.environ.get("LLM_MUTATOR_ADDR", os.environ.get("LLM_MUTATOR_SOCK", "tcp://127.0.0.1:15333"))
PROMPT_FILE = Path(os.environ.get("LLM_MUTATOR_PROMPT_FILE", str(ROOT / "targets" / "dsl" / "prompt.txt")))
SEED_DIR = Path(os.environ.get("LLM_MUTATOR_SEED_DIR", str(ROOT / "targets" / "dsl" / "seeds")))
DISCOVERED_DIR = Path(os.environ.get("LLM_MUTATOR_DISCOVERED_DIR", str(ROOT / "runtime" / "discovered")))
CANDIDATE_LOG_DIR_TEXT = os.environ.get("LLM_MUTATOR_LOG_CANDIDATES_DIR", "")
CANDIDATE_LOG_DIR = Path(CANDIDATE_LOG_DIR_TEXT) if CANDIDATE_LOG_DIR_TEXT else None

QUEUE_SIZE = int(os.environ.get("LLM_MUTATOR_QUEUE_SIZE", "128"))
NUM_WORKERS = int(os.environ.get("LLM_MUTATOR_WORKERS", "2"))
MAX_SAMPLE_SIZE = int(os.environ.get("LLM_MUTATOR_MAX_SAMPLE_SIZE", "65535"))
MAX_CANDIDATE_CHARS = int(os.environ.get("LLM_MUTATOR_MAX_CANDIDATE_CHARS", "2048"))
LLM_TIMEOUT = int(os.environ.get("LLM_API_TIMEOUT", "20"))

LLM_API_URL = os.environ.get("LLM_API_URL", "")
LLM_API_KEY = os.environ.get("LLM_API_KEY", "")
LLM_MODEL = os.environ.get("LLM_MODEL", "gpt-4.1-mini")
USE_REAL_LLM = bool(LLM_API_URL)

sample_queue: queue.Queue[bytes] = queue.Queue(maxsize=QUEUE_SIZE)
seed_lock = threading.Lock()
prompt_lock = threading.Lock()
candidate_log_lock = threading.Lock()
stop_event = threading.Event()

seed_corpus: list[bytes] = []
feedback_counter = 0
candidate_log_counter = 0
current_prompt = ""
current_prompt_mtime = 0.0


def log(msg: str) -> None:
    print(f"[server] {msg}", flush=True)


def addr_display_name() -> str:
    return ADDR


def create_server_socket() -> socket.socket:
    if ADDR.startswith("tcp://"):
        host_port = ADDR[len("tcp://") :]
        host, port_text = host_port.rsplit(":", 1)
        port = int(port_text)

        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((host, port))
        return server

    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    if ADDR.startswith("@"):
        server.bind("\0" + ADDR[1:])
    else:
        path = Path(ADDR)
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.exists():
            path.unlink()
        server.bind(str(path))
    return server


def clamp_sample(data: bytes) -> bytes:
    return data[:MAX_SAMPLE_SIZE]


def load_prompt_if_changed() -> None:
    global current_prompt, current_prompt_mtime

    try:
        st = PROMPT_FILE.stat()
    except FileNotFoundError:
        return

    if st.st_mtime <= current_prompt_mtime:
        return

    text = PROMPT_FILE.read_text(encoding="utf-8", errors="replace")
    with prompt_lock:
        current_prompt = text
        current_prompt_mtime = st.st_mtime
    log(f"reloaded prompt from {PROMPT_FILE}")


def load_seed_corpus() -> None:
    items: list[bytes] = []

    for base_dir in (SEED_DIR, DISCOVERED_DIR):
        if not base_dir.exists():
            continue

        for path in sorted(base_dir.glob("*")):
            if not path.is_file():
                continue
            try:
                data = path.read_bytes()
            except OSError:
                continue
            if data:
                items.append(clamp_sample(data))

    with seed_lock:
        seed_corpus[:] = items

    log(f"loaded {len(items)} seed samples")


def persist_feedback_sample(data: bytes) -> None:
    global feedback_counter

    DISCOVERED_DIR.mkdir(parents=True, exist_ok=True)
    feedback_counter += 1
    ts = int(time.time() * 1000)
    path = DISCOVERED_DIR / f"queue_{ts}_{feedback_counter:05d}.txt"

    try:
        path.write_bytes(clamp_sample(data))
    except OSError:
        return


def register_feedback_sample(data: bytes) -> None:
    data = clamp_sample(data)
    if not data:
        return

    persist_feedback_sample(data)
    with seed_lock:
        seed_corpus.append(data)
    log(f"accepted feedback sample ({len(data)} bytes)")


def persist_generated_candidate(data: bytes, worker_id: int) -> None:
    global candidate_log_counter

    if CANDIDATE_LOG_DIR is None:
        return

    try:
        CANDIDATE_LOG_DIR.mkdir(parents=True, exist_ok=True)
    except OSError:
        return

    with candidate_log_lock:
        candidate_log_counter += 1
        counter = candidate_log_counter

    ts = int(time.time() * 1000)
    mode = "real" if USE_REAL_LLM else "fake"
    path = CANDIDATE_LOG_DIR / f"candidate_{ts}_{counter:05d}_{mode}_w{worker_id}.txt"

    try:
        path.write_bytes(clamp_sample(data))
    except OSError:
        return


def build_messages() -> list[dict[str, str]]:
    with prompt_lock:
        prompt = current_prompt.strip()

    with seed_lock:
        corpus = list(seed_corpus)

    system = (
        "You generate one compact fuzzing input for a toy line-based DSL target. "
        "Return only the program text with newline-separated commands. "
        "Do not use markdown, explanations, bullets, or code fences."
    )

    user_parts = [prompt] if prompt else ["Generate one valid DSL program."]

    if corpus:
        chosen = random.sample(corpus, k=min(3, len(corpus)))
        examples = "\n\n".join(
            f"Example #{idx + 1}:\n{item.decode('utf-8', errors='replace')[:400]}"
            for idx, item in enumerate(chosen)
        )
        user_parts.append("Seed examples:\n" + examples)

    user_parts.append(
        "Produce exactly one new candidate under 2 KB. "
        "Keep syntax plausible and vary commands, constants, and command order."
    )

    return [
        {"role": "system", "content": system},
        {"role": "user", "content": "\n\n".join(user_parts)},
    ]


def call_openai_compatible() -> bytes:
    payload = {
        "model": LLM_MODEL,
        "messages": build_messages(),
        "temperature": 0.9,
        "max_tokens": 400,
    }

    headers = {"Content-Type": "application/json"}
    if LLM_API_KEY:
        headers["Authorization"] = f"Bearer {LLM_API_KEY}"

    req = urllib.request.Request(
        LLM_API_URL,
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers=headers,
    )

    with urllib.request.urlopen(req, timeout=LLM_TIMEOUT) as resp:
        raw = json.loads(resp.read().decode("utf-8", errors="replace"))

    text = raw["choices"][0]["message"]["content"]
    if not isinstance(text, str):
        raise ValueError("unexpected response shape from LLM endpoint")

    return clamp_sample(text.encode("utf-8", errors="replace")[:MAX_CANDIDATE_CHARS])


def fake_generator() -> bytes:
    regs = ["A", "B", "C"]
    modes = ["FAST", "SLOW", "DEBUG"]
    checks = ["HELLO", "MAGIC", "PLEASE", "FIZZ", "OPEN", "WORLD"]
    append_values = ["open", "world", "alpha", "beta", "trace", "seed"]
    crash_flags = ["NO", "MAYBE", "NOW"]

    lines: list[str] = [f"MODE {random.choice(modes)}"]

    for _ in range(random.randint(2, 8)):
        choice = random.choice(["set", "copy", "xor", "append", "check", "loop"])
        if choice == "set":
            lines.append(f"SET {random.choice(regs)} {random.randint(0, 20000)}")
        elif choice == "copy":
            lines.append(f"COPY {random.choice(regs)} {random.choice(regs)}")
        elif choice == "xor":
            lines.append(f"XOR {random.choice(regs)} {random.randint(0, 20000)}")
        elif choice == "append":
            lines.append(f"APPEND {random.choice(append_values)}")
        elif choice == "check":
            lines.append(f"CHECK {random.choice(checks)}")
        elif choice == "loop":
            lines.append(f"LOOP {random.randint(1, 12)}")

    if random.random() < 0.35:
        lines.append(f"CRASH {random.choice(crash_flags)}")

    return clamp_sample(("\n".join(lines) + "\n").encode("utf-8"))


def produce_one() -> Optional[bytes]:
    load_prompt_if_changed()

    if USE_REAL_LLM:
        return call_openai_compatible()
    return fake_generator()


def producer_worker(worker_id: int) -> None:
    log(f"producer-{worker_id} started ({'real-llm' if USE_REAL_LLM else 'fake'} mode)")

    while not stop_event.is_set():
        if sample_queue.full():
            time.sleep(0.05)
            continue

        try:
            data = produce_one()
            if not data:
                time.sleep(0.1)
                continue
            sample_queue.put(data, timeout=0.2)
            persist_generated_candidate(data, worker_id)
        except Exception as exc:
            log(f"producer-{worker_id} error: {exc}")
            time.sleep(1.0)


def recv_exact(conn: socket.socket, size: int) -> bytes:
    chunks: list[bytes] = []
    remaining = size

    while remaining > 0:
        chunk = conn.recv(remaining)
        if not chunk:
            raise ConnectionError("peer closed connection")
        chunks.append(chunk)
        remaining -= len(chunk)

    return b"".join(chunks)


def handle_client(conn: socket.socket) -> None:
    with conn:
        while not stop_event.is_set():
            op = conn.recv(1)
            if not op:
                return

            if op == b"G":
                try:
                    data = sample_queue.get_nowait()
                except queue.Empty:
                    conn.sendall((0).to_bytes(4, "big"))
                    continue

                conn.sendall(len(data).to_bytes(4, "big"))
                conn.sendall(data)
                continue

            if op == b"A":
                raw_len = recv_exact(conn, 4)
                data_len = int.from_bytes(raw_len, "big")
                if data_len <= 0 or data_len > MAX_SAMPLE_SIZE:
                    raise ValueError(f"invalid feedback length: {data_len}")
                payload = recv_exact(conn, data_len)
                register_feedback_sample(payload)
                continue

            raise ValueError(f"unknown opcode: {op!r}")


def serve() -> None:
    DISCOVERED_DIR.mkdir(parents=True, exist_ok=True)
    if CANDIDATE_LOG_DIR is not None:
        CANDIDATE_LOG_DIR.mkdir(parents=True, exist_ok=True)
        log(f"logging generated candidates to {CANDIDATE_LOG_DIR}")

    load_prompt_if_changed()
    load_seed_corpus()

    workers = [
        threading.Thread(target=producer_worker, args=(i,), daemon=True)
        for i in range(NUM_WORKERS)
    ]
    for worker in workers:
        worker.start()

    with create_server_socket() as server:
        server.listen(16)
        log(f"listening on {addr_display_name()}")

        while not stop_event.is_set():
            conn, _ = server.accept()
            thread = threading.Thread(target=handle_client, args=(conn,), daemon=True)
            thread.start()


def main() -> int:
    try:
        serve()
    except KeyboardInterrupt:
        log("stopping")
        return 130
    finally:
        stop_event.set()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

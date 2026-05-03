# AFL++ + async LLM mutator: минимальная реализация

Ниже — каркас системы, где:

- **AFL++ custom mutator** никогда не ждёт LLM в горячем цикле;
- отдельный **LLM-воркер** заранее генерирует кандидатов и держит их в локальной очереди;
- мутатор быстро берёт уже готовый input из локального Unix socket и при промахе делает дешёвую локальную мутацию.

Это ровно тот компромисс, который нужен для AFL++: custom mutator работает в первом недетерминированном stage, а производительность в AFL++ критична, особенно если цель уже ускорена persistent mode и stdin-вводом. Для C/C++ mutator API и соответствующих хуков используются `afl_custom_init`, `afl_custom_fuzz`, `afl_custom_fuzz_count`, `afl_custom_post_process`, `afl_custom_describe`, `afl_custom_queue_new_entry`, `afl_custom_deinit`. Также имеет смысл держать цель в persistent mode и читать ввод через stdin, чтобы не терять скорость.

## Структура

```text
.
├── afl_llm_mutator.c
├── llm_mutator_server.py
├── harness.c
├── Makefile
└── prompt.txt
```

---

## `afl_llm_mutator.c`

```c
// afl_llm_mutator.c
// Минимальный AFL++ custom mutator, который:
// 1) быстро опрашивает локальный Unix socket за уже готовым кандидатом,
// 2) если кандидат есть — смешивает его с текущим seed,
// 3) если кандидата нет — делает дешёвую локальную мутацию.
//
// Важно: никаких синхронных вызовов LLM внутри afl_custom_fuzz().

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

// Нам не нужно знать внутренности afl_state_t, если мы его не разыменовываем.
typedef struct afl_state afl_state_t;

typedef struct {
  int sock;
  unsigned int seed;
  uint8_t *out_buf;
  size_t out_cap;
  char sock_path[108];
  char last_desc[256];
  uint64_t requests;
  uint64_t hits;
  uint64_t misses;
  time_t last_reconnect_attempt;
} my_mutator_t;

static uint32_t rng_next(uint32_t *s) {
  uint32_t x = *s;
  x ^= x << 13;
  x ^= x >> 17;
  x ^= x << 5;
  *s = x;
  return x;
}

static size_t min_size(size_t a, size_t b) { return a < b ? a : b; }

static int connect_unix_socket(const char *path) {
  int fd = socket(AF_UNIX, SOCK_STREAM, 0);
  if (fd < 0) return -1;

  struct sockaddr_un addr;
  memset(&addr, 0, sizeof(addr));
  addr.sun_family = AF_UNIX;
  strncpy(addr.sun_path, path, sizeof(addr.sun_path) - 1);

  if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) != 0) {
    close(fd);
    return -1;
  }

  // Делаем неблокирующим, чтобы AFL не ждал воркер.
  int flags = fcntl(fd, F_GETFL, 0);
  if (flags >= 0) fcntl(fd, F_SETFL, flags | O_NONBLOCK);
  return fd;
}

static void maybe_reconnect(my_mutator_t *m) {
  time_t now = time(NULL);
  if (m->sock >= 0) return;
  if (now == m->last_reconnect_attempt) return;
  m->last_reconnect_attempt = now;
  m->sock = connect_unix_socket(m->sock_path);
}

static int send_all(int fd, const void *buf, size_t len) {
  const uint8_t *p = (const uint8_t *)buf;
  while (len > 0) {
    ssize_t n = send(fd, p, len, MSG_NOSIGNAL);
    if (n < 0) {
      if (errno == EINTR) continue;
      if (errno == EAGAIN || errno == EWOULDBLOCK) return -2;
      return -1;
    }
    p += (size_t)n;
    len -= (size_t)n;
  }
  return 0;
}

static int recv_all_deadline(int fd, void *buf, size_t len, int max_spins) {
  uint8_t *p = (uint8_t *)buf;
  int spins = 0;
  while (len > 0) {
    ssize_t n = recv(fd, p, len, 0);
    if (n == 0) return -1;
    if (n < 0) {
      if (errno == EINTR) continue;
      if (errno == EAGAIN || errno == EWOULDBLOCK) {
        if (++spins >= max_spins) return -2;
        usleep(1000); // 1ms, очень короткий polling.
        continue;
      }
      return -1;
    }
    p += (size_t)n;
    len -= (size_t)n;
  }
  return 0;
}

static int fetch_candidate(my_mutator_t *m, uint8_t *dst, size_t *dst_len,
                           size_t max_size) {
  // Протокол:
  // клиент -> сервер: 1 байт opcode 'G'
  // сервер -> клиент: 4 байта big-endian length; 0 == MISS
  // затем payload из length байт.
  maybe_reconnect(m);
  if (m->sock < 0) return 0;

  uint8_t op = 'G';
  if (send_all(m->sock, &op, 1) != 0) {
    close(m->sock);
    m->sock = -1;
    return 0;
  }

  uint8_t hdr[4];
  int rc = recv_all_deadline(m->sock, hdr, sizeof(hdr), 2);
  if (rc != 0) {
    close(m->sock);
    m->sock = -1;
    return 0;
  }

  uint32_t n = ((uint32_t)hdr[0] << 24) |
               ((uint32_t)hdr[1] << 16) |
               ((uint32_t)hdr[2] << 8) |
               ((uint32_t)hdr[3]);
  if (n == 0) return 0;

  if (n > max_size) n = (uint32_t)max_size;
  rc = recv_all_deadline(m->sock, dst, n, 4);
  if (rc != 0) {
    close(m->sock);
    m->sock = -1;
    return 0;
  }

  *dst_len = (size_t)n;
  return 1;
}

static void cheap_fallback_mutation(my_mutator_t *m, const uint8_t *buf,
                                    size_t buf_size, size_t max_size,
                                    size_t *out_len) {
  size_t n = min_size(buf_size, max_size);
  if (n == 0) {
    m->out_buf[0] = 'A';
    *out_len = 1;
    snprintf(m->last_desc, sizeof(m->last_desc),
             "fallback:init-single-byte");
    return;
  }

  memcpy(m->out_buf, buf, n);

  uint32_t st = m->seed ^ (uint32_t)m->requests;
  int ops = 1 + (rng_next(&st) % 4);
  for (int i = 0; i < ops; ++i) {
    uint32_t choice = rng_next(&st) % 3;
    if (choice == 0 && n > 0) {
      size_t pos = rng_next(&st) % n;
      m->out_buf[pos] ^= (uint8_t)(1u << (rng_next(&st) % 8));
    } else if (choice == 1 && n > 0) {
      size_t pos = rng_next(&st) % n;
      m->out_buf[pos] += (uint8_t)(1 + (rng_next(&st) % 15));
    } else if (choice == 2 && n + 1 < max_size) {
      size_t pos = rng_next(&st) % (n + 1);
      memmove(m->out_buf + pos + 1, m->out_buf + pos, n - pos);
      m->out_buf[pos] = (uint8_t)(32 + (rng_next(&st) % 95));
      n++;
    }
  }

  *out_len = n;
  snprintf(m->last_desc, sizeof(m->last_desc),
           "fallback:cheap-mutation ops=%d", ops);
}

static void blend_with_seed(my_mutator_t *m, const uint8_t *seed_buf,
                            size_t seed_len, const uint8_t *cand_buf,
                            size_t cand_len, size_t max_size,
                            size_t *out_len) {
  // Простая комбинация: префикс от seed + суффикс от LLM-кандидата.
  if (cand_len == 0) {
    cheap_fallback_mutation(m, seed_buf, seed_len, max_size, out_len);
    return;
  }

  size_t total = min_size(cand_len, max_size);
  memcpy(m->out_buf, cand_buf, total);

  if (seed_len > 0 && total > 2) {
    size_t prefix = min_size(seed_len / 2, total / 2);
    memcpy(m->out_buf, seed_buf, prefix);
  }

  *out_len = total;
  snprintf(m->last_desc, sizeof(m->last_desc),
           "llm-cache-hit:size=%zu", cand_len);
}

void *afl_custom_init(afl_state_t *afl, unsigned int seed) {
  (void)afl;

  my_mutator_t *m = (my_mutator_t *)calloc(1, sizeof(my_mutator_t));
  if (!m) return NULL;

  m->seed = seed ? seed : 0xA5A5A5A5u;
  m->sock = -1;
  m->out_cap = 1024 * 1024;  // 1 MiB scratch buffer
  m->out_buf = (uint8_t *)malloc(m->out_cap);
  if (!m->out_buf) {
    free(m);
    return NULL;
  }

  const char *sock = getenv("LLM_MUTATOR_SOCK");
  if (!sock) sock = "/tmp/afl_llm_mutator.sock";
  strncpy(m->sock_path, sock, sizeof(m->sock_path) - 1);
  m->sock = connect_unix_socket(m->sock_path);

  snprintf(m->last_desc, sizeof(m->last_desc), "init");
  return m;
}

unsigned int afl_custom_fuzz_count(void *data, const unsigned char *buf,
                                   size_t buf_size) {
  (void)data;
  (void)buf;
  (void)buf_size;
  // Ограничиваем число вызовов на queue entry, чтобы LLM-часть не раздувала stage.
  return 4;
}

void afl_custom_splice_optout(void *data) {
  (void)data;
}

size_t afl_custom_fuzz(void *data, unsigned char *buf, size_t buf_size,
                       unsigned char **out_buf, unsigned char *add_buf,
                       size_t add_buf_size, size_t max_size) {
  (void)add_buf;
  (void)add_buf_size;

  my_mutator_t *m = (my_mutator_t *)data;
  if (!m || !out_buf) return 0;

  m->requests++;
  if (max_size == 0) return 0;
  if (max_size > m->out_cap) max_size = m->out_cap;

  uint8_t candidate[65536];
  size_t candidate_len = 0;
  int hit = fetch_candidate(m, candidate, &candidate_len,
                            min_size(sizeof(candidate), max_size));

  size_t produced = 0;
  if (hit) {
    m->hits++;
    blend_with_seed(m, buf, buf_size, candidate, candidate_len,
                    max_size, &produced);
  } else {
    m->misses++;
    cheap_fallback_mutation(m, buf, buf_size, max_size, &produced);
  }

  *out_buf = m->out_buf;
  return produced;
}

const char *afl_custom_describe(void *data, size_t max_description_len) {
  my_mutator_t *m = (my_mutator_t *)data;
  if (!m) return "llm-mutator:null";
  if (max_description_len > 0 && max_description_len < sizeof(m->last_desc)) {
    m->last_desc[max_description_len - 1] = '\0';
  }
  return m->last_desc;
}

size_t afl_custom_post_process(void *data, unsigned char *buf, size_t buf_size,
                               unsigned char **out_buf) {
  // Здесь можно делать трансляцию из внутреннего представления в формат таргета.
  // В демо мы просто пропускаем bytes как есть.
  (void)data;
  *out_buf = buf;
  return buf_size;
}

uint8_t afl_custom_queue_get(void *data, const uint8_t *filename) {
  (void)data;
  (void)filename;
  return 1;
}

uint8_t afl_custom_havoc_mutation_probability(void *data) {
  (void)data;
  return 8; // слегка чаще дефолта 6%
}

size_t afl_custom_havoc_mutation(void *data, uint8_t *buf, size_t buf_size,
                                 uint8_t **out_buf, size_t max_size) {
  my_mutator_t *m = (my_mutator_t *)data;
  if (!m || !buf || !out_buf) return 0;

  size_t n = min_size(buf_size, max_size);
  memcpy(m->out_buf, buf, n);
  if (n > 0) {
    size_t pos = (size_t)(m->seed + m->requests) % n;
    m->out_buf[pos] ^= 0x20;
  }
  *out_buf = m->out_buf;
  snprintf(m->last_desc, sizeof(m->last_desc), "havoc:tweak-byte");
  return n;
}

void afl_custom_deinit(void *data) {
  my_mutator_t *m = (my_mutator_t *)data;
  if (!m) return;
  if (m->sock >= 0) close(m->sock);
  free(m->out_buf);
  free(m);
}
```

---

## `llm_mutator_server.py`

```python
#!/usr/bin/env python3
# llm_mutator_server.py
# Локальный сервер с Unix socket, который заранее держит очередь сэмплов,
# сгенерированных из prompt.txt. AFL-мутатор делает только короткий polling.
#
# Есть 2 режима:
# 1) stub/fake generator — для локальной отладки без LLM API,
# 2) OpenAI-compatible HTTP endpoint — если задать LLM_API_URL / KEY / MODEL.

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

SOCK_PATH = os.environ.get("LLM_MUTATOR_SOCK", "/tmp/afl_llm_mutator.sock")
PROMPT_FILE = Path(os.environ.get("LLM_MUTATOR_PROMPT_FILE", "./prompt.txt"))
SEED_DIR = Path(os.environ.get("LLM_MUTATOR_SEED_DIR", "./seeds"))
QUEUE_SIZE = int(os.environ.get("LLM_MUTATOR_QUEUE_SIZE", "128"))
NUM_WORKERS = int(os.environ.get("LLM_MUTATOR_WORKERS", "2"))
USE_REAL_LLM = os.environ.get("LLM_API_URL") is not None
LLM_API_URL = os.environ.get("LLM_API_URL", "")
LLM_API_KEY = os.environ.get("LLM_API_KEY", "")
LLM_MODEL = os.environ.get("LLM_MODEL", "gpt-4.1-mini")

sample_queue: queue.Queue[bytes] = queue.Queue(maxsize=QUEUE_SIZE)
prompt_lock = threading.Lock()
current_prompt = ""
current_prompt_mtime = 0.0
seed_corpus: list[bytes] = []
stop_event = threading.Event()


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
    print(f"[server] reloaded prompt from {PROMPT_FILE}")


def load_seed_corpus() -> None:
    global seed_corpus
    if not SEED_DIR.exists():
        seed_corpus = []
        return

    items: list[bytes] = []
    for path in sorted(SEED_DIR.glob("*")):
        if path.is_file():
            try:
                data = path.read_bytes()
                if data:
                    items.append(data[:65535])
            except Exception:
                pass
    seed_corpus = items
    print(f"[server] loaded {len(seed_corpus)} seed samples from {SEED_DIR}")


def build_messages() -> list[dict]:
    with prompt_lock:
        prompt = current_prompt.strip()

    system = (
        "You generate compact fuzzing inputs for a target program. "
        "Return ONLY the raw candidate input as plain text, no markdown, no explanation. "
        "Prefer short but diverse inputs that are syntactically plausible."
    )

    user_parts = [prompt] if prompt else ["Generate one compact fuzzing candidate."]

    if seed_corpus:
        few = random.sample(seed_corpus, k=min(3, len(seed_corpus)))
        examples = "\n\n".join(
            f"Example #{i+1}:\n{b.decode('utf-8', errors='replace')[:400]}"
            for i, b in enumerate(few)
        )
        user_parts.append("Here are some seed examples:\n" + examples)

    user_parts.append(
        "Produce exactly one new candidate. Keep it under 4KB unless the prompt explicitly requires more."
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
        "max_tokens": 512,
    }
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        LLM_API_URL,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {LLM_API_KEY}",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        raw = json.loads(resp.read().decode("utf-8", errors="replace"))

    # OpenAI-compatible shape:
    text = raw["choices"][0]["message"]["content"]
    return text.encode("utf-8", errors="replace")[:65535]


def fake_generator() -> bytes:
    # Заглушка, чтобы можно было отладить систему без внешнего API.
    verbs = ["SET", "ADD", "SUB", "MUL", "DIV", "CHECK", "MODE", "CRASH"]
    regs = ["A", "B", "C"]
    lines = []
    n = random.randint(1, 8)
    for _ in range(n):
        v = random.choice(verbs)
        if v in {"SET", "ADD", "SUB", "MUL", "DIV"}:
            lines.append(f"{v} {random.choice(regs)} {random.randint(0, 4096)}")
        elif v == "CHECK":
            choice = random.choice(["HELLO", "FUZZ", "SECRET", "MAGIC", "PLEASE"])
            lines.append(f"CHECK {choice}")
        elif v == "MODE":
            lines.append(f"MODE {random.choice(['FAST', 'SLOW', 'DEBUG'])}")
        elif v == "CRASH":
            lines.append(f"CRASH {random.choice(['NO', 'MAYBE', 'PLEASE'])}")
    return ("\n".join(lines) + "\n").encode("utf-8")


def produce_one() -> Optional[bytes]:
    load_prompt_if_changed()
    if USE_REAL_LLM:
        try:
            return call_openai_compatible()
        except Exception as e:
            print(f"[server] LLM call failed: {e}")
            return None
    return fake_generator()


def producer_loop(worker_id: int) -> None:
    while not stop_event.is_set():
        if sample_queue.full():
            time.sleep(0.05)
            continue
        data = produce_one()
        if not data:
            time.sleep(0.2)
            continue
        try:
            sample_queue.put(data, timeout=0.2)
        except queue.Full:
            pass


def handle_client(conn: socket.socket) -> None:
    try:
        while not stop_event.is_set():
            op = conn.recv(1)
            if not op:
                return
            if op != b"G":
                return

            try:
                sample = sample_queue.get_nowait()
            except queue.Empty:
                conn.sendall((0).to_bytes(4, "big"))
                continue

            conn.sendall(len(sample).to_bytes(4, "big"))
            conn.sendall(sample)
    except (BrokenPipeError, ConnectionResetError):
        return
    finally:
        try:
            conn.close()
        except Exception:
            pass


def server_loop() -> None:
    if os.path.exists(SOCK_PATH):
        os.unlink(SOCK_PATH)

    srv = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    srv.bind(SOCK_PATH)
    srv.listen(64)
    os.chmod(SOCK_PATH, 0o666)
    print(f"[server] listening on {SOCK_PATH}")

    try:
        while not stop_event.is_set():
            conn, _ = srv.accept()
            t = threading.Thread(target=handle_client, args=(conn,), daemon=True)
            t.start()
    finally:
        srv.close()
        try:
            os.unlink(SOCK_PATH)
        except FileNotFoundError:
            pass


def main() -> None:
    load_prompt_if_changed()
    load_seed_corpus()

    for i in range(NUM_WORKERS):
        t = threading.Thread(target=producer_loop, args=(i,), daemon=True)
        t.start()

    server_loop()


if __name__ == "__main__":
    main()
```

---

## `harness.c`

```c
// harness.c
// Игрушечная цель для локальной отладки.
// Формат входа — строки с командами.
// Идея: LLM по prompt-описанию может быстрее начать генерировать валидные команды,
// чем чисто байтовые мутации.

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef __AFL_FUZZ_TESTCASE_LEN
#define __AFL_FUZZ_TESTCASE_LEN (len)
#endif

static int parse_u32(const char *s, uint32_t *out) {
  char *end = NULL;
  unsigned long v = strtoul(s, &end, 10);
  if (!s || *s == '\0' || !end || *end != '\0') return 0;
  *out = (uint32_t)v;
  return 1;
}

static int reg_index(const char *s) {
  if (!strcmp(s, "A")) return 0;
  if (!strcmp(s, "B")) return 1;
  if (!strcmp(s, "C")) return 2;
  return -1;
}

static void crash_now(void) {
  volatile uint32_t *p = (volatile uint32_t *)0;
  *p = 0x1337;
}

static void run_one(const uint8_t *data, size_t len) {
  char *buf = (char *)malloc(len + 1);
  if (!buf) return;
  memcpy(buf, data, len);
  buf[len] = '\0';

  uint32_t r[3] = {0, 0, 0};
  int debug = 0;

  char *save1 = NULL;
  for (char *line = strtok_r(buf, "\n", &save1);
       line != NULL;
       line = strtok_r(NULL, "\n", &save1)) {

    char *save2 = NULL;
    char *op = strtok_r(line, " ", &save2);
    if (!op) continue;

    if (!strcmp(op, "MODE")) {
      char *arg = strtok_r(NULL, " ", &save2);
      if (arg && !strcmp(arg, "DEBUG")) debug = 1;
    } else if (!strcmp(op, "SET")) {
      char *reg = strtok_r(NULL, " ", &save2);
      char *val = strtok_r(NULL, " ", &save2);
      int idx = reg ? reg_index(reg) : -1;
      uint32_t v = 0;
      if (idx >= 0 && val && parse_u32(val, &v)) r[idx] = v;
    } else if (!strcmp(op, "ADD")) {
      char *reg = strtok_r(NULL, " ", &save2);
      char *val = strtok_r(NULL, " ", &save2);
      int idx = reg ? reg_index(reg) : -1;
      uint32_t v = 0;
      if (idx >= 0 && val && parse_u32(val, &v)) r[idx] += v;
    } else if (!strcmp(op, "SUB")) {
      char *reg = strtok_r(NULL, " ", &save2);
      char *val = strtok_r(NULL, " ", &save2);
      int idx = reg ? reg_index(reg) : -1;
      uint32_t v = 0;
      if (idx >= 0 && val && parse_u32(val, &v)) r[idx] -= v;
    } else if (!strcmp(op, "MUL")) {
      char *reg = strtok_r(NULL, " ", &save2);
      char *val = strtok_r(NULL, " ", &save2);
      int idx = reg ? reg_index(reg) : -1;
      uint32_t v = 0;
      if (idx >= 0 && val && parse_u32(val, &v)) r[idx] *= v;
    } else if (!strcmp(op, "DIV")) {
      char *reg = strtok_r(NULL, " ", &save2);
      char *val = strtok_r(NULL, " ", &save2);
      int idx = reg ? reg_index(reg) : -1;
      uint32_t v = 0;
      if (idx >= 0 && val && parse_u32(val, &v) && v != 0) r[idx] /= v;
    } else if (!strcmp(op, "CHECK")) {
      char *arg = strtok_r(NULL, " ", &save2);
      if (!arg) continue;

      if (!strcmp(arg, "SECRET") && r[0] == 31337) {
        if (debug) {
          // дополнительная глубокая ветка
          if (r[1] == 0xC0FFEEu && r[2] == 7u) {
            crash_now();
          }
        }
      }

      if (!strcmp(arg, "MAGIC") && r[0] == 0x41414141u) {
        if ((r[1] ^ r[2]) == 0xDEADBEEFu) {
          crash_now();
        }
      }
    } else if (!strcmp(op, "CRASH")) {
      char *arg = strtok_r(NULL, " ", &save2);
      if (arg && !strcmp(arg, "PLEASE") && debug && r[0] == 1337) {
        crash_now();
      }
    }
  }

  free(buf);
}

int main(void) {
#ifdef __AFL_HAVE_MANUAL_CONTROL
  __AFL_INIT();
#endif

  static uint8_t buf[1 << 16];

  while (__AFL_LOOP(1000)) {
    ssize_t len = read(0, buf, sizeof(buf));
    if (len > 0) run_one(buf, (size_t)len);
  }

  return 0;
}
```

---

## `Makefile`

```make
AFL_CC ?= afl-clang-fast
CC ?= cc
CFLAGS ?= -O2 -g -Wall -Wextra

all: harness afl_llm_mutator.so

harness: harness.c
	$(AFL_CC) $(CFLAGS) -o $@ $<

afl_llm_mutator.so: afl_llm_mutator.c
	$(CC) $(CFLAGS) -fPIC -shared -o $@ $<

clean:
	rm -f harness afl_llm_mutator.so
```

---

## `prompt.txt`

```text
The target accepts line-based commands.
Generate inputs made of 1 to 8 lines.
Each line should be one of:
- MODE FAST
- MODE SLOW
- MODE DEBUG
- SET <REG> <INT>
- ADD <REG> <INT>
- SUB <REG> <INT>
- MUL <REG> <INT>
- DIV <REG> <INT>
- CHECK SECRET
- CHECK MAGIC
- CRASH PLEASE
Where <REG> is A, B, or C.
Prefer semantically plausible sequences that mutate internal state before checks.
Occasionally try edge numeric values.
Return plain text only.
```

---

## Как запускать

```bash
mkdir -p in seeds out
printf 'MODE DEBUG\nSET A 1\n' > in/seed1.txt
cp in/seed1.txt seeds/

python3 llm_mutator_server.py
```

В другом терминале:

```bash
export LLM_MUTATOR_SOCK=/tmp/afl_llm_mutator.sock
export LLM_MUTATOR_PROMPT_FILE=./prompt.txt
export LLM_MUTATOR_SEED_DIR=./seeds
export AFL_CUSTOM_MUTATOR_LIBRARY=$PWD/afl_llm_mutator.so
# Если обычные stage ломают формат слишком сильно:
# export AFL_CUSTOM_MUTATOR_ONLY=1

make
afl-fuzz -i in -o out -- ./harness
```

Если ты используешь реальный OpenAI-compatible endpoint, добавь:

```bash
export LLM_API_URL=http://127.0.0.1:8000/v1/chat/completions
export LLM_API_KEY=your_key
export LLM_MODEL=gpt-4.1-mini
python3 llm_mutator_server.py
```

---

## Что здесь можно улучшить дальше

1. **Обратная связь от AFL++ в воркер**
   - подключить `queue_new_entry()` и отправлять в сервер новые интересные кейсы,
   - сервер может использовать их как fresh exemplars.

2. **Более умная стратегия генерации**
   - разделить prompt на system + task + constraints,
   - добавлять успешные seed-и и crash-и в few-shot контекст.

3. **Структурное post_process()**
   - очень полезно, если LLM генерирует внутренний DSL/AST/JSON, а target ждёт другой wire-format.

4. **Точный контроль затрат**
   - лимиты на количество генераций в секунду,
   - отдельный rate limiter,
   - отдельный fallback на полностью локальный grammar-based mutator.

5. **Лучший IPC**
   - вместо per-request socket можно сделать shared-memory ring buffer или mmap, если захочешь ещё сильнее уменьшить overhead.

6. **persistent/deferred harness**
   - для реальной цели почти всегда стоит держать harness в persistent mode и читать из stdin, чтобы не терять производительность.

## Что важно помнить

- Python mutators в AFL++ лучше использовать осторожно; для основного hot path практичнее C/C++ mutator.
- `AFL_CUSTOM_MUTATOR_ONLY` полезен, когда обычные стадии портят формат, но не всегда нужен: иногда лучший режим — гибрид, где LLM даёт валидные зацепки, а AFL дальше добивает байтовыми мутациями.
- `post_process()` не должен вносить случайных изменений; это место для детерминированной трансляции представления, а не ещё одной фазы мутации.

Если захочешь, следующим шагом можно превратить этот каркас в более жёсткий вариант: **grammar-aware mutator + LLM only for rare “escape” generations**, либо в версию с **coverage-aware feedback**, где воркер получает сигналы о том, какие шаблоны приводят к новым путям. 


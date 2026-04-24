#include <ctype.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#ifdef __clang__
#pragma clang optimize off
#endif

#ifdef __GNUC__
#pragma GCC optimize("O0")
#endif

#ifndef __AFL_LOOP
static int fallback_afl_loop(void) {

  static int once = 1;
  if (once) {

    once = 0;
    return 1;

  }

  return 0;

}

#define __AFL_INIT() \
  do {               \
  } while (0)
#define __AFL_LOOP(x) fallback_afl_loop()
#endif

enum vm_mode_t { MODE_FAST = 0, MODE_SLOW = 1, MODE_DEBUG = 2 };

typedef struct {
  int regs[3];
  enum vm_mode_t mode;
  int loop_value;
  unsigned seen;
  char text[256];
} vm_state_t;

static int reg_index(const char *name) {

  if (!name || name[1] != '\0') return -1;
  if (name[0] == 'A') return 0;
  if (name[0] == 'B') return 1;
  if (name[0] == 'C') return 2;
  return -1;

}

static void append_word(vm_state_t *st, const char *word) {

  size_t cur = strlen(st->text);
  size_t rem = sizeof(st->text) - cur - 1;
  if (rem == 0) return;

  strncat(st->text, word, rem);

}

static void maybe_crash(const vm_state_t *st, const char *flag) {

  if (strcmp(flag, "NOW") != 0) return;

  if (st->mode == MODE_DEBUG && st->loop_value == 7 && st->regs[0] == 1337 &&
      st->regs[1] == (1337 ^ 1364) && st->regs[2] == 0x4141 &&
      strstr(st->text, "open") != NULL && (st->seen & 0x1e) == 0x1e) {
    abort();
  }

}

static void run_check(vm_state_t *st, const char *token) {

  if (strcmp(token, "HELLO") == 0 && st->mode == MODE_FAST) {
    st->seen |= 0x01;
  } else if (strcmp(token, "MAGIC") == 0 && st->mode == MODE_DEBUG &&
             st->regs[0] == 1337) {
    st->seen |= 0x02;
  } else if (strcmp(token, "PLEASE") == 0 &&
             st->regs[1] == (st->regs[0] ^ 1364)) {
    st->seen |= 0x04;
  } else if (strcmp(token, "FIZZ") == 0 && strstr(st->text, "open") != NULL) {
    st->seen |= 0x08;
  } else if (strcmp(token, "OPEN") == 0 && strstr(st->text, "open") != NULL) {
    st->seen |= 0x10;
  } else if (strcmp(token, "WORLD") == 0 && strstr(st->text, "world") != NULL) {
    st->seen |= 0x20;
  }

}

static void execute_line(vm_state_t *st, char *line) {

  char *save = NULL;
  char *cmd = strtok_r(line, " \t\r\n", &save);
  if (!cmd) return;

  if (strcmp(cmd, "MODE") == 0) {

    char *value = strtok_r(NULL, " \t\r\n", &save);
    if (!value) return;

    if (strcmp(value, "FAST") == 0) {
      st->mode = MODE_FAST;
    } else if (strcmp(value, "SLOW") == 0) {
      st->mode = MODE_SLOW;
    } else if (strcmp(value, "DEBUG") == 0) {
      st->mode = MODE_DEBUG;
    }

    return;

  }

  if (strcmp(cmd, "SET") == 0) {

    char *reg = strtok_r(NULL, " \t\r\n", &save);
    char *val = strtok_r(NULL, " \t\r\n", &save);
    if (!reg || !val) return;

    int idx = reg_index(reg);
    if (idx < 0) return;
    st->regs[idx] = atoi(val);
    return;

  }

  if (strcmp(cmd, "COPY") == 0) {

    char *dst = strtok_r(NULL, " \t\r\n", &save);
    char *src = strtok_r(NULL, " \t\r\n", &save);
    if (!dst || !src) return;

    int dst_idx = reg_index(dst);
    int src_idx = reg_index(src);
    if (dst_idx < 0 || src_idx < 0) return;

    st->regs[dst_idx] = st->regs[src_idx];
    return;

  }

  if (strcmp(cmd, "XOR") == 0) {

    char *reg = strtok_r(NULL, " \t\r\n", &save);
    char *val = strtok_r(NULL, " \t\r\n", &save);
    if (!reg || !val) return;

    int idx = reg_index(reg);
    if (idx < 0) return;
    st->regs[idx] ^= atoi(val);
    return;

  }

  if (strcmp(cmd, "APPEND") == 0) {

    char *word = strtok_r(NULL, " \t\r\n", &save);
    if (!word) return;
    append_word(st, word);
    return;

  }

  if (strcmp(cmd, "CHECK") == 0) {

    char *token = strtok_r(NULL, " \t\r\n", &save);
    if (!token) return;
    run_check(st, token);
    return;

  }

  if (strcmp(cmd, "LOOP") == 0) {

    char *val = strtok_r(NULL, " \t\r\n", &save);
    if (!val) return;
    st->loop_value = atoi(val);
    return;

  }

  if (strcmp(cmd, "CRASH") == 0) {

    char *flag = strtok_r(NULL, " \t\r\n", &save);
    if (!flag) return;
    maybe_crash(st, flag);
    return;

  }

}

static void run_program(const uint8_t *data, size_t size) {

  vm_state_t st;
  memset(&st, 0, sizeof(st));
  st.mode = MODE_SLOW;

  char buf[4096];
  size_t n = size < sizeof(buf) - 1 ? size : sizeof(buf) - 1;
  memcpy(buf, data, n);
  buf[n] = '\0';

  char *save = NULL;
  char *line = strtok_r(buf, "\n", &save);
  while (line) {

    execute_line(&st, line);
    line = strtok_r(NULL, "\n", &save);

  }

}

int main(void) {

  uint8_t buf[4096];
  __AFL_INIT();

  while (__AFL_LOOP(UINT_MAX)) {

    memset(buf, 0, sizeof(buf));
    ssize_t len = read(STDIN_FILENO, buf, sizeof(buf));
    if (len <= 0) continue;

    run_program(buf, (size_t)len);

  }

  return 0;

}

#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

typedef struct afl_state afl_state_t;

typedef struct {
  int sock;
  unsigned int seed;
  uint8_t *out_buf;
  size_t out_cap;
  char endpoint[256];
  char last_desc[256];
  uint64_t requests;
  uint64_t hits;
  uint64_t misses;
  time_t last_reconnect_attempt;
} my_mutator_t;

static size_t min_size(size_t a, size_t b) { return a < b ? a : b; }

static uint32_t rng_next(uint32_t *state) {

  uint32_t x = *state;
  if (!x) x = 0xA5A5A5A5u;
  x ^= x << 13;
  x ^= x >> 17;
  x ^= x << 5;
  *state = x;
  return x;

}

static int connect_endpoint(const char *endpoint) {

  if (strncmp(endpoint, "tcp://", 6) == 0) {

    const char *host_begin = endpoint + 6;
    const char *colon = strrchr(host_begin, ':');
    if (!colon || colon == host_begin) return -1;

    char host[128];
    size_t host_len = (size_t)(colon - host_begin);
    if (host_len >= sizeof(host)) return -1;

    memcpy(host, host_begin, host_len);
    host[host_len] = '\0';

    int port = atoi(colon + 1);
    if (port <= 0 || port > 65535) return -1;

    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons((uint16_t)port);

    if (strcmp(host, "localhost") == 0) {
      strcpy(host, "127.0.0.1");
    }

    if (inet_pton(AF_INET, host, &addr.sin_addr) != 1) {

      close(fd);
      return -1;

    }

    if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) != 0) {

      close(fd);
      return -1;

    }

    int flags = fcntl(fd, F_GETFL, 0);
    if (flags >= 0) fcntl(fd, F_SETFL, flags | O_NONBLOCK);

    return fd;

  }

  int fd = socket(AF_UNIX, SOCK_STREAM, 0);
  if (fd < 0) return -1;

  struct sockaddr_un addr;
  memset(&addr, 0, sizeof(addr));
  addr.sun_family = AF_UNIX;

  socklen_t addr_len = sizeof(addr);
  if (endpoint[0] == '@') {

    size_t name_len = strlen(endpoint + 1);
    if (name_len + 1 > sizeof(addr.sun_path)) {

      close(fd);
      return -1;

    }

    addr.sun_path[0] = '\0';
    memcpy(addr.sun_path + 1, endpoint + 1, name_len);
    addr_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + name_len);

  } else {

    strncpy(addr.sun_path, endpoint, sizeof(addr.sun_path) - 1);

  }

  if (connect(fd, (struct sockaddr *)&addr, addr_len) != 0) {

    close(fd);
    return -1;

  }

  int flags = fcntl(fd, F_GETFL, 0);
  if (flags >= 0) fcntl(fd, F_SETFL, flags | O_NONBLOCK);

  return fd;

}

static void maybe_reconnect(my_mutator_t *data) {

  time_t now = time(NULL);
  if (data->sock >= 0) return;
  if (data->last_reconnect_attempt == now) return;
  data->last_reconnect_attempt = now;
  data->sock = connect_endpoint(data->endpoint);

}

static void drop_connection(my_mutator_t *data) {

  if (data->sock >= 0) close(data->sock);
  data->sock = -1;

}

static int send_all(int fd, const void *buf, size_t len) {

  const uint8_t *ptr = (const uint8_t *)buf;

  while (len > 0) {

    ssize_t written = send(fd, ptr, len, MSG_NOSIGNAL);
    if (written < 0) {

      if (errno == EINTR) continue;
      if (errno == EAGAIN || errno == EWOULDBLOCK) return -2;
      return -1;

    }

    ptr += (size_t)written;
    len -= (size_t)written;

  }

  return 0;

}

static int recv_all_deadline(int fd, void *buf, size_t len, int max_spins) {

  uint8_t *ptr = (uint8_t *)buf;
  int      spins = 0;

  while (len > 0) {

    ssize_t got = recv(fd, ptr, len, 0);
    if (got == 0) return -1;
    if (got < 0) {

      if (errno == EINTR) continue;
      if (errno == EAGAIN || errno == EWOULDBLOCK) {

        if (++spins >= max_spins) return -2;
        usleep(1000);
        continue;

      }

      return -1;

    }

    ptr += (size_t)got;
    len -= (size_t)got;

  }

  return 0;

}

static int drain_bytes(int fd, size_t len) {

  uint8_t scratch[1024];

  while (len > 0) {

    size_t chunk = min_size(sizeof(scratch), len);
    int rc = recv_all_deadline(fd, scratch, chunk, 4);
    if (rc != 0) return rc;
    len -= chunk;

  }

  return 0;

}

static void cheap_fallback_mutation(my_mutator_t *data, const uint8_t *buf,
                                    size_t buf_size, size_t max_size,
                                    size_t *out_len) {

  size_t n = min_size(buf_size, max_size);

  if (n == 0) {

    data->out_buf[0] = 'A';
    *out_len = 1;
    snprintf(data->last_desc, sizeof(data->last_desc), "fallback:init-byte");
    return;

  }

  memcpy(data->out_buf, buf, n);

  uint32_t state = data->seed ^ (uint32_t)data->requests;
  int ops = 1 + (int)(rng_next(&state) % 4u);

  for (int i = 0; i < ops; ++i) {

    uint32_t choice = rng_next(&state) % 4u;

    if (choice == 0 && n > 0) {

      size_t pos = (size_t)(rng_next(&state) % n);
      data->out_buf[pos] ^= (uint8_t)(1u << (rng_next(&state) % 8u));

    } else if (choice == 1 && n > 0) {

      size_t pos = (size_t)(rng_next(&state) % n);
      data->out_buf[pos] += (uint8_t)(1u + (rng_next(&state) % 15u));

    } else if (choice == 2 && n + 1 < max_size) {

      size_t pos = (size_t)(rng_next(&state) % (n + 1));
      memmove(data->out_buf + pos + 1, data->out_buf + pos, n - pos);
      data->out_buf[pos] = (uint8_t)(32 + (rng_next(&state) % 95u));
      ++n;

    } else if (choice == 3 && n > 4) {

      size_t from = (size_t)(rng_next(&state) % n);
      size_t to = (size_t)(rng_next(&state) % n);
      data->out_buf[to] = data->out_buf[from];

    }

  }

  *out_len = n;
  snprintf(data->last_desc, sizeof(data->last_desc),
           "fallback:cheap-mutation ops=%d", ops);

}

static void blend_with_seed(my_mutator_t *data, const uint8_t *seed_buf,
                            size_t seed_len, const uint8_t *cand_buf,
                            size_t cand_len, size_t max_size,
                            size_t *out_len) {

  if (cand_len == 0) {

    cheap_fallback_mutation(data, seed_buf, seed_len, max_size, out_len);
    return;

  }

  size_t n = min_size(cand_len, max_size);
  memcpy(data->out_buf, cand_buf, n);

  if (seed_len > 0 && n > 2) {

    size_t prefix = min_size(seed_len / 2, n / 2);
    memcpy(data->out_buf, seed_buf, prefix);

  }

  *out_len = n;
  snprintf(data->last_desc, sizeof(data->last_desc), "llm-cache-hit:size=%zu",
           cand_len);

}

static int fetch_candidate(my_mutator_t *data, uint8_t *dst, size_t *dst_len,
                           size_t max_size) {

  maybe_reconnect(data);
  if (data->sock < 0) return 0;

  uint8_t opcode = 'G';
  if (send_all(data->sock, &opcode, 1) != 0) {

    drop_connection(data);
    return 0;

  }

  uint8_t hdr[4];
  int     rc = recv_all_deadline(data->sock, hdr, sizeof(hdr), 2);
  if (rc != 0) {

    drop_connection(data);
    return 0;

  }

  uint32_t payload_len = ((uint32_t)hdr[0] << 24) | ((uint32_t)hdr[1] << 16) |
                         ((uint32_t)hdr[2] << 8) | (uint32_t)hdr[3];

  if (payload_len == 0) return 0;

  size_t to_copy = min_size((size_t)payload_len, max_size);
  rc = recv_all_deadline(data->sock, dst, to_copy, 4);
  if (rc != 0) {

    drop_connection(data);
    return 0;

  }

  if ((size_t)payload_len > to_copy) {

    rc = drain_bytes(data->sock, (size_t)payload_len - to_copy);
    if (rc != 0) {

      drop_connection(data);
      return 0;

    }

  }

  *dst_len = to_copy;
  return 1;

}

static void send_feedback(my_mutator_t *data, const uint8_t *buf, size_t len) {

  maybe_reconnect(data);
  if (data->sock < 0 || !buf || !len) return;

  len = min_size(len, (size_t)65535);

  uint8_t hdr[5];
  hdr[0] = 'A';
  hdr[1] = (uint8_t)((len >> 24) & 0xff);
  hdr[2] = (uint8_t)((len >> 16) & 0xff);
  hdr[3] = (uint8_t)((len >> 8) & 0xff);
  hdr[4] = (uint8_t)(len & 0xff);

  if (send_all(data->sock, hdr, sizeof(hdr)) != 0 ||
      send_all(data->sock, buf, len) != 0) {

    drop_connection(data);

  }

}

static int read_file_bytes(const char *filename, uint8_t *buf, size_t cap,
                           size_t *out_len) {

  FILE *fp = fopen(filename, "rb");
  if (!fp) return -1;

  size_t total = fread(buf, 1, cap, fp);
  if (ferror(fp)) {

    fclose(fp);
    return -1;

  }

  fclose(fp);
  *out_len = total;
  return 0;

}

void *afl_custom_init(afl_state_t *afl, unsigned int seed) {

  (void)afl;

  my_mutator_t *data = (my_mutator_t *)calloc(1, sizeof(my_mutator_t));
  if (!data) return NULL;

  data->seed = seed ? seed : 0xA5A5A5A5u;
  data->sock = -1;
  data->out_cap = 1024 * 1024;
  data->out_buf = (uint8_t *)malloc(data->out_cap);
  if (!data->out_buf) {

    free(data);
    return NULL;

  }

  const char *endpoint = getenv("LLM_MUTATOR_ADDR");
  if (!endpoint) endpoint = getenv("LLM_MUTATOR_SOCK");
  if (!endpoint) endpoint = "tcp://127.0.0.1:15333";
  strncpy(data->endpoint, endpoint, sizeof(data->endpoint) - 1);
  data->endpoint[sizeof(data->endpoint) - 1] = '\0';

  data->sock = connect_endpoint(data->endpoint);
  snprintf(data->last_desc, sizeof(data->last_desc), "init");
  return data;

}

unsigned int afl_custom_fuzz_count(void *data, const unsigned char *buf,
                                   size_t buf_size) {

  (void)data;
  (void)buf;
  (void)buf_size;
  return 4;

}

void afl_custom_splice_optout(void *data) { (void)data; }

size_t afl_custom_fuzz(void *data, unsigned char *buf, size_t buf_size,
                       unsigned char **out_buf, unsigned char *add_buf,
                       size_t add_buf_size, size_t max_size) {

  (void)add_buf;
  (void)add_buf_size;

  my_mutator_t *state = (my_mutator_t *)data;
  if (!state || !out_buf || !state->out_buf || max_size == 0) return 0;

  state->requests++;
  if (max_size > state->out_cap) max_size = state->out_cap;

  uint8_t candidate[65536];
  size_t  candidate_len = 0;
  int hit = fetch_candidate(state, candidate, &candidate_len,
                            min_size(sizeof(candidate), max_size));

  size_t produced = 0;
  if (hit) {

    state->hits++;
    blend_with_seed(state, buf, buf_size, candidate, candidate_len, max_size,
                    &produced);

  } else {

    state->misses++;
    cheap_fallback_mutation(state, buf, buf_size, max_size, &produced);

  }

  *out_buf = state->out_buf;
  return produced;

}

const char *afl_custom_describe(void *data, size_t max_description_len) {

  my_mutator_t *state = (my_mutator_t *)data;
  if (!state) return "llm-mutator:null";

  if (max_description_len > 0 &&
      max_description_len < sizeof(state->last_desc)) {
    state->last_desc[max_description_len - 1] = '\0';
  }

  return state->last_desc;

}

size_t afl_custom_post_process(void *data, unsigned char *buf, size_t buf_size,
                               unsigned char **out_buf) {

  (void)data;
  *out_buf = buf;
  return buf_size;

}

unsigned char afl_custom_queue_get(void *data, const unsigned char *filename) {

  (void)data;
  (void)filename;
  return 1;

}

unsigned char afl_custom_havoc_mutation_probability(void *data) {

  (void)data;
  return 8;

}

size_t afl_custom_havoc_mutation(void *data, uint8_t *buf, size_t buf_size,
                                 uint8_t **out_buf, size_t max_size) {

  my_mutator_t *state = (my_mutator_t *)data;
  if (!state || !buf || !out_buf) return 0;

  size_t n = min_size(buf_size, min_size(max_size, state->out_cap));
  memcpy(state->out_buf, buf, n);

  if (n > 0) {

    size_t pos = (size_t)(state->seed + state->requests) % n;
    state->out_buf[pos] ^= 0x20;

  }

  *out_buf = state->out_buf;
  snprintf(state->last_desc, sizeof(state->last_desc), "havoc:tweak-byte");
  return n;

}

uint8_t afl_custom_queue_new_entry(void *data,
                                   const uint8_t *filename_new_queue,
                                   const uint8_t *filename_orig_queue) {

  (void)filename_orig_queue;

  my_mutator_t *state = (my_mutator_t *)data;
  if (!state || !filename_new_queue) return 0;

  uint8_t buf[65535];
  size_t  len = 0;
  if (read_file_bytes((const char *)filename_new_queue, buf, sizeof(buf), &len) ==
      0) {
    send_feedback(state, buf, len);
  }

  return 0;

}

void afl_custom_deinit(void *data) {

  my_mutator_t *state = (my_mutator_t *)data;
  if (!state) return;

  if (state->sock >= 0) close(state->sock);
  free(state->out_buf);
  free(state);

}

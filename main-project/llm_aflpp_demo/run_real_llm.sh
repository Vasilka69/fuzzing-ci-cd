#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

: "${LLM_API_URL:?set LLM_API_URL first}"

AFLPP_DIR="${AFLPP_DIR:-$ROOT/../AFLplusplus}"
PYTHON_BIN="${PYTHON:-python3}"
ADDR="${LLM_MUTATOR_ADDR:-${LLM_MUTATOR_SOCK:-tcp://127.0.0.1:15333}}"
OUTPUT_DIR="${AFL_OUTPUT_DIR:-$ROOT/output/real}"
SEEDS_DIR="${AFL_SEEDS_DIR:-$ROOT/seeds}"

make all
mkdir -p "$ROOT/runtime" "$ROOT/output"

export LLM_MUTATOR_ADDR="$ADDR"
export LLM_MUTATOR_PROMPT_FILE="${LLM_MUTATOR_PROMPT_FILE:-$ROOT/prompt.txt}"
export LLM_MUTATOR_SEED_DIR="${LLM_MUTATOR_SEED_DIR:-$SEEDS_DIR}"
export LLM_MUTATOR_DISCOVERED_DIR="${LLM_MUTATOR_DISCOVERED_DIR:-$ROOT/runtime/discovered}"

"$PYTHON_BIN" "$ROOT/llm_mutator_server.py" &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT

sleep 1

AFL_CUSTOM_MUTATOR_LIBRARY="$ROOT/build/afl_llm_mutator.so" \
AFL_CUSTOM_MUTATOR_ONLY="${AFL_CUSTOM_MUTATOR_ONLY:-1}" \
"$AFLPP_DIR/afl-fuzz" \
  -i "$SEEDS_DIR" \
  -o "$OUTPUT_DIR" \
  -x "$ROOT/demo.dict" \
  -- "$ROOT/build/target_dsl" "$@"

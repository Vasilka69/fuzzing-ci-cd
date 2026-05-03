#!/usr/bin/env bash
export LLM_API_URL="http://127.0.0.1:11434/v1/chat/completions"
export LLM_MODEL="qwen3:8b"
unset LLM_API_KEY
export LLM_MUTATOR_ADDR="tcp://127.0.0.1:15335"
export LLM_MUTATOR_LOG_CANDIDATES_DIR="$PWD/runtime/generated"

export NO_PROXY=127.0.0.1,localhost
export no_proxy=127.0.0.1,localhost

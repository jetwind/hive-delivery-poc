#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../workspace/product-search-demo"
exec opencode serve --hostname 127.0.0.1 --port 4096 --cors http://localhost:5173

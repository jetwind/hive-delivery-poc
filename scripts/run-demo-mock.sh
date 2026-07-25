#!/usr/bin/env bash
set -euo pipefail
export HIVE_OPENCODE_MOCK=true
mvn -pl backend spring-boot:run

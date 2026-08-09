#!/usr/bin/env bash
#
# Structural guards for the instrumentation suite. See scripts/check_tests.py for what each
# check enforces and why.

set -euo pipefail

exec python3 "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check_tests.py"

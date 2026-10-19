#!/usr/bin/env bash
# Publishes demo prices as ADMIN. Usage: ADMIN_EMAIL=... ADMIN_PASSWORD=... [BASE_URL=http://localhost:8080] scripts/publish-demo-quotes.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
: "${ADMIN_EMAIL:?set ADMIN_EMAIL}" "${ADMIN_PASSWORD:?set ADMIN_PASSWORD}"

token=$(jq -n --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASSWORD" '{email: $e, password: $p}' |
  curl -sf -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' -d @- |
  jq -er .accessToken) || { echo "login failed" >&2; exit 1; }

publish() { # symbol price...
  local symbol=$1; shift
  for price in "$@"; do
    curl -sf -X POST "$BASE_URL/api/v1/admin/markets/$symbol/quotes" \
      -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
      -d "{\"price\":\"$price\"}"
    echo
  done
}

publish BTC-USDT 65000.00 65010.50 64990.25 65120.00 65080.75
publish ETH-USDT 3200.00 3205.40 3198.10 3210.00 3207.25

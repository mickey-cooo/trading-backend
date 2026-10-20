#!/usr/bin/env bash
# Runs the whole trading flow against a running API and stops at the first wrong answer.
#   register -> demo funds -> market buy -> limit sell -> reserve -> price moves -> fill
#   -> second limit order -> cancel -> balances, portfolio, history
#
# Needs: curl, jq, and an admin account (the admin publishes the prices).
#   ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=... ./scripts/trading-flow.sh
# Optional: BASE_URL (default http://localhost:8080), MATCH_WAIT_SECONDS (default 3, the matcher runs every second).
# For a local or test API only: passwords and tokens go on curl's command line, so other users on this machine can see them in `ps`.
# Run it on a fresh database for exact numbers: it expects the new user to start with 10,000 USDT.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MATCH_WAIT_SECONDS="${MATCH_WAIT_SECONDS:-3}"
: "${ADMIN_EMAIL:?set ADMIN_EMAIL}"
: "${ADMIN_PASSWORD:?set ADMIN_PASSWORD}"

run_id="$(date +%s)-$RANDOM"
trader_email="flow-${run_id}@example.test"
trader_password="flow-$(openssl rand -hex 8)"

step() { printf '\n== %s\n' "$1"; }

fail() { printf 'FAILED: %s\n' "$1" >&2; exit 1; }

expect_equal() { # description, expected, actual
  [ "$2" = "$3" ] || fail "$1: expected '$2' but got '$3'"
  printf 'ok  %s = %s\n' "$1" "$3"
}

# api METHOD PATH TOKEN [JSON_BODY] [IDEMPOTENCY_KEY]: prints the body, fails on a non-2xx answer
api() {
  local method="$1" path="$2" token="$3" body="${4:-}" key="${5:-}"
  local args=(-sS -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json' -w '\n%{http_code}')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$key" ] && args+=(-H "Idempotency-Key: $key")
  [ -n "$body" ] && args+=(-d "$body")
  local response status
  response="$(curl "${args[@]}")"
  status="${response##*$'\n'}"
  [[ "$status" =~ ^2 ]] || fail "$method $path answered $status: ${response%$'\n'*}"
  printf '%s' "${response%$'\n'*}"
}

login() { # email, password -> access token
  api POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}" | jq -r .accessToken
}

# asset_field TOKEN ASSET FIELD: one field of one balance
asset_field() {
  api GET /api/v1/accounts/me/balances "$1" | jq -r --arg asset "$2" ".[] | select(.asset==\$asset) | .$3"
}

publish_price() { # admin token, pair, price
  api POST "/api/v1/admin/markets/$2/quotes" "$1" "{\"price\":\"$3\"}" > /dev/null
}

step "Register and log in"
api POST /api/v1/auth/register "" \
  "{\"email\":\"$trader_email\",\"password\":\"$trader_password\",\"confirmPassword\":\"$trader_password\"}" > /dev/null
trader="$(login "$trader_email" "$trader_password")"
admin="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"

step "Start price 100.00 and the demo funds"
publish_price "$admin" BTC-USDT 100.00
expect_equal "USDT available" "10000.000000000000000000" "$(asset_field "$trader" USDT available)"

step "Market buy 2 BTC"
buy="$(api POST /api/v1/orders "$trader" \
  '{"symbol":"BTC-USDT","side":"BUY","type":"MARKET","quantity":"2"}' "buy-$run_id")"
expect_equal "market buy status" FILLED "$(jq -r .status <<<"$buy")"
expect_equal "USDT available" "9800.000000000000000000" "$(asset_field "$trader" USDT available)"
expect_equal "BTC available" "2.000000000000000000" "$(asset_field "$trader" BTC available)"

step "Limit sell 1 BTC at 150.00 and check the reserve"
sell="$(api POST /api/v1/orders "$trader" \
  '{"symbol":"BTC-USDT","side":"SELL","type":"LIMIT","quantity":"1","limitPrice":"150"}' "sell-$run_id")"
expect_equal "limit sell status" OPEN "$(jq -r .status <<<"$sell")"
sell_id="$(jq -r .id <<<"$sell")"
expect_equal "BTC reserved" "1.000000000000000000" "$(asset_field "$trader" BTC reserved)"
expect_equal "BTC available" "1.000000000000000000" "$(asset_field "$trader" BTC available)"

step "Price reaches 150.00, wait for the matcher"
publish_price "$admin" BTC-USDT 150.00
sleep "$MATCH_WAIT_SECONDS"
expect_equal "limit sell status" FILLED "$(api GET "/api/v1/orders/$sell_id" "$trader" | jq -r .status)"
expect_equal "BTC reserved" "0.000000000000000000" "$(asset_field "$trader" BTC reserved)"
expect_equal "USDT available" "9950.000000000000000000" "$(asset_field "$trader" USDT available)"

step "Place a limit buy at 100.00 and cancel it"
pending="$(api POST /api/v1/orders "$trader" \
  '{"symbol":"BTC-USDT","side":"BUY","type":"LIMIT","quantity":"1","limitPrice":"100"}' "pending-$run_id")"
pending_id="$(jq -r .id <<<"$pending")"
expect_equal "limit buy status" OPEN "$(jq -r .status <<<"$pending")"
expect_equal "USDT reserved" "100.000000000000000000" "$(asset_field "$trader" USDT reserved)"
expect_equal "open orders" 1 "$(api GET '/api/v1/orders?status=OPEN' "$trader" | jq '.content | length')"
cancelled="$(api POST "/api/v1/orders/$pending_id/cancel" "$trader")"
expect_equal "cancelled status" CANCELLED "$(jq -r .status <<<"$cancelled")"
expect_equal "USDT reserved" "0.000000000000000000" "$(asset_field "$trader" USDT reserved)"
expect_equal "USDT available" "9950.000000000000000000" "$(asset_field "$trader" USDT available)"

step "Balances, portfolio and history"
portfolio="$(api GET /api/v1/portfolio "$trader")"
expect_equal "BTC value" "150.00" "$(jq -r '.assets[] | select(.asset=="BTC") | .valueUsdt' <<<"$portfolio")"
expect_equal "USDT value" "9950.00" "$(jq -r '.assets[] | select(.asset=="USDT") | .valueUsdt' <<<"$portfolio")"
expect_equal "orders" 3 "$(api GET /api/v1/orders "$trader" | jq '.content | length')"
expect_equal "trades" 2 "$(api GET /api/v1/trades "$trader" | jq '.content | length')"
ledger_lines="$(api GET '/api/v1/accounts/me/ledger?size=100' "$trader" | jq '.content | length')"
printf 'ok  ledger has %s lines\n' "$ledger_lines"

printf '\nAll checks passed.\n'

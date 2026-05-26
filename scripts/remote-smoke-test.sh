#!/usr/bin/env bash
set -u

BASE_URL="${1:-${WANG_DETECTIVE_BASE_URL:-${BASE_URL:-}}}"
USERNAME="${2:-${WANG_DETECTIVE_USERNAME:-${ADMIN_USERNAME:-}}}"
PASSWORD="${3:-${WANG_DETECTIVE_PASSWORD:-${ADMIN_PASSWORD:-}}}"
TIMEOUT_SECONDS="${WANG_DETECTIVE_TIMEOUT_SECONDS:-25}"
INSECURE="${WANG_DETECTIVE_INSECURE:-0}"

if [[ -z "$BASE_URL" || -z "$USERNAME" || -z "$PASSWORD" ]]; then
  echo "Usage: bash scripts/remote-smoke-test.sh https://example.com admin 'password'"
  echo "Or set WANG_DETECTIVE_BASE_URL, WANG_DETECTIVE_USERNAME, WANG_DETECTIVE_PASSWORD."
  exit 2
fi

BASE_URL="${BASE_URL%/}"
TMP_DIR="$(mktemp -d)"
TOKEN=""
TOTAL=0
FAILED=0
CURL_TLS_ARGS=()

if [[ "$INSECURE" == "1" ]]; then
  CURL_TLS_ARGS=(-k)
fi

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

extract_token() {
  grep -o '"token"[[:space:]]*:[[:space:]]*"[^"]*"' "$1" 2>/dev/null \
    | head -n 1 \
    | sed 's/.*"token"[[:space:]]*:[[:space:]]*"//;s/"$//'
}

extract_first_string() {
  local key="$1"
  local file="$2"
  grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$file" 2>/dev/null \
    | head -n 1 \
    | sed "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"//;s/\"$//"
}

print_result() {
  local state="$1"
  local status="$2"
  local name="$3"
  local detail="$4"
  printf '%-4s %-4s %s - %s\n' "$state" "$status" "$name" "$detail"
}

request() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local expect="${5:-}"
  local out="$TMP_DIR/${name}.json"
  local err="$TMP_DIR/${name}.err"
  local status
  local headers=(-H "Accept: application/json")

  TOTAL=$((TOTAL + 1))
  if [[ -n "$TOKEN" ]]; then
    headers+=(-H "Authorization: Bearer $TOKEN")
  fi
  if [[ -n "$body" ]]; then
    headers+=(-H "Content-Type: application/json" --data "$body")
  fi

  status="$(curl -sS "${CURL_TLS_ARGS[@]}" \
    --connect-timeout 8 \
    --max-time "$TIMEOUT_SECONDS" \
    -X "$method" \
    "${headers[@]}" \
    -o "$out" \
    -w "%{http_code}" \
    "$BASE_URL$path" 2>"$err")" || status="000"

  if [[ "$status" =~ ^2[0-9][0-9]$ ]] && { [[ -z "$expect" ]] || grep -q "$expect" "$out"; }; then
    print_result "PASS" "$status" "$name" "ok"
    return 0
  fi

  FAILED=$((FAILED + 1))
  local detail
  detail="$(head -c 180 "$err" 2>/dev/null)"
  if [[ -z "$detail" ]]; then
    detail="$(head -c 180 "$out" 2>/dev/null)"
  fi
  print_result "FAIL" "$status" "$name" "${detail:-unexpected response}"
  return 1
}

echo "Remote smoke test: $BASE_URL"

request "health" "GET" "/actuator/health" "" '"status"[[:space:]]*:[[:space:]]*"UP"' || true

LOGIN_BODY="{\"account\":\"$(json_escape "$USERNAME")\",\"password\":\"$(json_escape "$PASSWORD")\"}"
if request "login" "POST" "/api/sys/login" "$LOGIN_BODY" '"token"[[:space:]]*:'; then
  TOKEN="$(extract_token "$TMP_DIR/login.json")"
fi

if [[ -n "$TOKEN" ]]; then
  request "diagnostics" "GET" "/api/v1/system/diagnostics" "" '"checks"[[:space:]]*:' || true
  request "version-info" "GET" "/api/v1/system/version-info" "" '"currentVersion"[[:space:]]*:' || true
  request "glance" "GET" "/api/sys/glance" "" "" || true
  request "oci-user-page" "POST" "/api/oci/userPage" '{"currentPage":1,"pageSize":5}' '"records"[[:space:]]*:' || true
  request "task-page" "POST" "/api/oci/createTaskPage" '{"currentPage":1,"pageSize":5}' '"records"[[:space:]]*:' || true
  request "audit-recent" "GET" "/api/ops/audit/recent?limit=5" "" "" || true
  request "backup-local" "GET" "/api/v1/backups/local" "" "" || true
  request "rescue-overview" "GET" "/api/rescue/overview" "" "" || true
  request "oci-risk" "GET" "/api/v1/oci/risk?maxConfigs=1" "" '"configs"[[:space:]]*:' || true

  CFG_ID="$(extract_first_string "id" "$TMP_DIR/oci-user-page.json")"
  if [[ -n "$CFG_ID" ]]; then
    VCN_BODY="{\"ociCfgId\":\"$(json_escape "$CFG_ID")\",\"currentPage\":1,\"pageSize\":10,\"cleanReLaunch\":true}"
    if request "vcn-page" "POST" "/api/vcn/page" "$VCN_BODY" '"records"[[:space:]]*:'; then
      VCN_ID="$(extract_first_string "id" "$TMP_DIR/vcn-page.json")"
      if [[ -n "$VCN_ID" ]]; then
        SECURITY_BASE="\"ociCfgId\":\"$(json_escape "$CFG_ID")\",\"vcnId\":\"$(json_escape "$VCN_ID")\",\"currentPage\":1,\"pageSize\":20,\"cleanReLaunch\":false"
        request "security-rules-ingress" "POST" "/api/securityRule/page" "{$SECURITY_BASE,\"type\":0}" '"records"[[:space:]]*:' || true
        request "security-rules-egress" "POST" "/api/securityRule/page" "{$SECURITY_BASE,\"type\":1}" '"records"[[:space:]]*:' || true
      fi
    fi
  fi
else
  echo "Skip authenticated checks because login token was not returned."
fi

echo
if [[ "$FAILED" -gt 0 ]]; then
  echo "Failed $FAILED/$TOTAL checks."
  exit 1
fi

echo "All $TOTAL checks passed."

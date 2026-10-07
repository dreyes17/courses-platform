#!/usr/bin/env bash
# Generates realistic traffic against a running stack (`docker compose up`) so the Grafana dashboard and Jaeger
# have something to show: a small catalog, students enrolling, approved and declined payments, a full course, an
# idempotent retry, completed courses with certificates, catalog browsing and a burst of rate-limited logins.
#
#   ./scripts/demo-traffic.sh                                  # against http://localhost:8080
#   API_URL=http://localhost:8090 ROUNDS=50 ./scripts/demo-traffic.sh
#
# Needs curl and jq, and ADMIN_EMAIL/ADMIN_PASSWORD in the environment or in .env. Safe to re-run: each run uses
# its own names and emails. Sign-up is limited to 20 per hour per IP, so each run registers 6 students; the final
# login burst uses up the login budget for about a minute.
set -euo pipefail

API_URL=${API_URL:-http://localhost:8080}
ROUNDS=${ROUNDS:-30}
STUDENTS=6

cd "$(dirname "$0")/.."
if [[ -z ${ADMIN_EMAIL:-} || -z ${ADMIN_PASSWORD:-} ]] && [[ -f .env ]]; then
  set -a; . ./.env; set +a
fi
: "${ADMIN_EMAIL:?set ADMIN_EMAIL or create .env}"
: "${ADMIN_PASSWORD:?set ADMIN_PASSWORD or create .env}"
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

RUN=$(date +%s)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
TOKEN=
STATUS=
REQUESTS=0

# api METHOD PATH [JSON] [curl args...]: the body lands in $TMP/body, the status code in $STATUS.
api() {
  local method=$1 path=$2 body=${3:-}
  shift $(($# < 3 ? $# : 3))
  REQUESTS=$((REQUESTS + 1))
  local args=(-s -o "$TMP/body" -D "$TMP/headers" -w '%{http_code}' -X "$method" "$API_URL$path"
    -H "X-Correlation-Id: demo-$RUN-$REQUESTS")
  if [[ -n $TOKEN ]]; then args+=(-H "Authorization: Bearer $TOKEN"); fi
  if [[ -n $body ]]; then args+=(-H 'Content-Type: application/json' -d "$body"); fi
  STATUS=$(curl "${args[@]}" "$@")
}

field() { jq -r "$1" "$TMP/body"; }

expect() {
  if [[ " $* " != *" $STATUS "* ]]; then
    echo "Unexpected HTTP $STATUS (expected $*):" >&2
    cat "$TMP/body" >&2; echo >&2
    exit 1
  fi
}

# Logs in and sets TOKEN, waiting out the login rate limit (10 per minute per IP) if it kicks in.
login() {
  TOKEN=
  while :; do
    api POST /api/auth/token "$(jq -nc --arg e "$1" --arg p "$2" '{email: $e, password: $p}')"
    [[ $STATUS == 429 ]] || break
    local wait
    wait=$(grep -i '^retry-after:' "$TMP/headers" | tr -dc '0-9')
    echo "  login rate limit reached, waiting ${wait:-10}s"
    sleep "${wait:-10}"
  done
  expect 200
  TOKEN=$(field .accessToken)
}

# Polls an enrollment until it reaches the given status (the payment is confirmed asynchronously).
await_status() {
  for _ in $(seq 1 30); do
    api GET "/api/enrollments/$1"
    expect 200
    [[ $(field .status) == "$2" ]] && return
    sleep 1
  done
  echo "Enrollment $1 never reached $2 (last status: $(field .status))" >&2
  exit 1
}

echo "==> Waiting for the API at $API_URL"
for _ in $(seq 1 60); do
  curl -s -o /dev/null "$API_URL/v3/api-docs" && break
  sleep 2
done

echo "==> Catalog (as ADMIN)"
login "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
ADMIN_TOKEN=$TOKEN

api POST /api/categories "$(jq -nc --arg n "Backend Engineering $RUN" \
  '{name: $n, description: "Distributed systems, databases and APIs"}')"
expect 201
CATEGORY=$(field .id)

api POST /api/instructors "$(jq -nc --arg e "ada.$RUN@example.com" \
  '{name: "Ada Lovelace", email: $e, bio: "Backend engineer and trainer", password: "instructor-demo-pass"}')"
expect 201
INSTRUCTOR=$(field .id)

create_course() { # title level price capacity; sets COURSE
  api POST /api/courses "$(jq -nc --arg t "$1" --arg l "$2" --argjson p "$3" --argjson c "$4" \
    --arg cat "$CATEGORY" --arg ins "$INSTRUCTOR" \
    '{title: $t, description: "Hands-on course", durationHours: 12, level: $l, price: $p, capacity: $c,
      categoryId: $cat, instructorId: $ins}')"
  expect 201
  COURSE=$(field .id)
  api POST "/api/courses/$COURSE/publish"
  expect 200 204
}
create_course "Event-Driven Microservices with Spring Boot" INTERMEDIATE 49.00 50
POPULAR=$COURSE
create_course "PostgreSQL Performance Tuning" ADVANCED 89.00 3
SMALL=$COURSE
create_course "Executive Software Architecture Program" ADVANCED 12500.00 10
EXPENSIVE=$COURSE
echo "  3 courses published (one with 3 seats, one priced above the simulated gateway's limit)"

echo "==> Registering $STUDENTS students"
STUDENT_TOKENS=()
TOKEN=
for i in $(seq 1 "$STUDENTS"); do
  email="student$i.$RUN@example.com"
  api POST /api/auth/register "$(jq -nc --arg e "$email" --arg n "Student$i" \
    '{firstName: $n, lastName: "Demo", email: $e, password: "student-demo-pass"}')"
  if [[ $STATUS == 429 ]]; then
    echo "Sign-up is rate limited (20 per hour per IP); try again later." >&2
    exit 1
  fi
  expect 201
  login "$email" student-demo-pass
  STUDENT_TOKENS+=("$TOKEN")
  TOKEN=
done

enroll() { # student-index course-id idempotency-key
  TOKEN=${STUDENT_TOKENS[$1]}
  api POST /api/enrollments "$(jq -nc --arg c "$2" '{courseId: $c}')" -H "Idempotency-Key: $3"
}

echo "==> Enrollments"
POPULAR_ENROLLMENTS=()
for i in $(seq 0 $((STUDENTS - 1))); do
  enroll "$i" "$POPULAR" "demo-$RUN-popular-$i"
  expect 201
  POPULAR_ENROLLMENTS+=("$(field .id)")
done
echo "  $STUDENTS enrolled in the popular course"

enroll 0 "$POPULAR" "demo-$RUN-popular-0"
expect 201
[[ $(field .id) == "${POPULAR_ENROLLMENTS[0]}" ]] || { echo "Idempotent retry returned another enrollment" >&2; exit 1; }
echo "  retry with the same Idempotency-Key returned the original enrollment"

enroll 1 "$POPULAR" "demo-$RUN-popular-again"
expect 409
echo "  enrolling twice with a new key: 409 (already enrolled)"

SMALL_ENROLLMENTS=()
for i in 0 1 2; do
  enroll "$i" "$SMALL" "demo-$RUN-small-$i"
  expect 201
  SMALL_ENROLLMENTS+=("$(field .id)")
done
enroll 3 "$SMALL" "demo-$RUN-small-3"
expect 409
echo "  3-seat course filled; the 4th student got 409 (course full)"

enroll 4 "$EXPENSIVE" "demo-$RUN-expensive-4"
expect 201
DECLINED=$(field .id)

echo "==> Waiting for the asynchronous payments"
TOKEN=$ADMIN_TOKEN
for id in "${POPULAR_ENROLLMENTS[@]}" "${SMALL_ENROLLMENTS[@]}"; do await_status "$id" ACTIVE; done
await_status "$DECLINED" CANCELLED
api GET "/api/enrollments/$DECLINED/payment"
echo "  $((${#POPULAR_ENROLLMENTS[@]} + ${#SMALL_ENROLLMENTS[@]})) payments confirmed; one declined: $(field .failureReason)"

echo "==> Progress, completions and certificates"
for i in 0 1 2; do
  TOKEN=${STUDENT_TOKENS[$i]}
  for progress in 40 80 100; do
    api PUT "/api/enrollments/${POPULAR_ENROLLMENTS[$i]}/progress" "{\"progress\": $progress}"
    expect 200
  done
done
TOKEN=$ADMIN_TOKEN
CODE=
for _ in $(seq 1 30); do
  api GET "/api/enrollments/${POPULAR_ENROLLMENTS[0]}/certificate"
  [[ $STATUS == 200 ]] && { CODE=$(field .code); break; }
  sleep 1
done
[[ -n $CODE ]] || { echo "No certificate was issued" >&2; exit 1; }
TOKEN=
api GET "/api/certificates/$CODE"
expect 200
echo "  3 courses completed; certificate $CODE verified without a token"

TOKEN=${STUDENT_TOKENS[2]}
api POST "/api/enrollments/${SMALL_ENROLLMENTS[2]}/cancel"
expect 200 204
echo "  one student cancelled, releasing a seat"

echo "==> Browsing the catalog ($ROUNDS rounds)"
for round in $(seq 1 "$ROUNDS"); do
  TOKEN=${STUDENT_TOKENS[$((round % STUDENTS))]}
  api GET "/api/courses?categoryId=$CATEGORY&size=10"
  api GET "/api/courses/scroll?size=5"
  api GET "/api/courses/$POPULAR"
  api GET "/api/courses/$SMALL"
  api GET /api/categories
  api GET "/api/courses?withAvailableSeats=true&sort=price,desc"
  api GET /api/courses/00000000-0000-0000-0000-000000000000   # 404
  TOKEN=
  api GET /api/students                                      # 401
  sleep 0.2
done

echo "==> Burst of failed logins (rate limiting)"
TOKEN=
rejected=0
for i in $(seq 1 15); do
  api POST /api/auth/token '{"email": "intruder@example.com", "password": "wrong-password"}'
  [[ $STATUS == 429 ]] && rejected=$((rejected + 1))
done
echo "  $rejected of 15 attempts rejected with 429"

cat <<EOF

Done: $REQUESTS requests in run $RUN.
  Grafana:  http://localhost:3000   (dashboard "Courses — platform", last 15 minutes)
  Jaeger:   http://localhost:16686  (service "courses", operation "http post /api/enrollments")
  Example enrollment traced end to end: ${POPULAR_ENROLLMENTS[0]}
EOF

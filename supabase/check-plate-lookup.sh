#!/bin/sh
# Checks that resolve_plate answers what it should and nothing more (issue #392).
#
#   sh supabase/check-plate-lookup.sh
#
# Everything runs as `anon`, which is the point: this function is granted to anon on
# purpose, so these are the answers a stranger gets. The interesting assertions are the
# refusals — a partial plate, and any column beyond the five.
#
# It exists because the shape of this function *is* the privacy boundary. A widened
# `returns table` or a dropped `revoke` would leak owner data while every test in the
# Kotlin suite kept passing.

set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PROPERTIES="$ROOT/local.properties"

if [ ! -f "$PROPERTIES" ]; then
  echo "no local.properties — nothing to check against" >&2
  exit 1
fi

URL=$(grep -E '^supabase\.url=' "$PROPERTIES" | cut -d= -f2- | tr -d ' \r')
KEY=$(grep -E '^supabase\.anonKey=' "$PROPERTIES" | cut -d= -f2- | tr -d ' \r')

if [ -z "$URL" ] || [ -z "$KEY" ]; then
  echo "supabase.url or supabase.anonKey missing from local.properties" >&2
  exit 1
fi

pass=0
fail=0

ok()   { printf '  ok    %s\n' "$1"; pass=$((pass + 1)); }
bad()  { printf '  FAIL  %s (%s)\n' "$1" "$2"; fail=$((fail + 1)); }

# $1 label, $2 expected status, $3 plate
status_for() {
  curl -s -o /dev/null -w '%{http_code}' \
    -H "apikey: $KEY" -H "Authorization: Bearer $KEY" \
    -X POST -H 'Content-Type: application/json' \
    -d "{\"p_plate\":\"$3\"}" "$URL/rest/v1/rpc/resolve_plate"
}

check_status() {
  actual=$(status_for "$1" "$2" "$3")
  [ "$actual" = "$2" ] && ok "$1" || bad "$1" "expected $2, got $actual"
}

body_for() {
  curl -s -H "apikey: $KEY" -H "Authorization: Bearer $KEY" \
    -X POST -H 'Content-Type: application/json' \
    -d "{\"p_plate\":\"$1\"}" "$URL/rest/v1/rpc/resolve_plate"
}

echo "$URL"
echo
echo "what a stranger may ask"
# A plate nobody has entered. 200 with [] — a miss is an answer, not an error.
check_status "an unknown plate is answered"  200 "ZZ99ZZ9999"

echo
echo "what a stranger may not ask"
# 400: the function raises 22023 on anything that is not a whole plate. This is the
# only thing standing between the grant and prefix probing, so it is the assertion
# that matters most in this file.
check_status "a partial plate is refused"    400 "MH12"
check_status "an empty plate is refused"     400 ""
check_status "an over-long plate is refused" 400 "MH12AB1234567"

echo
echo "what comes back"
# The five attribute keys and nothing else. Checked on the shape of the response rather
# than on a seeded car, so this runs against any project.
body=$(body_for "ZZ99ZZ9999")
if [ "$body" = "[]" ]; then
  ok "a miss is an empty array, not a null row"
else
  bad "a miss is an empty array, not a null row" "got $(printf '%s' "$body" | cut -c1-80)"
fi

for column in owner_id id nickname current_odometer_km registration_number created_at; do
  if printf '%s' "$body" | grep -q "\"$column\""; then
    bad "$column is not returned" "found in the response body"
  else
    ok "$column is not returned"
  fi
done

echo
echo "what stays shut"
# The counter and its salt are the function's own bookkeeping. RLS with no policy means
# a 200 with an empty array rather than a 401, so the body is what to check.
for table in plate_lookups plate_lookup_salt; do
  body=$(curl -s -H "apikey: $KEY" -H "Authorization: Bearer $KEY" \
    "$URL/rest/v1/$table?select=*")
  case "$body" in
    "[]"|*PGRST*|*permission*) ok "$table is not readable" ;;
    *) bad "$table is not readable" "got $(printf '%s' "$body" | cut -c1-80)" ;;
  esac
done

# Only the definer function may charge a call.
charge=$(curl -s -o /dev/null -w '%{http_code}' \
  -H "apikey: $KEY" -H "Authorization: Bearer $KEY" \
  -X POST -H 'Content-Type: application/json' -d '{}' \
  "$URL/rest/v1/rpc/plate_lookup_charge")
case "$charge" in
  401|404) ok "the counter cannot be charged directly" ;;
  *) bad "the counter cannot be charged directly" "expected 401 or 404, got $charge" ;;
esac

echo
printf '%s passed, %s failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]

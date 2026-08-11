#!/bin/sh
# Is the `legal` function actually serving, and are the pages that read it wired to the right
# pair of projects?
#
# Run this after every redeploy. The "Verify JWT with legacy secret" toggle switches itself
# back on when a function is updated (supabase/supabase#43608), and nothing announces it — the
# pages just start returning 401, including the deletion URL on the Play listing.
#
#   sh supabase/check-legal.sh          # prod, the default
#   sh supabase/check-legal.sh dev
#
# Project refs are read from local.properties so there is no placeholder to substitute: prod is
# `supabase.url`, dev is `supabase.url.dev` — the same two properties the Gradle build picks
# between by build type.

set -eu

ENV=${1:-prod}

case "$ENV" in
  prod) URL_KEY='supabase\.url'; FIREBASE_PROJECT='odo-mobile-ba9aa' ;;
  dev)  URL_KEY='supabase\.url\.dev'; FIREBASE_PROJECT='odo-mobile-dev' ;;
  *) echo "usage: sh supabase/check-legal.sh [dev|prod]" >&2; exit 1 ;;
esac

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
URL=$(sed -n "s/^$URL_KEY=//p" "$ROOT/local.properties" | tr -d '\r' | head -1)

if [ -z "$URL" ]; then
  echo "no ${URL_KEY} in local.properties" >&2
  exit 1
fi

BASE="$URL/functions/v1/legal"
PAGES="https://$FIREBASE_PROJECT.web.app"
echo "$ENV"
echo "  function $BASE"
echo "  pages    $PAGES"
echo

fail=0

for path in "" /terms /privacy /delete-account /privacy-policy /nope; do
  code=$(curl -so /dev/null -w '%{http_code}' "$BASE$path")
  want=200
  [ "$path" = "/nope" ] && want=404

  if [ "$code" = "$want" ]; then
    printf '  ok    %-18s %s\n' "${path:-/}" "$code"
  else
    printf '  FAIL  %-18s %s (want %s)\n' "${path:-/}" "$code" "$want"
    fail=1
    # 401 has one cause worth naming, because it is the one that comes back by itself.
    [ "$code" = "401" ] && echo '        -> "Verify JWT with legacy secret" is on again. Turn it off.'
  fi
done

echo
# The function renders the deletion page too, but nobody reads that copy: Supabase's default
# domain rewrites text/html to text/plain, which is why the real one is on Firebase Hosting and
# is checked further down. So FIREBASE_WEB_CONFIG only affects `supabase functions serve` and is
# reported rather than failed — a wrong value here breaks no deployed page.
served=$(curl -s "$BASE/delete-account")
if echo "$served" | grep -q 'not finished being set up'; then
  echo '  note  served form       FIREBASE_WEB_CONFIG unset (only affects functions serve)'
else
  echo "$served" \
    | grep -oE '"projectId":"[^"]*"' \
    | sed 's/^/  note  served form       /'
fi

body=$(curl -sX POST "$BASE/delete-account" -H 'Content-Type: application/json' -d '{"idToken":"not.a.token"}')
if [ "$body" = '{"error_code":"invalid_token"}' ]; then
  echo '  ok    POST guard        rejects a bad token'
elif echo "$body" | grep -q not_configured; then
  echo '  FAIL  POST guard        FIREBASE_PROJECT_ID is not set'
  fail=1
else
  echo "  FAIL  POST guard        unexpected: $body"
  fail=1
fi

echo

# The Firebase Hosting copy is the one anybody actually reads — Supabase's default domain
# rewrites HTML to text/plain. What matters here is that the pair matches: the page has to sign
# in against this environment's Firebase project and POST to this environment's function.
for path in / /privacy /terms /delete-account; do
  code=$(curl -so /dev/null -w '%{http_code}' "$PAGES$path")
  if [ "$code" = 200 ]; then
    printf '  ok    pages %-13s %s\n' "$path" "$code"
  else
    printf '  FAIL  pages %-13s %s (want 200)\n' "$path" "$code"
    [ "$code" = "404" ] && echo "        -> not deployed yet: sh web/deploy.sh $ENV"
    fail=1
  fi
done

page=$(curl -s "$PAGES/delete-account")

if echo "$page" | grep -q "\"projectId\":\"$FIREBASE_PROJECT\""; then
  echo "  ok    pages firebase     $FIREBASE_PROJECT"
else
  echo "  FAIL  pages firebase     not $FIREBASE_PROJECT"
  echo "$page" | grep -oE '"projectId":"[^"]*"' | sed 's/^/        got: /'
  echo "        -> sh web/deploy.sh $ENV rebuilds it from web/build.ts's table."
  fail=1
fi

if echo "$page" | grep -qF "$BASE/delete-account"; then
  echo "  ok    pages endpoint     $ENV function"
else
  echo "  FAIL  pages endpoint     points at another project"
  echo "$page" | grep -oE 'https://[a-z]+\.supabase\.co[^"'"'"']*delete-account' | sed 's/^/        got: /'
  fail=1
fi

# The POST is cross-origin — page on Firebase Hosting, function on Supabase — so without CORS
# headers the browser blocks it and the page fails at the last step, after the SMS has been sent
# and paid for. The origins are derived from FIREBASE_PROJECT_ID, so a missing header means that
# secret does not list this environment's Firebase project, or the deployed function predates
# the CORS support in index.ts.
if curl -s -D - -o /dev/null -X OPTIONS "$BASE/delete-account" \
  -H "Origin: $PAGES" -H 'Access-Control-Request-Method: POST' \
  | grep -qi "access-control-allow-origin: $PAGES"; then
  echo "  ok    CORS              $PAGES may POST"
else
  echo "  FAIL  CORS              $PAGES is blocked"
  echo "        -> FIREBASE_PROJECT_ID must list $FIREBASE_PROJECT, and the deployed function"
  echo "           must be current (supabase/legal.bundle.js). Both, then redeploy."
  fail=1
fi

echo
[ "$fail" = 0 ] && echo "all good" || echo "see FAIL lines above"
exit "$fail"

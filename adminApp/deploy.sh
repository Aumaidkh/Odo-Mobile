#!/bin/sh
# Build the admin panel for one environment and deploy it to that environment's site.
#
#   sh adminApp/deploy.sh dev    -> odo-admin-dev.web.app
#   sh adminApp/deploy.sh prod   -> odoapp-admin / admin.odoapp.in
#
# One command for both halves on purpose: the built bundle does not say which environment it
# was configured for, so building and deploying separately is how a panel pointed at production
# ends up on the development URL.
#
# **Production needs the database prepared first.** The panel is nothing without the admin
# schema — `admin_users` is what decides who may sign in at all — and that schema does not exist
# on the production project until somebody runs the rollout. Deploying before then puts a
# sign-in page on a public domain that refuses everybody, including whoever deployed it.
# The steps, in order, are in docs/ADMIN_PROD_ROLLOUT.md; this script checks the first one.

set -eu

ENV=${1:-}
if [ "$ENV" != "dev" ] && [ "$ENV" != "prod" ]; then
  echo "usage: sh adminApp/deploy.sh <dev|prod>" >&2
  exit 1
fi

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

if [ "$ENV" = "dev" ]; then
  # The dev Firebase web key, read from the debug google-services.json rather than kept in a
  # second place. It is a public client identifier; who may sign in is decided by the
  # admin_users table, server-side.
  KEY=$(python3 -c "import json;print(json.load(open('androidApp/src/debug/google-services.json'))['client'][0]['api_key'][0]['current_key'])")
  BUILD_ENV="SUPABASE_ENV=dev FIREBASE_WEB_API_KEY_DEV=$KEY"
else
  # Refuse to ship a panel that cannot sign anybody in. `admin_users` answers 404 from PostgREST
  # when the table does not exist and 401 when it does and is shut — which is the difference
  # between "the rollout has not been run" and "the rollout worked".
  URL=$(sed -n 's/^supabase\.url=//p' local.properties | tr -d ' \r' | head -1)
  ANON=$(sed -n 's/^supabase\.anonKey=//p' local.properties | tr -d ' \r' | head -1)
  if [ -z "$URL" ] || [ -z "$ANON" ]; then
    echo "supabase.url / supabase.anonKey missing from local.properties" >&2
    exit 1
  fi
  CODE=$(curl -s -o /dev/null -w '%{http_code}' \
    -H "apikey: $ANON" -H "Authorization: Bearer $ANON" \
    "$URL/rest/v1/admin_users?select=id&limit=1")
  if [ "$CODE" = "404" ]; then
    echo "admin_users does not exist on production ($URL)." >&2
    echo "Run the rollout first — see docs/ADMIN_PROD_ROLLOUT.md." >&2
    exit 1
  fi
  BUILD_ENV=""
fi

# shellcheck disable=SC2086
env $BUILD_ENV ./gradlew :adminApp:wasmJsBrowserDistribution

STAGE="adminApp/hosting/$ENV/public"
rm -rf "$STAGE"
mkdir -p "$STAGE"
cp -R adminApp/build/dist/wasmJs/productionExecutable/. "$STAGE/"
# Source maps are dropped: another 1.5 MB, and they hand out the whole source tree — which
# matters more for a staff tool than for the blog.
rm -f "$STAGE"/*.map "$STAGE"/*.LICENSE.txt

cd "adminApp/hosting/$ENV"
firebase deploy --only hosting --project "$ENV"

echo
if [ "$ENV" = "dev" ]; then
  echo "https://odo-admin-dev.web.app/"
else
  echo "https://admin.odoapp.in/"
fi

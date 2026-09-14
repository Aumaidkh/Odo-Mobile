#!/bin/sh
# Build the blog and its CMS for one environment and deploy them to that environment's site.
#
#   sh webApp/deploy.sh dev    -> odo-blog-dev.web.app,  talks to the dev Supabase project
#   sh webApp/deploy.sh prod   -> odoapp-blog / blog.odoapp.in, prod Supabase project
#
# Served at the ROOT of its own origin, not under /blog. That is the move: the blog used to be
# a subdirectory of the marketing site and is now a site, which is why this script exists at all
# rather than the landing deploy carrying it.
#
# One command for both halves on purpose, the same reason adminApp/deploy.sh is: the built
# bundle does not say which environment it was configured for, so building and deploying
# separately is how a CMS pointed at production ends up on the development URL.
#
# **Deploy order during the migration matters.** The landing site's 301s from /blog/* must go
# out *after* blog.odoapp.in resolves — publishing them first sends every reader and every
# crawler to a host that does not answer yet, which is worse than the old URLs simply staying.

set -eu

ENV=${1:-}
if [ "$ENV" != "dev" ] && [ "$ENV" != "prod" ]; then
  echo "usage: sh webApp/deploy.sh <dev|prod>" >&2
  exit 1
fi

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

if [ "$ENV" = "dev" ]; then
  # The dev Firebase web key, read from the debug google-services.json rather than kept in a
  # second place. It is a public client identifier; who may sign in is decided by the
  # blog_authors table, server-side.
  KEY=$(python3 -c "import json;print(json.load(open('androidApp/src/debug/google-services.json'))['client'][0]['api_key'][0]['current_key'])")
  SUPABASE_ENV=dev FIREBASE_WEB_API_KEY_DEV="$KEY" ./gradlew :webApp:wasmJsBrowserDistribution
else
  # Production needs no key here: :webCore falls back to the production web API key, which was
  # a constant in this repository long before it moved there.
  ./gradlew :webApp:wasmJsBrowserDistribution
fi

STAGE="webApp/hosting/$ENV/public"
rm -rf "$STAGE"
mkdir -p "$STAGE"
cp -R webApp/build/dist/wasmJs/productionExecutable/. "$STAGE/"
# Source maps are dropped: another 1.5 MB, and they hand out the whole source tree.
rm -f "$STAGE"/*.map "$STAGE"/*.LICENSE.txt

# The pre-rendered article pages, the sitemap and robots.txt. Googlebot runs the JavaScript but
# a Compose canvas leaves no text in the DOM afterwards, so without these there is nothing to
# index however patient the crawler is.
(cd landing && SUPABASE_ENV="$ENV" BLOG_STAGE="../$STAGE" deno run -A render-blog.ts)

cd "webApp/hosting/$ENV"
firebase deploy --only hosting --project "$ENV"

echo
if [ "$ENV" = "dev" ]; then
  echo "https://odo-blog-dev.web.app/     and /admin"
else
  echo "https://blog.odoapp.in/           and /admin"
fi

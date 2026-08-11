#!/bin/sh
# Build the legal pages for one environment and deploy them to that environment's Firebase
# Hosting site.
#
#   sh web/deploy.sh dev     -> odo-mobile-dev.web.app,   posts to the dev Supabase project
#   sh web/deploy.sh prod    -> odo-mobile-ba9aa.web.app, posts to the prod Supabase project
#
# One command for both halves on purpose. `web/public` holds whichever environment was built
# last and the deployed HTML does not say which, so building and deploying separately is how a
# production page ends up initialising a Firebase client for the development project.
#
# The alias maps to a project id in .firebaserc, and the pairs themselves are in web/build.ts.
# Deploying dev needs nothing that deploying prod does not — same login, same two commands.

set -eu

ENV=${1:-}

if [ "$ENV" != "dev" ] && [ "$ENV" != "prod" ]; then
  echo "usage: sh web/deploy.sh <dev|prod>" >&2
  exit 1
fi

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

deno run --allow-read --allow-write --allow-env web/build.ts "$ENV"
firebase deploy --only hosting --project "$ENV"

echo
echo "check it: sh supabase/check-legal.sh $ENV"

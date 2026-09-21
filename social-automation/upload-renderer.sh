#!/bin/sh
# Publishes the renderer to the `social-renderer` Storage bucket, which is what the
# social-render workflow actually runs. Editing renderer/render.js without running this
# changes nothing in production.
#
# Usage: sh social-automation/upload-renderer.sh   (reads social-automation/.env)
set -eu

dir=$(dirname "$0")
. "$dir/.env"

put() {
  curl -fsS -X POST \
    -H "apikey: $SUPABASE_SERVICE_ROLE_KEY" \
    -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY" \
    -H "Content-Type: $2" \
    -H "x-upsert: true" \
    --data-binary "@$dir/renderer/$1" \
    "$SUPABASE_URL/storage/v1/object/social-renderer/$1" >/dev/null
  echo "uploaded $1"
}

put render.js application/javascript
for tpl in "$dir"/renderer/templates/*.html; do
  put "templates/$(basename "$tpl")" text/html
done

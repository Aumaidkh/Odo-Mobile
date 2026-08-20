#!/bin/sh
# Checks that the blog's half of the database is actually there and actually shut.
#
#   sh supabase/check-blog.sh
#
# Reads the project URL and anon key from local.properties, the same two values
# the web app is built with. Everything below runs as `anon`, which is the point:
# these are the answers a stranger gets.
#
# It exists because the alternative is trusting a migration that ran once and a
# set of policies nobody has read back. A schema that is subtly open looks exactly
# like one that is closed until somebody asks it the right question.

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

# $1 label, $2 expected status, $3 path, $4.. extra curl args
check() {
  label=$1
  expected=$2
  path=$3
  shift 3
  actual=$(curl -s -o /dev/null -w '%{http_code}' \
    -H "apikey: $KEY" -H "Authorization: Bearer $KEY" \
    "$@" "$URL$path")
  if [ "$actual" = "$expected" ]; then
    printf '  ok    %s\n' "$label"
    pass=$((pass + 1))
  else
    printf '  FAIL  %s (expected %s, got %s)\n' "$label" "$expected" "$actual"
    fail=$((fail + 1))
  fi
}

# $1 label, $2 path — passes when the body is an empty array
check_empty() {
  body=$(curl -s -H "apikey: $KEY" -H "Authorization: Bearer $KEY" "$URL$2")
  if [ "$body" = "[]" ]; then
    printf '  ok    %s\n' "$1"
    pass=$((pass + 1))
  else
    printf '  FAIL  %s (expected [], got %s)\n' "$1" "$(printf '%s' "$body" | cut -c1-80)"
    fail=$((fail + 1))
  fi
}

echo "$URL"
echo
echo "what a reader may see"
check       "categories are public"       200 "/rest/v1/blog_categories?select=slug"
check       "authors are public"          200 "/rest/v1/blog_authors?select=slug"
check       "published posts are public"  200 "/rest/v1/blog_posts?select=slug&limit=1"
check       "media index is public"       200 "/rest/v1/blog_media?select=name&limit=1"

echo
echo "what a reader may not see"
# Not a 403: RLS filters rows rather than refusing the request, so a closed table
# answers 200 with nothing in it. An empty array is the pass.
check_empty "drafts are invisible"        "/rest/v1/blog_posts?select=id&status=eq.draft"
check_empty "subscribers are invisible"   "/rest/v1/blog_subscribers?select=email"
check_empty "topic requests are invisible" "/rest/v1/blog_topic_requests?select=email"
check_empty "view counts are invisible"   "/rest/v1/blog_post_daily_views?select=post_id"

echo
echo "what a reader may do"
check       "counting a read is allowed"  204 "/rest/v1/rpc/blog_record_view" \
  -X POST -H 'Content-Type: application/json' \
  -d '{"p_slug":"__does-not-exist__","p_from_search":false}'
check       "subscribing is allowed"      201 "/rest/v1/blog_subscribers" \
  -X POST -H 'Content-Type: application/json' -H 'Prefer: resolution=merge-duplicates' \
  -d '{"email":"check-blog@odoapp.in"}'

echo
echo "what a reader may not do"
check       "writing a post is refused"   401 "/rest/v1/blog_posts" \
  -X POST -H 'Content-Type: application/json' -d '{"title":"nope"}'
check       "reading the analytics is refused" 401 "/rest/v1/rpc/blog_analytics" \
  -X POST -H 'Content-Type: application/json' -d '{"p_days":30}'
check       "the account lookup is not exposed" 404 "/rest/v1/rpc/auth_user_id_by_email" \
  -X POST -H 'Content-Type: application/json' -d '{"p_email":"someone@example.com"}'

echo
echo "the sign-in exchange"
check       "blog-session rejects a junk token" 401 "/functions/v1/blog-session" \
  -X POST -H 'Content-Type: application/json' -d '{"idToken":"not-a-token"}'

echo
printf '%s passed, %s failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]

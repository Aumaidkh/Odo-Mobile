#!/bin/sh
# Checks that the admin panel's half of the database is actually there and actually shut.
#
#   sh supabase/check-admin.sh          # prod, the default
#   sh supabase/check-admin.sh dev
#
# Reads the project URL and anon key from local.properties, the same values the web apps are
# built with, choosing between the two environments the way check-legal.sh does. Everything
# below runs as `anon`, which is the point: these are the answers a stranger gets, and a
# stranger is who this table set has to be closed to.
#
# The sibling of check-blog.sh, and it exists for the same reason. The Kotlin tests are mocked
# HTTP — they cannot see a policy at all. A permission model that is subtly open looks exactly
# like one that is closed until somebody asks it the right question.

set -eu

ENV=${1:-prod}

case "$ENV" in
  prod) SUFFIX='' ;;
  dev)  SUFFIX='\.dev' ;;
  *) echo "usage: sh supabase/check-admin.sh [dev|prod]" >&2; exit 1 ;;
esac

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PROPERTIES="$ROOT/local.properties"

if [ ! -f "$PROPERTIES" ]; then
  echo "no local.properties — nothing to check against" >&2
  exit 1
fi

# The trailing `=` in each pattern is load-bearing: without it the prod pattern also matches
# `supabase.url.dev=` and the script silently checks the wrong project.
URL=$(sed -n "s/^supabase\.url$SUFFIX=//p"     "$PROPERTIES" | tr -d ' \r' | head -1)
KEY=$(sed -n "s/^supabase\.anonKey$SUFFIX=//p" "$PROPERTIES" | tr -d ' \r' | head -1)

if [ -z "$URL" ] || [ -z "$KEY" ]; then
  echo "supabase.url$SUFFIX or supabase.anonKey$SUFFIX missing from local.properties" >&2
  exit 1
fi

pass=0
fail=0

# $1 label, $2 expected status (or "a|b" for either), $3 path, $4.. extra curl args
check() {
  label=$1
  expected=$2
  path=$3
  shift 3
  actual=$(curl -s -o /dev/null -w '%{http_code}' \
    -H "apikey: $KEY" -H "Authorization: Bearer $KEY" \
    "$@" "$URL$path")
  case "|$expected|" in
    *"|$actual|"*) matched=yes ;;
    *) matched=no ;;
  esac
  if [ "$matched" = yes ]; then
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

echo "$ENV"
echo "  $URL"

echo
echo "the tables exist"
# A closed table and a table that was never created both return nothing useful to `anon`, so
# "it is shut" is not evidence the migration ran. PostgREST distinguishes them by status:
# 200 with no rows is a table under RLS, 404 is a table that is not there.
check       "admin_users exists"             200 "/rest/v1/admin_users?select=email&limit=1"
check       "admin_roles exists"             200 "/rest/v1/admin_roles?select=slug&limit=1"
check       "admin_role_permissions exists"  200 "/rest/v1/admin_role_permissions?select=permission&limit=1"
check       "admin_user_roles exists"        200 "/rest/v1/admin_user_roles?select=role_slug&limit=1"
check       "admin_audit_log exists"         200 "/rest/v1/admin_audit_log?select=id&limit=1"

echo
echo "what a stranger may see"
# Nothing. Every policy is scoped `to authenticated`, so `anon` matches none of them and RLS
# filters rather than refuses — a closed table answers 200 with an empty array. That is the
# same shape check-blog.sh asserts, and the empty array is the pass.
check_empty "the staff list is invisible"      "/rest/v1/admin_users?select=email"
check_empty "the roles are invisible"          "/rest/v1/admin_roles?select=slug"
check_empty "the permission map is invisible"  "/rest/v1/admin_role_permissions?select=permission"
check_empty "who holds what is invisible"      "/rest/v1/admin_user_roles?select=role_slug"
check_empty "the audit log is invisible"       "/rest/v1/admin_audit_log?select=id"

echo
echo "what a stranger may not do"
# 401, and specifically not 200-with-false. These are revoked from anon at the GRANT level, so
# Postgres refuses the call rather than answering it — a `false` handed to a caller who should
# not be asking invites the answer being treated as data.
check       "the permission check is not exposed" 401 "/rest/v1/rpc/admin_has" \
  -X POST -H 'Content-Type: application/json' -d '{"p_permission":"admin.roles.write"}'
check       "the staff check is not exposed"      401 "/rest/v1/rpc/is_admin" \
  -X POST -H 'Content-Type: application/json'
check       "the admin-id lookup is not exposed"  401 "/rest/v1/rpc/current_admin_id" \
  -X POST -H 'Content-Type: application/json'

# 401, not 403: with no policy matching `anon` there is nothing to evaluate, and PostgREST
# reports the refusal rather than silently writing nothing.
check       "adding an admin is refused"      401 "/rest/v1/admin_users" \
  -X POST -H 'Content-Type: application/json' -d '{"email":"check-admin@odoapp.in"}'
check       "granting a role is refused"      401 "/rest/v1/admin_user_roles" \
  -X POST -H 'Content-Type: application/json' \
  -d '{"admin_id":"00000000-0000-0000-0000-000000000000","role_slug":"super_admin"}'
check       "adding a permission is refused"  401 "/rest/v1/admin_role_permissions" \
  -X POST -H 'Content-Type: application/json' -d '{"role_slug":"support","permission":"admin.roles.write"}'

# The audit log has no insert policy for anybody, not just for strangers. Only the definer-owned
# trigger writes here, which is the whole reason the log is worth reading back.
check       "writing to the audit log is refused" 401 "/rest/v1/admin_audit_log" \
  -X POST -H 'Content-Type: application/json' -d '{"action":"INSERT","subject_type":"nope"}'

echo
echo "the sign-in exchange"
# Deployed --no-verify-jwt, so these reach the handler on the anon key alone. That is the
# point: somebody signing in has no Supabase token yet. What stops a stranger is the Firebase
# signature check and the admin_users lookup behind it, not the gateway.
check       "admin-session refuses GET"           405 "/functions/v1/admin-session" -X GET
check       "admin-session refuses no body"       400 "/functions/v1/admin-session" \
  -X POST -H 'Content-Type: application/json'
check       "admin-session refuses a junk token"  401 "/functions/v1/admin-session" \
  -X POST -H 'Content-Type: application/json' -d '{"idToken":"not-a-token"}'
# A syntactically valid JWT that nobody signed. Catches a deploy where the JWKS verification
# was skipped or misconfigured — the shape alone must never be enough.
check       "admin-session refuses an unsigned token" 401 "/functions/v1/admin-session" \
  -X POST -H 'Content-Type: application/json' \
  -d '{"idToken":"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJhQGIuY29tIn0.x"}'
check       "admin-session answers preflight"     204 "/functions/v1/admin-session" -X OPTIONS

# The lookup admin-session needs at step 3. It ships in 20260831130000_admin_session_support.sql
# rather than being borrowed from the blog schema, so that the panel works on a project that
# has never had a blog. 401 means it is there and shut; 404 means that migration has not run
# and sign-in will fail at step 3 with a 500.
check       "the account lookup exists and is shut" 401 "/rest/v1/rpc/auth_user_id_by_email" \
  -X POST -H 'Content-Type: application/json' -d '{"p_email":"someone@example.com"}'
check       "the identity call exists and is shut" 401 "/rest/v1/rpc/my_admin_identity" \
  -X POST -H 'Content-Type: application/json'

echo
printf '%s passed, %s failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]

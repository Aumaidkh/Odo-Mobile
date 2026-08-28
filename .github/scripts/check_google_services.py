"""Does this google-services.json cover the applicationId we are about to build?

Run as: check_google_services.py <path-to-json> <applicationId>
Exits 0 when the file covers it, 1 when it does not, and prints what it found either way.

Kept as a file rather than inlined in the workflow because it needs a heredoc, and a
heredoc nested inside a YAML block scalar inside an `if` is the kind of thing that breaks
silently on an indentation change nobody reviewed.

The matching rule mirrors the Google Services Gradle plugin's own lookup: an exact match,
or a client registered for any dot-prefix of the id. That prefix fallback is what lets a
file listing only `com.hopcape.odo` serve a debug build installing as
`com.hopcape.odo.debug`.
"""

import json
import sys


def covered_packages(path):
    with open(path) as handle:
        data = json.load(handle)
    return {
        client["client_info"]["android_client_info"]["package_name"]
        for client in data.get("client", [])
    }


def candidates(application_id):
    """`a.b.c` -> {`a.b.c`, `a.b`, `a`} — the ids a registered client could match."""
    parts = application_id.split(".")
    return {".".join(parts[:end]) for end in range(len(parts), 0, -1)}


def main():
    if len(sys.argv) != 3:
        print("usage: check_google_services.py <json> <applicationId>", file=sys.stderr)
        return 2

    path, application_id = sys.argv[1], sys.argv[2]

    try:
        covered = covered_packages(path)
    except (OSError, ValueError, KeyError) as failure:
        # Almost always a truncated or wrongly-encoded secret.
        print(f"{path} is not a usable google-services.json: {failure}")
        return 1

    print(f"google-services.json covers: {', '.join(sorted(covered)) or '(nothing)'}")
    print(f"this build installs as: {application_id}")

    if covered & candidates(application_id):
        return 0

    print("no client matches, and no dot-prefix of it matches either")
    return 1


if __name__ == "__main__":
    sys.exit(main())

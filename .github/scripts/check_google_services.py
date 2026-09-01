"""Does this google-services.json cover the applicationId we are about to build?

Run as: check_google_services.py <path-to-json> <applicationId>
Exits 0 when the file covers it, 1 when it does not, and prints what it found either way.

Kept as a file rather than inlined in the workflow because it needs a heredoc, and a
heredoc nested inside a YAML block scalar inside an `if` is the kind of thing that breaks
silently on an indentation change nobody reviewed.

The match has to be exact. The plugin does not fall back to a client registered for a
shorter prefix — a file listing only `com.hopcape.odo` is rejected for a debug build
installing as `com.hopcape.odo.debug`, which is what the first CI run of this workflow
found. A local checkout gets away with it only because it also has a variant-specific
`androidApp/src/debug/google-services.json`, which the plugin prefers over the root file.
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

    if application_id in covered:
        return 0

    print("no client is registered for it")
    return 1


if __name__ == "__main__":
    sys.exit(main())

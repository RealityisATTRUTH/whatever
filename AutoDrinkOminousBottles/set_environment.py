"""
Marks every version of a Modrinth project as client-side only (or another environment), in one go.

Modrinth now stores the environment on each version, so a project with many versions would otherwise need each one
edited by hand. Uses the same MODRINTH_TOKEN as upload_modrinth.py (it needs the "Write to version data" scope).

    python set_environment.py --project YOUR_PROJECT_SLUG --dry-run
    python set_environment.py --project YOUR_PROJECT_SLUG

Needs Python 3.8 or newer and nothing else.
"""
import argparse
import json
import os
import urllib.error
import urllib.request

API3 = "https://api.modrinth.com/v3"
USER_AGENT = "RealityisATTRUTH/whatever set_environment.py"


def call(method, path, token, body=None):
    req = urllib.request.Request(API3 + path, data=None if body is None else json.dumps(body).encode(), method=method)
    req.add_header("User-Agent", USER_AGENT)
    req.add_header("Authorization", token)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as r:
            data = r.read()
            return json.loads(data) if data else None
    except urllib.error.HTTPError as e:
        raise SystemExit(f"Modrinth said {e.code} for {method} {path}: {e.read().decode(errors='replace')}")


def main():
    ap = argparse.ArgumentParser(description="Set the environment on every version of a Modrinth project.")
    ap.add_argument("--project", required=True, help="the project's slug or id, from its page address")
    ap.add_argument("--environment", default="client_only",
                    help="client_only (default), server_only, client_and_server, ...")
    ap.add_argument("--dry-run", action="store_true", help="show what would change, change nothing")
    args = ap.parse_args()

    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        raise SystemExit("Set the MODRINTH_TOKEN environment variable first.")

    versions = call("GET", f"/project/{args.project}/version", token)
    print(f"{len(versions)} versions")
    for v in versions:
        now = v.get("environment")
        line = f"{v['version_number']:<40} {now} -> {args.environment}"
        if now == args.environment:
            print(f"OK   {v['version_number']:<40} already {now}")
        elif args.dry_run:
            print(f"WOULD SET {line}")
        else:
            call("PATCH", f"/version/{v['id']}", token, {"environment": args.environment})
            print(f"SET  {line}")


if __name__ == "__main__":
    main()

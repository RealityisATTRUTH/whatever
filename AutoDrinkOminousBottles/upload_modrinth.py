"""
Uploads every jar in this folder to a Modrinth project, each tagged with its loader and Minecraft versions.

It reads manifest.json (written by package.py), so run it from the unzipped pack folder:

    python upload_modrinth.py --project YOUR_PROJECT_SLUG --dry-run
    python upload_modrinth.py --project YOUR_PROJECT_SLUG

Your Modrinth token goes in the MODRINTH_TOKEN environment variable, never on the command line or in a file you
share. Make one at https://modrinth.com/settings/pats with the "Create versions" scope.

    Windows (PowerShell):  $env:MODRINTH_TOKEN = "mrp_..."
    Mac or Linux:          export MODRINTH_TOKEN=mrp_...

--dry-run shows what it would upload without uploading anything. Versions that are already on the project are
skipped, so it's safe to run again if it stops halfway.

Needs Python 3.8 or newer and nothing else.
"""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
import uuid

API = "https://api.modrinth.com/v2"
USER_AGENT = "RealityisATTRUTH/whatever upload_modrinth.py"
FABRIC_API = "P7dR8mSH"   # Fabric API's Modrinth project id
LOADER_NAMES = {"fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}


def request(method, path, token=None, body=None, content_type=None):
    req = urllib.request.Request(API + path, data=body, method=method)
    req.add_header("User-Agent", USER_AGENT)
    if token:
        req.add_header("Authorization", token)
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        hint = ""
        if e.code == 404 and path.startswith("/project/"):
            hint = ("\nCheck the slug matches your project's page address. If the project is a draft, your token"
                    " needs the 'Read projects' scope as well as 'Create versions'.")
        if e.code == 401:
            hint = "\nThe token was refused. Check it's pasted in full and hasn't expired."
        raise SystemExit(f"Modrinth said {e.code} for {method} {path}: {e.read().decode(errors='replace')}{hint}")


def key(v):
    return [int(p) for p in re.findall(r"\d+", v)]


def versions_in(spec, releases):
    """The release versions a range covers. Handles '1.21.4', '>=1.21 <1.21.2' and '[1.21.1,1.21.2)'."""
    spec = spec.strip()
    m = re.fullmatch(r"([\[(])\s*([\w.]*)\s*,\s*([\w.]*)\s*([\])])", spec)
    if m:
        lo_inc, lo, hi, hi_inc = m.group(1) == "[", m.group(2), m.group(3), m.group(4) == "]"
        def ok(v):
            if lo and (key(v) < key(lo) or (key(v) == key(lo) and not lo_inc)): return False
            if hi and (key(v) > key(hi) or (key(v) == key(hi) and not hi_inc)): return False
            return True
        return [v for v in releases if ok(v)]
    if spec.startswith((">", "<")):
        parts = spec.split()
        def ok(v):
            for p in parts:
                op, x = re.fullmatch(r"(>=|<=|>|<)([\w.]+)", p).groups()
                c = (key(v) > key(x)) - (key(v) < key(x))
                if (op == ">=" and c < 0) or (op == ">" and c <= 0) or (op == "<=" and c > 0) or (op == "<" and c >= 0):
                    return False
            return True
        return [v for v in releases if ok(v)]
    return [v for v in releases if v == spec]


def multipart(data, filename, filedata):
    boundary = uuid.uuid4().hex
    out = bytearray()
    out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"data\"\r\n\r\n".encode()
    out += json.dumps(data).encode() + b"\r\n"
    out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n".encode()
    out += b"Content-Type: application/java-archive\r\n\r\n" + filedata + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return bytes(out), f"multipart/form-data; boundary={boundary}"


def main():
    ap = argparse.ArgumentParser(description="Upload every jar in this pack to Modrinth.")
    ap.add_argument("--project", required=True, help="the Modrinth project's slug or id, from its page URL")
    ap.add_argument("--changelog", default="", help="text shown on each version's page")
    ap.add_argument("--type", default="release", choices=["release", "beta", "alpha"])
    ap.add_argument("--dry-run", action="store_true", help="show what would be uploaded, upload nothing")
    args = ap.parse_args()

    here = os.path.dirname(os.path.abspath(__file__))
    manifest = json.load(open(os.path.join(here, "manifest.json")))
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        raise SystemExit("Set the MODRINTH_TOKEN environment variable first (see the top of this file).")

    releases = [v["version"] for v in request("GET", "/tag/game_version") if v["version_type"] == "release"]
    # Sent with the token, so a draft project (one only you can see) is found too.
    project = request("GET", f"/project/{args.project}", token)
    existing = {v["version_number"] for v in request("GET", f"/project/{project['id']}/version", token)}
    print(f"Project: {project['title']} ({project['id']}), {len(existing)} versions already there")

    for entry in manifest["jars"]:
        loader = entry["loader"]
        games = versions_in(entry["range"], releases)
        number = f"{manifest['version']}+{loader}-{entry['label']}"
        path = os.path.join(here, loader, entry["file"])
        line = f"{number:<40} {', '.join(sorted(games, key=key))}"
        if not games:
            print(f"SKIP {line}  (no Minecraft versions matched {entry['range']!r})")
            continue
        if number in existing:
            print(f"HAVE {line}")
            continue
        if args.dry_run:
            print(f"WOULD UPLOAD {line}")
            continue
        data = {
            "project_id": project["id"],
            "name": f"{manifest['version']} for {LOADER_NAMES[loader]} {entry['label']}",
            "version_number": number,
            "changelog": args.changelog,
            "version_type": args.type,
            "loaders": [loader],
            "game_versions": games,
            "featured": False,
            "dependencies": [{"project_id": FABRIC_API, "dependency_type": "required"}] if loader == "fabric" else [],
            "file_parts": ["file"],
            "primary_file": "file",
        }
        body, ctype = multipart(data, entry["file"], open(path, "rb").read())
        made = request("POST", "/version", token, body, ctype)
        # Modrinth keeps the environment on each version; this mod only runs on the player's game.
        env = urllib.request.Request(f"https://api.modrinth.com/v3/version/{made['id']}", method="PATCH",
                                     data=json.dumps({"environment": "client_only"}).encode())
        env.add_header("User-Agent", USER_AGENT)
        env.add_header("Authorization", token)
        env.add_header("Content-Type", "application/json")
        try:
            urllib.request.urlopen(env).close()
        except urllib.error.HTTPError as e:
            print(f"  (couldn't mark it client-side only: {e.code}. Run set_environment.py afterwards.)")
        print(f"UPLOADED {line}")


if __name__ == "__main__":
    main()

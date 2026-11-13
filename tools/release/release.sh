#!/usr/bin/env bash
# Build a Windows release of SHARE, publish it to the web backend in share_web, and check that the
# live version endpoint serves it. Spec: docs/SPEC-release-pipeline.md.
#
#   tools/release/release.sh 1.0.1                       # production build (malenia)
#   tools/release/release.sh 1.0.1 --server http://192.168.122.183:10001   # test-container build
#
# Options:
#   --server URL       serverBaseUrl baked into the app and the updater (no /api)
#                      default: https://malenia.feld.cvut.cz/koubic
#   --verify-api URL   API base used for the post-publish check. Default: the internal
#                      http://192.168.122.183:10001/api, because malenia's HTTPS address is not
#                      reachable from the build machine. Both reach the same backend.
#   --host ALIAS       ssh alias of the server (default: share_web)
#   --no-build         publish what is already in packaging/build/release/<version>/
#   --dry-run          build and check locally, upload nothing
#
# Publishing is atomic: files go to <releases>/.incoming-<v>/, are verified there, and only then
# renamed to <releases>/<v>/. The backend ignores names that are not plain x.y.z, so clients never
# see a half-uploaded release. A published version is never overwritten: bump the version instead.
# To withdraw a release, delete its directory on the server.
set -euo pipefail

SERVER="https://malenia.feld.cvut.cz/koubic"
VERIFY_API="http://192.168.122.183:10001/api"
HOST="share_web"
RELEASES_DIR="/srv/task-protocoller/releases"
PLATFORM="windows-x86_64"
BUNDLE="install-windows-x64.zip"
BUILD=1
DRY_RUN=0

die() { echo "release: $*" >&2; exit 1; }
step() { echo; echo "==> $*"; }

version=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --server) SERVER="${2:?--server needs a URL}"; shift 2 ;;
    --verify-api) VERIFY_API="${2:?--verify-api needs a URL}"; shift 2 ;;
    --host) HOST="${2:?--host needs an ssh alias}"; shift 2 ;;
    --no-build) BUILD=0; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    -*) die "unknown option $1" ;;
    *) [[ -z "$version" ]] || die "only one version allowed"; version="$1"; shift ;;
  esac
done
[[ -n "$version" ]] || die "usage: release.sh <x.y.z> [--server URL] [--no-build] [--dry-run]"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "version must be plain x.y.z (AppVersion.parse rejects anything else)"
SERVER="${SERVER%/}"
VERIFY_API="${VERIFY_API%/}"
[[ "$SERVER" =~ ^https?:// ]] || die "--server must start with http:// or https://"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
out="$root/packaging/build/release/$version"
ssh_cmd=(ssh -o BatchMode=yes -o ServerAliveInterval=10 "$HOST")

# Numeric x.y.z comparison: prints -1, 0 or 1.
vcmp() { python -c 'import sys; a,b=(tuple(map(int,v.split("."))) for v in sys.argv[1:3]); print((a>b)-(a<b))' "$1" "$2"; }

step "release $version for $PLATFORM, server $SERVER"

if [[ $DRY_RUN -eq 0 ]]; then
  step "checking the server before building"
  "${ssh_cmd[@]}" "test -d '$RELEASES_DIR'" || die "$HOST:$RELEASES_DIR does not exist (see the web repo's docs/container_deployment.md)"
  if "${ssh_cmd[@]}" "test -e '$RELEASES_DIR/$version'"; then
    die "$version is already published on $HOST; releases are immutable, bump the version"
  fi
  live=$(curl -s -m 20 "$VERIFY_API/version/latest?platform=$PLATFORM" | python -c 'import sys,json
try: print(json.load(sys.stdin).get("release",""))
except Exception: print("")')
  if [[ -n "$live" && "$(vcmp "$version" "$live")" != "1" ]]; then
    die "$version is not newer than the live release $live; clients would ignore it"
  fi
  echo "live release: ${live:-none}"
fi

if [[ $BUILD -eq 1 ]]; then
  step "building (gradle :packaging:releaseWindowsX64)"
  (cd "$root" && ./gradlew --console=plain -PappVersion="$version" -PserverBaseUrl="$SERVER" :packaging:releaseWindowsX64)
fi

step "checking the build in $out"
[[ -d "$out" ]] || die "no build output at $out"
for f in app.zip "runtime-$PLATFORM.zip" "ffmpeg-$PLATFORM.zip" "$BUNDLE" "manifest-$PLATFORM.json"; do
  [[ -f "$out/$f" && -f "$out/$f.sha256" ]] || die "missing $f or its .sha256"
done
(cd "$out" && sha256sum -c --quiet ./*.sha256) || die "local checksums do not match"

# The server URL is baked in at build time, so a test build must never be published by mistake:
# check both places it ends up against --server.
python - "$out" "$SERVER" "$version" "$BUNDLE" <<'EOF' || die "build does not match --server/version (rebuild without --no-build?)"
import io, json, sys, zipfile
out, server, version, bundle = sys.argv[1:5]
with zipfile.ZipFile(f"{out}/{bundle}") as z:
    props = z.read("updater.properties").decode()
    endpoint = next(l.split("=", 1)[1].strip() for l in props.splitlines() if l.startswith("endpoint="))
    installed = json.loads(z.read("installed.json"))
with zipfile.ZipFile(f"{out}/app.zip") as z:
    with zipfile.ZipFile(io.BytesIO(z.read("app.jar"))) as jar:
        baked = json.loads(jar.read("org/example/app/server.json"))["serverBaseUrl"]
    app_version = json.loads(z.read("version.json"))["appVersion"]
problems = []
if endpoint != f"{server}/api/version/latest": problems.append(f"updater endpoint is {endpoint}")
if baked != server: problems.append(f"app.jar serverBaseUrl is {baked}")
if app_version != version: problems.append(f"app.zip version.json says {app_version}")
if installed.get("release") != version: problems.append(f"installed.json release is {installed.get('release')}")
for p in problems: print("  " + p, file=sys.stderr)
print(f"updater endpoint {endpoint}\napp serverBaseUrl {baked}\napp version {app_version}")
sys.exit(1 if problems else 0)
EOF

if [[ $DRY_RUN -eq 1 ]]; then
  step "dry run: nothing uploaded"; ls -l "$out"; exit 0
fi

incoming="$RELEASES_DIR/.incoming-$version"
step "uploading to $HOST:$incoming"
"${ssh_cmd[@]}" "rm -rf '$incoming' && install -d -m 755 '$incoming'"
scp -o BatchMode=yes -q "$out"/* "$HOST:$incoming/"

step "verifying on the server, then publishing"
"${ssh_cmd[@]}" "set -e; cd '$incoming' && sha256sum -c --quiet ./*.sha256 && chmod 644 ./* && cd .. &&
  test ! -e '$version' && mv '.incoming-$version' '$version' && ls -l '$version'"

step "checking the live endpoint ($VERIFY_API)"
python - "$VERIFY_API" "$PLATFORM" "$version" "$out" <<'EOF' || die "the live endpoint does not serve $version correctly"
import json, os, sys, urllib.parse, urllib.request
api, platform, version, out = sys.argv[1:5]
endpoint = f"{api}/version/latest"
with urllib.request.urlopen(f"{endpoint}?platform={platform}", timeout=30) as r:
    body = json.load(r)
manifest = json.load(open(f"{out}/manifest-{platform}.json"))
expected = {c["id"]: c["artifact"] for c in manifest["components"]}
ok = body.get("release") == version
print(f"release {body.get('release')}")
served = {c["id"]: c for c in body.get("components", [])}
for cid, artifact in expected.items():
    c = served.get(cid)
    local = open(f"{out}/{artifact}.sha256").read().split()[0].lower()
    if c is None:
        print(f"  {cid}: MISSING from the response"); ok = False; continue
    url = urllib.parse.urljoin(endpoint, c["url"])  # the updater resolves it the same way
    req = urllib.request.Request(url, method="HEAD")
    with urllib.request.urlopen(req, timeout=30) as r:
        length = int(r.headers["Content-Length"])
    good = c["checksum"] == local and length == os.path.getsize(f"{out}/{artifact}")
    ok &= good
    print(f"  {cid}: {'ok' if good else 'MISMATCH'}  {url}  {length} bytes")
sys.exit(0 if ok else 1)
EOF

step "published $version"
echo "Clients reach it through $SERVER. From a network that reaches it, check:"
echo "  curl '$SERVER/api/version/latest?platform=$PLATFORM'"

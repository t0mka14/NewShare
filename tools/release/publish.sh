#!/usr/bin/env bash
# Publish a built release to the demo mock server, so the updater can find it.
#
#   ./gradlew -PappVersion=1.0.1 :packaging:releaseLinuxX64
#   tools/release/publish.sh 1.0.1
#
# Content, not code: the server reads data/releases/ on every request, so this never restarts the
# service (unlike tools/mock-server/deploy.sh, which deploys server.py itself).
set -euo pipefail

SSH_HOST="${SSH_HOST:-root@192.168.122.183}"
SSH_PORT="${SSH_PORT:-10000}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/rsa_kouba}"
REMOTE_DIR="${REMOTE_DIR:-/opt/share-mock-server}"
BASE_URL="${BASE_URL:-http://${SSH_HOST#*@}:10001}"
PLATFORM="${PLATFORM:-linux-x86_64}"

version="${1:?usage: publish.sh <x.y.z>}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  echo "version must be plain x.y.z — AppVersion.parse rejects anything else" >&2; exit 2; }

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
local_dir="$here/packaging/build/release/$version"
[[ -f "$local_dir/app.zip" && -f "$local_dir/app.zip.sha256" ]] || {
  echo "missing $local_dir/app.zip(.sha256)" >&2
  echo "build it first: ./gradlew -PappVersion=$version :packaging:releaseLinuxX64" >&2
  exit 2; }

ssh_cmd=(ssh -i "$SSH_KEY" -p "$SSH_PORT" "$SSH_HOST")
scp_cmd=(scp -i "$SSH_KEY" -P "$SSH_PORT")

echo "==> verifying locally before upload"
( cd "$local_dir" && sha256sum -c ./*.sha256 )

echo "==> uploading to $SSH_HOST:$REMOTE_DIR/data/releases/$version"
"${ssh_cmd[@]}" "install -d -m 755 '$REMOTE_DIR/data/releases'"
# The artifact layout :packaging produces is already the layout the server expects, so this is a
# plain recursive copy with no renaming.
"${scp_cmd[@]}" -r "$local_dir" "$SSH_HOST:$REMOTE_DIR/data/releases/"

echo "==> verifying after upload"
"${ssh_cmd[@]}" "cd '$REMOTE_DIR/data/releases/$version' && sha256sum -c ./*.sha256 && ls -l"

# The manifest is per-platform, so ask the way a client does — without the parameter the server
# correctly answers with no components, which looks like a failed publish.
echo "==> GET /api/version/latest?platform=$PLATFORM now returns:"
curl -sf "$BASE_URL/api/version/latest?platform=$PLATFORM"; echo

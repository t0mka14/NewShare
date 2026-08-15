#!/usr/bin/env bash
# Deploy the SHARE mock configuration server to the demo container.
#
#   ./deploy.sh                 # copy + restart
#   SSH_HOST=… SSH_PORT=… ./deploy.sh
#
# The container has no pip, so the server is stdlib-only and needs no install step.
# Apache is disabled on first deploy because the mock server binds port 80 itself.
set -euo pipefail

SSH_HOST="${SSH_HOST:-root@192.168.122.183}"
SSH_PORT="${SSH_PORT:-10000}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/rsa_kouba}"
REMOTE_DIR="${REMOTE_DIR:-/opt/share-mock-server}"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ssh_cmd=(ssh -i "$SSH_KEY" -p "$SSH_PORT" "$SSH_HOST")
scp_cmd=(scp -i "$SSH_KEY" -P "$SSH_PORT")

echo "==> preparing $REMOTE_DIR on $SSH_HOST"
"${ssh_cmd[@]}" "install -d -m 755 '$REMOTE_DIR' '$REMOTE_DIR/data'"

echo "==> copying server.py and the systemd unit"
"${scp_cmd[@]}" "$here/server.py" "$SSH_HOST:$REMOTE_DIR/server.py"
"${scp_cmd[@]}" "$here/share-mock-server.service" "$SSH_HOST:/etc/systemd/system/share-mock-server.service"

echo "==> freeing port 80 (apache2) and (re)starting the service"
"${ssh_cmd[@]}" bash -s <<'REMOTE'
set -euo pipefail
chmod 755 /opt/share-mock-server/server.py
# Parse the multipart shapes real clients send before the new code takes over the running service.
python3 /opt/share-mock-server/server.py --selftest
if systemctl is-enabled --quiet apache2 2>/dev/null || systemctl is-active --quiet apache2; then
  systemctl disable --now apache2
  echo "apache2 stopped and disabled"
fi
systemctl daemon-reload
systemctl enable --now share-mock-server
systemctl restart share-mock-server
sleep 1
systemctl --no-pager --lines=5 status share-mock-server | head -12
REMOTE

echo "==> health check"
curl -sf "http://${SSH_HOST#*@}:10001/health" && echo
echo "web UI: http://${SSH_HOST#*@}:10001/config"

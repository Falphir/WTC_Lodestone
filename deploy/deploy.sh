#!/bin/sh
# Ships a release onto this box. Run as root:
#
#   sudo deploy/deploy.sh            # the newest release
#   sudo deploy/deploy.sh v0.2.0     # a specific tag (also how you roll back)
#
# First-time setup is deploy/README.md -- this script only swaps artifacts and restarts.
set -eu

REPO=Falphir/WTC_Lodestone
ROOT=/opt/lodestone
TAG=${1:-latest}

[ "$(id -u)" = 0 ] || { echo "run me as root" >&2; exit 1; }

if [ "$TAG" = latest ]; then
    base="https://github.com/$REPO/releases/latest/download"
else
    base="https://github.com/$REPO/releases/download/$TAG"
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

echo "==> downloading $TAG"
curl -fL --progress-bar -o "$tmp/hub.jar" "$base/wtc_lodestone-hub.jar"
curl -fL --progress-bar -o "$tmp/bot.tar.gz" "$base/wtc_lodestone-bot.tar.gz"
# The mod jar is not touched here. It only takes effect when that game server restarts, so grab it
# from the release page when you mean to do that -- see deploy/README.md.

# The hub runs Flyway on boot and there are no down-migrations, so this dump is the only way back.
echo "==> backing up the database first"
systemctl start lodestone-backup.service

echo "==> stopping services"
systemctl stop lodestone-bot lodestone-hub

install -o lodestone -g lodestone -m 644 "$tmp/hub.jar" "$ROOT/wtc_lodestone-hub.jar"

tar xzf "$tmp/bot.tar.gz" -C "$tmp"
rm -rf "$ROOT/bot"
mv "$tmp/discord" "$ROOT/bot"
chown -R lodestone:lodestone "$ROOT/bot"
echo "==> installing bot dependencies"
runuser -u lodestone -- sh -c "cd $ROOT/bot && npm ci --omit=dev"

echo "==> starting services"
systemctl start lodestone-hub lodestone-bot

# The hub's own answer to "am I actually up", including its database connection.
port=$(sed -n 's/^SERVER_PORT=//p' /etc/lodestone/hub.env)
port=${port:-8080}
echo "==> waiting for the hub on 127.0.0.1:$port"
i=0
while [ "$i" -lt 60 ]; do
    if curl -fsS "http://127.0.0.1:$port/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        echo "deployed $TAG"
        exit 0
    fi
    i=$((i + 1))
    sleep 2
done

echo "the hub did not come up -- journalctl -u lodestone-hub -n 50" >&2
exit 1

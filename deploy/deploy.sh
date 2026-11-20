#!/bin/sh
# Ships a release onto this box. Run as root:
#
#   sudo deploy/deploy.sh                       # the newest release
#   sudo deploy/deploy.sh v0.2.0                # a specific tag; going back to an older one this way
#                                               # keeps the database as it is, so only for releases
#                                               # that added no migration
#   sudo deploy/deploy.sh --rollback            # undo the last deploy: restore the dump it took, then
#                                               # put back the tag it replaced
#   sudo deploy/deploy.sh --rollback v0.2.0 /var/backups/lodestone/wtc_lodestone-....sql.gz
#
# Every deploy and rollback is a line in /opt/lodestone/deploys.log (tag before, tag after, the dump
# taken first), which is where --rollback's defaults come from. First-time setup and the rollback
# runbook are deploy/README.md -- this script only swaps artifacts, restores and restarts.
set -eu

REPO=Falphir/WTC_Lodestone
ROOT=/opt/lodestone
DB=wtc_lodestone
BACKUPS=/var/backups/lodestone
LOG=$ROOT/deploys.log

[ "$(id -u)" = 0 ] || { echo "run me as root" >&2; exit 1; }

# "field to" on a log line → its to=... value
field() { sed -n "s/.* $1=\([^ ]*\).*/\1/p"; }

# The tag "latest" stands for right now, from the redirect GitHub answers it with, so the log names a
# real tag and the download can't change under us between here and the swap.
resolve_tag() {
    if [ "$1" != latest ]; then echo "$1"; return; fi
    curl -fsSI "https://github.com/$REPO/releases/latest" | tr -d '\r' \
        | sed -n 's#^[Ll]ocation: .*/tag/\([^[:space:]]*\).*#\1#p'
}

download() {
    base="https://github.com/$REPO/releases/download/$1"
    echo "==> downloading $1"
    curl -fL --progress-bar -o "$tmp/hub.jar" "$base/wtc_lodestone-hub.jar"
    curl -fL --progress-bar -o "$tmp/bot.tar.gz" "$base/wtc_lodestone-bot.tar.gz"
    # The mod jar is not touched here. It only takes effect when that game server restarts, so grab
    # it from the release page when you mean to do that -- see deploy/README.md.
}

# Runs the same oneshot the nightly timer does (it returns once the dump is written) and prints the
# file it made.
backup() {
    echo "==> backing up the database" >&2
    systemctl start lodestone-backup.service
    # the names are timestamps, so the glob's last match is the newest
    for f in "$BACKUPS"/wtc_lodestone-*.sql.gz; do newest=$f; done
    echo "$newest"
}

swap_and_start() {
    install -o lodestone -g lodestone -m 644 "$tmp/hub.jar" "$ROOT/wtc_lodestone-hub.jar"

    tar xzf "$tmp/bot.tar.gz" -C "$tmp"
    rm -rf "$ROOT/bot"
    mv "$tmp/discord" "$ROOT/bot"
    chown -R lodestone:lodestone "$ROOT/bot"
    echo "==> installing bot dependencies"
    runuser -u lodestone -- sh -c "cd $ROOT/bot && npm ci --omit=dev"

    echo "==> starting services"
    systemctl start lodestone-hub lodestone-bot
}

# The hub's own answer to "am I actually up", including its database connection.
wait_for_hub() {
    port=$(sed -n 's/^SERVER_PORT=//p' /etc/lodestone/hub.env)
    port=${port:-8080}
    echo "==> waiting for the hub on 127.0.0.1:$port"
    i=0
    while [ "$i" -lt 60 ]; do
        if curl -fsS "http://127.0.0.1:$port/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        i=$((i + 1))
        sleep 2
    done
    echo "the hub did not come up -- journalctl -u lodestone-hub -n 50" >&2
    return 1
}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

last=$(tail -n 1 "$LOG" 2>/dev/null || true)

if [ "${1:-}" = --rollback ]; then
    # Defaults: undo the last deploy. A rollback after a rollback has nothing sensible to default to.
    case "$last" in
        *" deploy "*) ;;
        *) [ $# -ge 3 ] || { echo "the last line of $LOG isn't a deploy to undo: give the tag and the dump" >&2; exit 1; } ;;
    esac
    tag=${2:-$(echo "$last" | field from)}
    dump=${3:-$(echo "$last" | field dump)}
    [ -n "$tag" ] && [ "$tag" != unknown ] || { echo "don't know which tag came before: give it, e.g. --rollback v0.2.0 DUMP" >&2; exit 1; }
    [ -f "$dump" ] || { echo "no dump at '$dump' (dumps are kept 14 days): give one from $BACKUPS" >&2; exit 1; }

    # Everything that can fail without harm goes first: a bad dump or a missing release stops here,
    # with the hub still running.
    echo "==> checking $dump"
    gzip -t "$dump"
    download "$tag"
    # So the rollback can be undone too: this is the state being left.
    leaving=$(backup)

    echo "==> stopping services"
    systemctl stop lodestone-bot lodestone-hub

    # Dropped and recreated rather than restored over: the dump only drops the tables it has, so a
    # table the newer release's migration created would survive while the restored Flyway history
    # says it was never made -- and deploying that release again would fail on it. The lodestone
    # user's grants are on the name, not the database, so they survive the drop.
    echo "==> restoring $dump"
    trap 'rm -rf "$tmp"; echo "restore failed with the hub stopped -- the state before it is $leaving" >&2' EXIT
    mariadb -e "DROP DATABASE IF EXISTS $DB; CREATE DATABASE $DB CHARACTER SET utf8mb4;"
    gunzip < "$dump" | mariadb "$DB"
    trap 'rm -rf "$tmp"' EXIT

    swap_and_start
    echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) rollback to=$tag dump=$dump leaving=$leaving" >> "$LOG"
    wait_for_hub
    echo "rolled back to $tag; the state before is in $leaving"
    exit 0
fi

tag=$(resolve_tag "${1:-latest}")
[ -n "$tag" ] || { echo "couldn't tell which tag the latest release is" >&2; exit 1; }
from=$(echo "$last" | field to)

download "$tag"
# The hub runs Flyway on boot and there are no down-migrations, so this dump is the only way back.
dump=$(backup)

echo "==> stopping services"
systemctl stop lodestone-bot lodestone-hub
swap_and_start
# Logged before the health check: a release that migrated and then failed to come up is exactly the
# one --rollback has to know about.
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) deploy from=${from:-unknown} to=$tag dump=$dump" >> "$LOG"
wait_for_hub
echo "deployed $tag"

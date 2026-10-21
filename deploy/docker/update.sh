#!/bin/sh
# Deploys onto this Docker host. Run as root from anywhere:
#
#   sudo sh deploy/docker/update.sh            # latest main
#   sudo sh deploy/docker/update.sh v0.2.0     # a specific tag (also how you roll back the code)
#
# First-time setup is README.md next to this -- this script only updates, builds and restarts.
#
# Everything is inside main() because `git checkout` below can rewrite this very file, and the shell
# reads a script as it goes: a function is parsed in full before any of it runs.
set -eu

main() {
cd "$(dirname "$0")"

ref=${1:-origin/main}

[ "$(id -u)" = 0 ] || { echo "run me as root" >&2; exit 1; }
[ -f .env ] || { echo "no .env here -- cp .env.example .env and fill it in first" >&2; exit 1; }

echo "==> fetching $ref"
git fetch --quiet --tags origin
git checkout --quiet "$ref"

# The hub runs Flyway on boot and there are no down-migrations, so this dump is the only way back.
# Nothing to dump on the very first deploy, when the database isn't running yet.
if [ -n "$(docker compose ps -q mariadb)" ]; then
    echo "==> backing up the database first"
    sh ./backup.sh
fi

echo "==> building"
docker compose build

echo "==> starting"
docker compose up -d

# The hub's own answer to "am I actually up", including its database connection.
echo "==> waiting for the hub on 127.0.0.1:8081"
i=0
while [ "$i" -lt 90 ]; do
    if curl -fsS http://127.0.0.1:8081/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        echo "deployed $ref"
        exit 0
    fi
    i=$((i + 1))
    sleep 2
done

echo "the hub did not come up -- docker compose logs hub --tail 50" >&2
exit 1
}

main "$@"

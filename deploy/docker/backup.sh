#!/bin/sh
# sudo sh deploy/docker/backup.sh
#
# Dumps the database to /var/backups/lodestone and keeps two weeks. Run as root, nightly from cron
# (see README.md) and by update.sh before every deploy -- there are no down-migrations, so a dump
# is the only way back from a release that migrates.
set -eu
cd "$(dirname "$0")"

dir=/var/backups/lodestone
install -d -m 750 "$dir"
f="$dir/wtc_lodestone-$(date +%Y%m%d-%H%M%S).sql"

# Credentials are read inside the container from its own MARIADB_* env, so they never touch `ps`.
# Dump first, gzip second, not `dump | gzip`: a pipe takes gzip's exit status, so a failed dump
# would still "succeed" with an empty file.
if docker compose exec -T mariadb sh -c \
    'MYSQL_PWD="$MARIADB_PASSWORD" mariadb-dump --single-transaction -u"$MARIADB_USER" "$MARIADB_DATABASE"' > "$f"; then
    gzip "$f"
    echo "backed up to $f.gz"
else
    rm -f "$f"
    echo "database dump failed" >&2
    exit 1
fi

find "$dir" -name '*.sql.gz' -mtime +14 -delete

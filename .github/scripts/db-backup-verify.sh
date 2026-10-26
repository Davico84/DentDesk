#!/usr/bin/env bash
# Restaura la copia en una PostgreSQL vacía y comprueba, sin imprimir datos, que está completa
# (add-database-backups, design D1). Fail-closed: si no puede determinar lo esperado, falla.
# Lo usa el workflow db-backup.yml y se puede correr en local contra una PostgreSQL de Docker.
#
# Entorno:
#   PG_IMAGE        imagen de PostgreSQL con pg_restore/psql (la misma versión mayor que la base)
#   BACKUP_DIR      carpeta con db.dump; acá se escriben los archivos de trabajo
#   RESTORE_URL     URL libpq de la base vacía donde restaurar
#   MIGRATIONS_DIR  directorio de migraciones Flyway del repositorio (V<n>__*.sql)
#   PG_NETWORK      red de Docker para los contenedores (por defecto `host`, como en el runner)
#   BACKUP_MOUNT    BACKUP_DIR como lo ve Docker (por defecto BACKUP_DIR; en Windows, la ruta de Windows)
set -euo pipefail

: "${PG_IMAGE:?falta PG_IMAGE}" "${BACKUP_DIR:?falta BACKUP_DIR}" "${RESTORE_URL:?falta RESTORE_URL}"
: "${MIGRATIONS_DIR:?falta MIGRATIONS_DIR}"
PG_NETWORK="${PG_NETWORK:-host}"
BACKUP_MOUNT="${BACKUP_MOUNT:-$BACKUP_DIR}"

pg() { docker run --rm --network "$PG_NETWORK" --user "$(id -u):$(id -g)" -v "$BACKUP_MOUNT:/backup" "$PG_IMAGE" "$@"; }
fail() { echo "::error::$1"; exit 1; }

test -s "$BACKUP_DIR/db.dump" || fail "No hay volcado para verificar"

# La migración esperada se calcula antes de tocar la base: sin referencia no hay nada que comparar.
test -d "$MIGRATIONS_DIR" || fail "No existe $MIGRATIONS_DIR"
expected=$(find "$MIGRATIONS_DIR" -maxdepth 1 -type f -printf '%f\n' | sed -nE 's/^V([0-9]+)__.*\.sql$/\1/p' | sort -n | tail -n 1)
[ -n "$expected" ] || fail "No hay migraciones V<n>__*.sql en $MIGRATIONS_DIR"

for _ in $(seq 30); do pg pg_isready -q -d "$RESTORE_URL" && break; sleep 2; done
pg pg_isready -q -d "$RESTORE_URL" || fail "La base de restauración no responde"

# Sin dueños ni permisos: la base de restauración no tiene los roles de producción.
pg pg_restore --no-owner --no-acl --exit-on-error --dbname="$RESTORE_URL" /backup/db.dump \
  || fail "La copia no se restauró"

# Migraciones: la mayor versión aplicada con éxito, comparada como número (V29 > V10 > V9).
has_history=$(pg psql -X -At -v ON_ERROR_STOP=1 -d "$RESTORE_URL" -c \
  "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL")
[ "$has_history" = "t" ] || fail "La copia no tiene tabla de migraciones"
applied=$(pg psql -X -At -v ON_ERROR_STOP=1 -d "$RESTORE_URL" -c \
  "SELECT max(version::numeric) FROM flyway_schema_history WHERE success AND version IS NOT NULL")
[ -n "$applied" ] || fail "La copia no tiene migraciones aplicadas"
[ "$applied" = "$expected" ] || fail "La copia está en V$applied y el repositorio en V$expected"

# Tablas: cada TABLE del índice del volcado existe en la base restaurada (nombre exacto).
pg pg_restore --list /backup/db.dump | awk '$4 == "TABLE" && $5 != "DATA" { print $5 "." $6 }' | sort -u \
  > "$BACKUP_DIR/expected.txt"
[ -s "$BACKUP_DIR/expected.txt" ] || fail "La copia no incluye tablas"
pg psql -X -At -v ON_ERROR_STOP=1 -d "$RESTORE_URL" -c \
  "SELECT n.nspname || '.' || c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE c.relkind IN ('r', 'p') AND n.nspname NOT IN ('pg_catalog', 'information_schema')" \
  | sort -u > "$BACKUP_DIR/restored.txt"
missing=$(comm -23 "$BACKUP_DIR/expected.txt" "$BACKUP_DIR/restored.txt")
[ -z "$missing" ] || fail "Tablas sin restaurar: $(tr '\n' ' ' <<< "$missing")"

echo "Restauración ok: V$expected, $(wc -l < "$BACKUP_DIR/expected.txt") tablas."

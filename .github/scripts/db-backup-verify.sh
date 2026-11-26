#!/usr/bin/env bash
# Restaura la copia en una PostgreSQL vacía y comprueba, sin imprimir datos, que está completa
# (add-database-backups, design D1). Fail-closed: si no puede determinar lo esperado, falla.
# Lo usa el workflow db-backup.yml y se puede correr en local contra una PostgreSQL de Docker.
#
# Nada de lo que imprime viene de PostgreSQL: los errores de pg_restore/psql pueden traer host,
# usuario o valores de filas (p. ej. "Key (x)=(…)"), así que su salida de error va a un archivo que
# no se muestra y el log solo dice qué paso falló.
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
ERR="$BACKUP_DIR/verify.err"

fail() { echo "::error::$1"; exit 1; }
# Corre una herramienta de PostgreSQL; su stderr nunca llega al log.
pg() { docker run --rm --network "$PG_NETWORK" --user "$(id -u):$(id -g)" -v "$BACKUP_MOUNT:/backup" "$PG_IMAGE" "$@" 2>> "$ERR"; }
sql() { pg psql -X -At -v ON_ERROR_STOP=1 -d "$RESTORE_URL" -c "$1"; }

test -s "$BACKUP_DIR/db.dump" || fail "No hay volcado para verificar"
: > "$ERR"

# La migración esperada se calcula antes de tocar la base: sin referencia no hay nada que comparar.
test -d "$MIGRATIONS_DIR" || fail "No existe el directorio de migraciones"
expected=$(find "$MIGRATIONS_DIR" -maxdepth 1 -type f -printf '%f\n' | sed -nE 's/^V([0-9]+)__.*\.sql$/\1/p' | sort -n | tail -n 1)
[ -n "$expected" ] || fail "No hay migraciones V<n>__*.sql en el repositorio"

for _ in $(seq 30); do pg pg_isready -q -d "$RESTORE_URL" && break; sleep 2; done
pg pg_isready -q -d "$RESTORE_URL" || fail "La base de restauración no responde"

# Sin dueños ni permisos: la base de restauración no tiene los roles de producción, y el volcado no
# puede nombrarlos fuera de dueños y permisos (V33 dejó de hacerlo en las políticas): si alguna
# migración vuelve a nombrar un rol, esta restauración falla y lo avisa.
if ! pg pg_restore --no-owner --no-acl --exit-on-error --dbname="$RESTORE_URL" /backup/db.dump; then
  # Solo el objeto que falló, de la línea "from TOC entry <id>; <oid> <oid> <TIPO> <esquema> <nombre…>":
  # tipo y nombres, nunca el mensaje ni el comando (pueden traer valores de filas). Se filtra a un
  # juego de caracteres seguro por si acaso.
  failed=$(sed -nE 's/^pg_restore: from TOC entry [0-9]+; [0-9]+ [0-9]+ //p' "$ERR" | head -n 1 | tr -cd 'A-Za-z0-9_. ')
  # Categoría del error de pg_restore (lo que va entre "error:" y los dos puntos siguientes) y la
  # línea ERROR del servidor con todo texto entre comillas reemplazado por "…", salvo que parezca un
  # identificador con "_" (minúsculas, dígitos, "_" y ".": un valor de una sola palabra también se tapa): se ven nombres de objetos, nunca valores de filas.
  kind=$(sed -nE 's/^pg_restore: error: ([^:]*).*/\1/p' "$ERR" | head -n 1 | tr -cd 'A-Za-z0-9_. ')
  server=$(grep -m 1 -oE 'ERROR: .*' "$ERR" | sed -E -e 's/"[^"]*[^a-z0-9_."][^"]*"/"…"/g' -e 's/"[a-z0-9.]*"/"…"/g' | tr -cd 'A-Za-z0-9_. :"…()' | cut -c1-160)
  fail "La copia no se restauró (error: ${kind:-desconocido}; objeto: ${failed:-desconocido}; ${server:-sin línea ERROR}; detalle oculto)"
fi

# Migraciones: la mayor versión aplicada con éxito, comparada como número dentro de PostgreSQL
# (V29 > V10 > V9; 29 = 29.0). `expected` son solo dígitos: se puede escribir en la consulta.
has_history=$(sql "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL") \
  || fail "No se pudo consultar la copia restaurada"
[ "$has_history" = "t" ] || fail "La copia no tiene tabla de migraciones"
version_check=$(sql "SELECT CASE WHEN max(version::numeric) IS NULL THEN 'vacia'
                                 WHEN max(version::numeric) = $expected THEN 'ok'
                                 ELSE trim_scale(max(version::numeric))::text END
                     FROM flyway_schema_history WHERE success AND version IS NOT NULL") \
  || fail "No se pudo leer la tabla de migraciones"
[ "$version_check" != "vacia" ] || fail "La copia no tiene migraciones aplicadas"
[ "$version_check" = "ok" ] || fail "La copia está en V$version_check y el repositorio en V$expected"

# Tablas: cada TABLE del índice del volcado existe en la base restaurada (nombre exacto). El índice
# es "id; oid oid TABLE esquema nombre dueño", sin comillas (las filas "TABLE DATA" tienen un campo
# más y no cuentan): un nombre con espacios cambia la cantidad de campos y se rechaza en vez de leerlo
# mal (los identificadores del proyecto son snake_case).
pg pg_restore --list /backup/db.dump > "$BACKUP_DIR/list.txt" || fail "No se pudo leer el índice de la copia"
awk '$4 == "TABLE" && $5 != "DATA" { if (NF != 7) bad = 1; else print $5 "." $6 } END { exit bad }' "$BACKUP_DIR/list.txt" \
  | sort -u > "$BACKUP_DIR/expected.txt" \
  || fail "La copia tiene tablas con nombres no soportados (espacios o comillas)"
[ -s "$BACKUP_DIR/expected.txt" ] || fail "La copia no incluye tablas"
sql "SELECT n.nspname || '.' || c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relkind IN ('r', 'p') AND n.nspname NOT IN ('pg_catalog', 'information_schema')" \
  | sort -u > "$BACKUP_DIR/restored.txt" || fail "No se pudo listar las tablas restauradas"
missing=$(comm -23 "$BACKUP_DIR/expected.txt" "$BACKUP_DIR/restored.txt" | wc -l)
[ "$missing" -eq 0 ] || fail "Faltan $missing tabla(s) de la copia en la base restaurada"

echo "Restauración ok: V$expected, $(wc -l < "$BACKUP_DIR/expected.txt") tablas."

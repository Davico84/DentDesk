#!/usr/bin/env bash
# Comprueba que cada check que exige el Ruleset de main (.github/rulesets/main.json) lo publica
# exactamente un job, y que ese job corre en todo PR hacia main y dev (update-ci-required-checks, design D5).
#
# Por cada nombre exigido:
#   - En TODOS los workflows, un solo job con ese nombre efectivo (`name`, o su id si no lo tiene). El
#     Ruleset no distingue de qué workflow viene un check: otro job con el mismo nombre (aunque sea
#     manual o programado) podría satisfacerlo sobre el mismo commit sin que corriera el real.
#   - Ese job corre en todo PR hacia main y dev: su workflow dispara con `pull_request` sin `paths`,
#     `paths-ignore` ni `branches-ignore`, con `branches` (si está) que incluye main y dev, y `types` (si está)
#     que incluye opened, synchronize y reopened; el job tiene `runs-on` (uno que llama a un workflow
#     reutilizable publica otros nombres) y no tiene `if:`, que podría saltarlo.
#
# Nombres dinámicos: `${{ matrix.<clave> }}` se expande con los valores literales de la matriz
# (`matrix.<clave>` o `matrix.include[].<clave>`). Cualquier otro nombre con `${{` falla: no se puede
# demostrar estáticamente que no publica un nombre exigido.
#
# Uso: check-ci-check-names.sh [directorio de workflows] [archivo del Ruleset]
#      (por defecto .github/workflows y .github/rulesets/main.json: los nombres exigidos salen de ahí)
# Entorno: YQ  binario de mikefarah/yq v4 (por defecto `yq`; en CI, uno con versión y SHA-256 fijos)
set -euo pipefail

dir="${1:-.github/workflows}"
ruleset="${2:-.github/rulesets/main.json}"
yq="${YQ:-yq}"

q() { "$yq" eval "$1" "$2" | tr -d '\r'; }

# Una sola fuente: los checks que exige el Ruleset versionado.
test -s "$ruleset" || { echo "::error::No existe el Ruleset $ruleset"; exit 1; }
mapfile -t required < <("$yq" -p json -o yaml eval '.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[].context' "$ruleset" | tr -d '\r')
[ "${#required[@]}" -gt 0 ] || { echo "::error::El Ruleset $ruleset no exige ningún check"; exit 1; }

shopt -s nullglob
files=("$dir"/*.yml "$dir"/*.yaml)
[ "${#files[@]}" -gt 0 ] || { echo "::error::No hay workflows en $dir"; exit 1; }

# ¿El workflow dispara con pull_request sobre todo PR hacia main y dev? Consultas simples, una por condición
# (yq evalúa todas las ramas de una expresión, así que no se combinan en una sola).
pr_reaches_main() {
  local f="$1" t pt
  t=$(q '.on | tag' "$f")
  case "$t" in
    '!!str') [ "$(q '.on' "$f")" = pull_request ]; return ;;
    '!!seq') q '.on[]' "$f" | grep -qx pull_request; return ;;
    '!!map') ;;
    *) return 1 ;;
  esac
  [ "$(q '.on | has("pull_request")' "$f")" = true ] || return 1
  pt=$(q '.on.pull_request | tag' "$f")
  [ "$pt" = '!!null' ] && return 0
  [ "$pt" = '!!map' ] || return 1
  [ "$(q '.on.pull_request | (has("paths") or has("paths-ignore") or has("branches-ignore"))' "$f")" = false ] || return 1
  if [ "$(q '.on.pull_request | has("branches")' "$f")" = true ]; then
    local branches
    branches=$(q '.on.pull_request.branches[]' "$f")
    for want in main dev; do grep -qx "$want" <<< "$branches" || return 1; done
  fi
  if [ "$(q '.on.pull_request | has("types")' "$f")" = true ]; then
    local types
    types=$(q '.on.pull_request.types[]' "$f")
    for want in opened synchronize reopened; do grep -qx "$want" <<< "$types" || return 1; done
  fi
  return 0
}

# Nombres efectivos de los jobs del archivo (con `pr` solo los que corren en todo PR: runs-on y sin if).
job_names() {
  local f="$1" filter="$2" id name key
  while IFS=$'\t' read -r id name; do
    [ -n "$id" ] || continue
    if [[ "$name" == *'${{'* ]]; then
      if [[ "$name" =~ ^\$\{\{[[:space:]]*matrix\.([A-Za-z0-9_-]+)[[:space:]]*\}\}$ ]]; then
        key="${BASH_REMATCH[1]}"
        q ".jobs[\"$id\"].strategy.matrix | ((.\"$key\" // [])[] , ((.include // [])[] | .\"$key\" | select(. != null)))" "$f"
      else
        echo "::error::$f: el job '$id' tiene un nombre dinámico no verificable ($name)" >&2
        return 1
      fi
    else
      echo "$name"
    fi
  done < <(q ".jobs // {} | to_entries | .[] | $filter | .key + \"\t\" + (.value.name // .key)" "$f")
}

all_names=""
pr_names=""
for f in "${files[@]}"; do
  all_names+=$(job_names "$f" '.')$'\n'
  if pr_reaches_main "$f"; then
    pr_names+=$(job_names "$f" 'select(.value["runs-on"] != null and .value.if == null)')$'\n'
  fi
done

bad=0
for want in "${required[@]}"; do
  total=$(grep -cxF -- "$want" <<< "$all_names" || true)
  in_pr=$(grep -cxF -- "$want" <<< "$pr_names" || true)
  if [ "$total" -gt 1 ]; then
    echo "::error::El check exigido '$want' lo publican $total jobs: el Ruleset no distingue cuál"
    bad=1
  elif [ "$in_pr" -ne 1 ]; then
    echo "::error::Ningún job que corra en todo PR hacia main y dev publica el check exigido '$want' (falta, tiene if:, usa un workflow reutilizable o su pull_request no alcanza a main y dev): el merge quedaría pendiente"
    bad=1
  fi
done
[ "$bad" -eq 0 ] || exit 1
echo "Checks exigidos: cada uno lo publica un solo job, que corre en todo PR hacia main y dev (${#required[@]})."

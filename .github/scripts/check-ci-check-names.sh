#!/usr/bin/env bash
# Comprueba que cada check que exige el Ruleset de main (.github/rulesets/main.json) lo publica
# exactamente un job, y que ese job corre en los PR (update-ci-required-checks, design D5).
#
# Dos condiciones por cada nombre exigido:
#   - En TODOS los workflows, un solo job con ese nombre efectivo (`name`, o su id si no lo tiene). El
#     Ruleset no distingue de qué workflow viene un check: otro job con el mismo nombre (aunque sea
#     manual o programado) podría satisfacerlo sobre el mismo commit sin que corriera el real.
#   - Ese job está en un workflow que dispara con `pull_request`, corre en un runner (`runs-on`; un job
#     que llama a un workflow reutilizable publica otros nombres) y no tiene `if:`, que podría saltarlo
#     y dejar el merge pendiente.
#
# Uso: check-ci-check-names.sh [directorio de workflows] [archivo del Ruleset]
#      (por defecto .github/workflows y .github/rulesets/main.json: los nombres exigidos salen de ahí)
# Entorno: YQ  binario de mikefarah/yq v4 (por defecto `yq`; en CI, uno con versión y SHA-256 fijos)
set -euo pipefail

dir="${1:-.github/workflows}"
ruleset="${2:-.github/rulesets/main.json}"
yq="${YQ:-yq}"

# Una sola fuente: los checks que exige el Ruleset versionado.
test -s "$ruleset" || { echo "::error::No existe el Ruleset $ruleset"; exit 1; }
mapfile -t required < <("$yq" -p json -o yaml eval '.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[].context' "$ruleset" | tr -d '\r')
[ "${#required[@]}" -gt 0 ] || { echo "::error::El Ruleset $ruleset no exige ningún check"; exit 1; }

shopt -s nullglob
files=("$dir"/*.yml "$dir"/*.yaml)
[ "${#files[@]}" -gt 0 ] || { echo "::error::No hay workflows en $dir"; exit 1; }

# Disparadores del workflow, sea `on:` un texto, una lista o un mapa.
triggers='.on as $o | [($o | select(tag == "!!str")), ($o | select(tag == "!!seq") | .[]), ($o | select(tag == "!!map") | keys | .[])] | .[]'
all_names=""
pr_names=""
for f in "${files[@]}"; do
  all_names+=$("$yq" eval '.jobs // {} | to_entries | .[] | (.value.name // .key)' "$f" | tr -d '\r')$'\n'
  if "$yq" eval "$triggers" "$f" | tr -d '\r' | grep -qx 'pull_request'; then
    pr_names+=$("$yq" eval '.jobs // {} | to_entries | .[] | select(.value["runs-on"] != null and .value.if == null) | (.value.name // .key)' "$f" | tr -d '\r')$'\n'
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
    echo "::error::Ningún job que corra en los PR publica el check exigido '$want' (falta, tiene if:, usa un workflow reutilizable o su workflow no dispara con pull_request): el merge a main quedaría pendiente"
    bad=1
  fi
done
[ "$bad" -eq 0 ] || exit 1
echo "Checks exigidos: cada uno lo publica un solo job, que corre en los PR (${#required[@]})."

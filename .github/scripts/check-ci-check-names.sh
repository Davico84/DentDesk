#!/usr/bin/env bash
# Comprueba que cada check que exige el Ruleset de main (.github/rulesets/main.json) lo publica
# exactamente un job de los workflows (update-ci-required-checks, design D5). El Ruleset no distingue de
# qué workflow viene un check: un segundo job con el mismo nombre lo satisfaría aunque el original no
# corriera, y un job renombrado dejaría el merge pendiente para siempre.
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

# Nombre efectivo del check de cada job: su `name`, o su id si no lo tiene.
names=$("$yq" eval '.jobs // {} | to_entries | .[] | (.value.name // .key)' "${files[@]}" | tr -d '\r')

bad=0
for want in "${required[@]}"; do
  n=$(grep -cxF -- "$want" <<< "$names" || true)
  if [ "$n" -eq 0 ]; then
    echo "::error::Ningún job publica el check exigido '$want' (renombrado o quitado: el merge a main quedaría pendiente)"
    bad=1
  elif [ "$n" -gt 1 ]; then
    echo "::error::El check exigido '$want' lo publican $n jobs: el Ruleset no distingue cuál"
    bad=1
  fi
done
[ "$bad" -eq 0 ] || exit 1
echo "Checks exigidos: cada uno lo publica un solo job (${#required[@]})."

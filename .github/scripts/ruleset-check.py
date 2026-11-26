#!/usr/bin/env python3
"""Compara el Ruleset aplicado en GitHub con el archivo versionado (update-ci-required-checks, design D3).

Falla cerrado ante cualquier duda: sin Ruleset con ese nombre o con más de uno, sin `bypass_actors` en la
respuesta (GitHub lo omite si quien consulta no administra el repo) o con cualquier diferencia.

Se comparan name, target, enforcement, conditions, rules (con todos sus parameters) y bypass_actors. Lo
que agrega GitHub (id, source, fechas, _links…) se descarta. El orden de los arreglos no cuenta: cada
arreglo se ordena por el JSON canónico de sus elementos, así que dos reglas del mismo tipo tampoco pueden
intercambiarse sin detectarlo. Si GitHub devuelve parámetros por defecto que el archivo no tiene, aparecen
como diferencia: se agregan al archivo, para que diga exactamente lo aplicado.

Uso:
  python .github/scripts/ruleset-check.py [archivo]          # contra GitHub, con `gh` autenticado
  python .github/scripts/ruleset-check.py --files A B        # sin GitHub: compara dos JSON (pruebas)
"""
import json
import subprocess
import sys

KEEP = ("name", "target", "enforcement", "conditions", "rules", "bypass_actors")


def fail(msg):
    print(f"::error::{msg}")
    sys.exit(1)


def canonical(value):
    """Claves ordenadas y cada arreglo ordenado por el JSON canónico de sus elementos."""
    if isinstance(value, dict):
        return {k: canonical(v) for k, v in sorted(value.items())}
    if isinstance(value, list):
        items = [canonical(v) for v in value]
        return sorted(items, key=lambda v: json.dumps(v, sort_keys=True, ensure_ascii=False))
    return value


def normalize(ruleset, origin):
    if "bypass_actors" not in ruleset:
        fail(f"{origin}: falta bypass_actors (¿sin permiso de administración?); no se asume vacío")
    missing = [k for k in KEEP if k not in ruleset]
    if missing:
        fail(f"{origin}: faltan {', '.join(missing)}")
    return canonical({k: ruleset[k] for k in KEEP})


def gh_api(*args):
    out = subprocess.run(["gh", "api", *args], capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        fail(f"gh api {' '.join(args)} falló: {out.stderr.strip()}")
    return out.stdout


def remote(name):
    # Todas las páginas como un arreglo de páginas; el nombre se filtra acá, como dato, nunca dentro de
    # un programa jq.
    pages = json.loads(gh_api("--paginate", "--slurp", "repos/{owner}/{repo}/rulesets"))
    ids = [r["id"] for page in pages for r in page if r.get("name") == name]
    if len(ids) != 1:
        fail(f"se esperaba un Ruleset llamado '{name}' y hay {len(ids)}")
    return json.loads(gh_api(f"repos/{{owner}}/{{repo}}/rulesets/{ids[0]}"))


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def compare(expected, actual, exp_origin, act_origin):
    a = json.dumps(normalize(expected, exp_origin), indent=2, sort_keys=True, ensure_ascii=False).splitlines()
    b = json.dumps(normalize(actual, act_origin), indent=2, sort_keys=True, ensure_ascii=False).splitlines()
    if a == b:
        print(f"Ruleset '{expected.get('name')}': {act_origin} coincide con {exp_origin}.")
        return
    import difflib
    print("\n".join(difflib.unified_diff(a, b, exp_origin, act_origin, lineterm="")))
    fail(f"{act_origin} difiere de {exp_origin}")


def main(argv):
    if len(argv) == 3 and argv[0] == "--files":
        compare(load(argv[1]), load(argv[2]), argv[1], argv[2])
        return
    path = argv[0] if argv else ".github/rulesets/main.json"
    expected = load(path)
    compare(expected, remote(expected["name"]), path, "GitHub")


if __name__ == "__main__":
    main(sys.argv[1:])

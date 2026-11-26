## ADDED Requirements

### Requirement: CI completa en cada pull request
Todo pull request hacia `main` o `dev` SHALL ejecutar los seis checks de CI (backend, los cuatro de frontend y OpenSpec), cualesquiera sean los archivos que cambia. Los pushes a `main` y `dev` MAY seguir filtrando por área: un push que no toca el área de un workflow no lo ejecuta.

#### Scenario: PR solo de documentación
- **WHEN** se abre un PR hacia `dev` que solo cambia archivos de `docs/`
- **THEN** se ejecutan los seis checks

#### Scenario: PR solo de backend
- **WHEN** se abre un PR hacia `dev` que solo cambia `modules/backend/`
- **THEN** se ejecutan también los cuatro checks de frontend y el de OpenSpec

#### Scenario: Release hacia main
- **WHEN** se abre el PR `dev` → `main`
- **THEN** se ejecutan los seis checks, aunque el release no toque alguna de las áreas

#### Scenario: Push que no toca el área
- **WHEN** se mergea a `dev` un cambio que solo toca `docs/`
- **THEN** el push resultante no ejecuta los workflows de backend, frontend ni OpenSpec

### Requirement: Nombres únicos de los checks de CI
Cada uno de los seis checks de CI SHALL publicarse con un nombre único en el repositorio que identifique su área: `backend-verify`, `frontend-lint`, `frontend-codegen-drift`, `frontend-test`, `frontend-e2e-smoke` y `openspec-validate`. Ningún otro job de un workflow del repositorio MUST publicar un check con alguno de esos nombres.

#### Scenario: Nombres publicados
- **WHEN** termina la CI de un PR
- **THEN** los check runs del commit se llaman exactamente como los seis nombres, publicados por la app GitHub Actions

#### Scenario: Nombre repetido en otro workflow
- **WHEN** un PR agrega un workflow con un job cuyo check se llamaría `frontend-test`
- **THEN** el check `openspec-validate` de ese PR falla nombrando el check repetido

#### Scenario: Nombre exigido que desaparece
- **WHEN** un PR renombra o quita uno de los seis jobs
- **THEN** el check `openspec-validate` falla nombrando el check que falta

### Requirement: Protección de main con checks obligatorios
La rama `main` SHALL aceptar cambios solo por pull request, con los seis checks en verde publicados por la app GitHub Actions; sin aprobaciones exigidas. `main` MUST rechazar el push directo, el force push y el borrado. No SHALL exigirse que la rama del PR esté al día con `main`.

#### Scenario: Release con los checks en verde
- **WHEN** el PR `dev` → `main` tiene los seis checks en verde
- **THEN** se puede mergear sin aprobaciones y sin traer `main` a `dev`

#### Scenario: Un check en rojo
- **WHEN** alguno de los seis checks del PR hacia `main` falla
- **THEN** GitHub no permite el merge

#### Scenario: Un check que no llega
- **WHEN** uno de los seis checks no se publicó en el PR hacia `main`
- **THEN** el merge queda bloqueado como pendiente

#### Scenario: Check con el mismo nombre de otra app
- **WHEN** una app distinta de GitHub Actions publica un check `backend-verify` en verde
- **THEN** no cuenta para el Ruleset

#### Scenario: Push directo o force push
- **WHEN** alguien intenta un push directo, un force push o borrar `main`
- **THEN** GitHub lo rechaza

### Requirement: Ruleset de main versionado
La configuración del Ruleset de `main` SHALL vivir en el repositorio como un archivo versionado, y lo aplicado en GitHub MUST coincidir con ese archivo. Cambiar la protección de `main` SHALL hacerse editando ese archivo en un PR y volviéndolo a aplicar, no solo en la interfaz de GitHub.

#### Scenario: Comparar con lo aplicado
- **WHEN** se compara el Ruleset activo en GitHub con el archivo del repositorio
- **THEN** coinciden la rama objetivo, los checks con su app, las reglas y el modo de aplicación

#### Scenario: Cambio hecho solo en la interfaz
- **WHEN** alguien cambia el Ruleset en la interfaz sin tocar el archivo
- **THEN** la comparación repetible lo detecta como diferencia y termina con error

#### Scenario: Mismo contenido en otro orden
- **WHEN** GitHub devuelve las reglas o los checks en otro orden que el archivo
- **THEN** la comparación no reporta diferencia

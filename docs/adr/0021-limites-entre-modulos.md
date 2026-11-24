# ADR-0021: Límites entre módulos — declarados y vigilados, no estrictos

- **Estado:** Aceptado (decisión); se implementa en `update-module-boundaries`
- **Fecha:** 2026-11-24
- **Decisores:** David Velarde Alvarado
- **Relacionado:** `openspec/specs/project-foundation` (requirement "Estructura arquitectónica establecida": *"`modules/core` no depende de otros módulos"*) · [`docs/testing.md §5`](../testing.md) (una regla que nunca ha fallado no sabes si funciona)
- **Fuente normativa:** no aplica. Sin impacto en `regulatory-matrix.md`.

## Contexto

Se preguntó cómo se manejan los límites y la comunicación entre módulos (¿puede `patients` importar algo interno de `auth`?). La respuesta honesta, tras auditar los imports reales:

- **No había regla formal** ni en el frontend ni entre los paquetes `service.*` del backend. Sí había una promesa en la spec (`core` no depende de módulos) cumplida por costumbre.
- **Hay dependencias directas entre módulos de negocio**, por ruta profunda y sin barrels: `clinical-history → staff / dental-chart / patients / appointments`, `appointments → staff / patients`, `dental-chart → staff`, `auth → staff`, `home → patients`. Son legítimas: la credencial clínica (ADR-0016) atraviesa la historia, el odontograma, el perfil y la agenda (odontólogos agendables), y la nota de consulta propone la próxima cita.
- **Un ciclo real en el backend**: `auth ↔ staff`, causado por dos excepciones del flujo de invitación alojadas en `service.auth`.

Lo que sí estaba vigilado por la build: capas (`presentation`/`service`/`persistence`), tenancy (RLS), `@Async`/`@Scheduled`, sellado coordinado del odontograma.

## Decisión

1. **Se declaran, no se prohíben.** Un mapa único por lado lista las dependencias permitidas entre módulos (front: `eslint.config.js`) y entre dominios (back: test ArchUnit). Una arista nueva exige editar el mapa: aparece en el diff y se justifica en el PR.
2. **`core` no importa de módulos de negocio** — la promesa de la spec pasa a ser una regla de lint.
3. **Sin ciclos entre `service.<dominio>`** — regla `slices().beFreeOfCycles()`. El ciclo `auth ↔ staff` se rompe moviendo `EmailAlreadyExistsException` e `InvalidInvitationException` a `service.staff` (`InvalidInvitationException` es del flujo de invitación; `EmailAlreadyExistsException` también la lanza el registro, así que se mueve **por la dirección del grafo** —`auth → staff` ya existía—, no por pertenencia conceptual pura).
4. **Las reglas se prueban fallando**: cada una tiene un test con una violación deliberada y, en el backend, el chequeo de importador no vacío.
5. **Se documenta el estado real**: sin barrels ni API pública por módulo; `core` importa de `@/store`, `@/routes/paths` y `@/layouts/RootLayout` (no son módulos) y eso no cambia; `presentation`, `infra`, `screens`, `routes` y `store` no se vigilan.

## Consecuencias

- ✅ Un import estático nuevo entre módulos del frontend (alias `@/modules/...`) o una dependencia nueva entre paquetes `service.<dominio>` **no entra sin verse**, **dentro del alcance declarado de estas reglas**; los ciclos entre dominios `service` rompen la build. Fuera de ese alcance (imports relativos o dinámicos, `screens`, `routes`, `layouts`, `store`, `presentation`, `infra`, clases sueltas de `service`) no hay vigilancia.
- ✅ La promesa de la spec sobre `core` deja de depender de la memoria.
- ✅ Cero dependencias nuevas; el mapa es un objeto de JavaScript y un `Map` de Java.
- ⚠️ **No es desacoplamiento.** Las dependencias existentes se aceptan tal cual; `clinical-history` sigue conociendo detalles de `staff`. La regla promete *declaradas y sin ciclos*, no *bajo acoplamiento*.
- ⚠️ El mapa puede convertirse en ritual (agregar la arista para que pase). Se mitiga con la revisión del diff, no con la herramienta.
- ⚠️ ESLint no ve imports dinámicos construidos; ArchUnit no ve nombres de clase en `String`.
- ⚠️ Los tests del frontend (`*.test.*`, `*.spec.*`, carpetas `test/`) quedan exentos: arman varios módulos a propósito. Aplica también a `core`.
- ⚠️ **Lo que se vigila son dos convenciones de importación** (`@/modules/...` y paquetes `service.<dominio>`), no "los límites" en abstracto: un import relativo entre módulos escaparía hasta que la task 1.4 lo cubra.

## Alternativas descartadas

- **`eslint-plugin-boundaries`** — el mismo mapa con más sintaxis y un resolver de alias; dependencia nueva en un proyecto que ya sufrió un plugin de ESLint roto por una versión mayor.
- **`dependency-cruiser`** — herramienta y CI aparte; el frontend no tiene ciclos hoy, así que su ventaja no se usaría.
- **Barrels y API pública por módulo** — rediseño con decenas de imports a reescribir y sin dolor actual. Se puede añadir **encima** de este mapa.
- **Prohibir toda dependencia entre módulos de negocio** — falso para este dominio (la credencial clínica es transversal); mandaría a `core` código que no lo merece.
- **Paquete transversal de excepciones bajo `service`** — evitaría que `auth` conozca un detalle de `staff`, pero crea una bolsa de excepciones sin dueño por un ciclo de dos paquetes. Se reevalúa con un tercer consumidor.
- **Congelar el ciclo con `FreezingArchRule`** — dejaría la regla verde sobre el defecto que la motivó.
- **Solo documentar la convención** — es el estado de partida.

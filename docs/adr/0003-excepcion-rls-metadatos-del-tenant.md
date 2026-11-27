# ADR-0003: `OrganizationSettings` y `OrganizationBranding` van sin Row-Level Security

- **Estado:** Aceptado — válida con matiz, revalidada en [`ADR-0022`](0022-cobertura-rls-y-excepciones-por-clinica.md)
- **Fecha:** 2026-07-13
- **Decisores:** David Velarde Alvarado

## Contexto

El aislamiento multi-tenant se apoya en **Row-Level Security** de PostgreSQL, con **dos claves** según la tabla (ver `docs/backend.md §8.1` y `openspec/specs/multi-tenancy/`):

| Tabla | RLS | Aislada por |
|---|---|---|
| De negocio (`patients`, `appointments`, `treatments`, `invoices`…) | ✅ | **organización** (`organization_id`) |
| `memberships` | ✅ | **identidad** (`user_id`) |
| `users`, `refresh_tokens` | ❌ | dominio de auth: se leen **por credencial**, antes de que exista contexto de organización |
| **`organization_settings`, `organization_branding`** | ❌ | **← esta ADR** |

Al auditar la arquitectura, esas dos últimas destacan: son 1:1 con `Organization` y **no llevan RLS**, lo que a primera vista **parece un olvido** en un sistema que presume de aislamiento. No lo es: es una decisión, y conviene dejarla escrita para que nadie la "arregle" sin entender el porqué, ni la copie a ciegas en una tabla donde sí haría falta.

## Decisión

**`organization_settings` y `organization_branding` no llevan RLS.** Son **metadatos del tenant**, no datos de negocio, y su seguridad descansa en una propiedad estructural, no en el motor:

1. **Su clave primaria ES el `organization_id`** (comparten PK con `Organization` vía `@MapsId`). No existe la noción de "escanear la tabla y colarse en filas de otra organización": **siempre se accede por PK**, nunca por un filtro que se pueda olvidar. Es justo el fallo que RLS existe para atrapar (`SELECT` sin `WHERE`), y aquí **no puede ocurrir**.

2. **El `organizationId` sale SIEMPRE del contexto de sesión** (el claim `oid` del token firmado), **nunca de la petición**. El servicio hace `OrganizationContext.get()`; el cliente no puede indicar sobre qué organización opera. Esta es **la invariante de la que depende toda la decisión**.

3. **Añadir RLS aquí exigiría fijar `app.organization_id` en cada transacción** que las toque. Hoy el contexto se fija de forma **explícita** en los flujos de auth (`TenantSession.bindIdentity`); el interceptor genérico por transacción está **diferido** a la primera capacidad de negocio. Poner RLS sin ese interceptor sería una fuente segura de errores en tiempo de ejecución, sin ganar seguridad real (ver punto 1).

## Consecuencias

- ✅ Modelo más simple, sin RLS ni contexto de organización en un camino (la marca) que además se lee en **cada carga del app shell**.
- ✅ La regla general se mantiene **intacta y sin excepciones ambiguas**: *toda entidad de **negocio** lleva `organization_id` + RLS*. Estas dos no son de negocio.
- ⚠️ **La seguridad depende de una invariante de código, no del motor.** Si alguien la rompe, no hay red debajo.

> 🚨 **La invariante, explícita:** el `organizationId` con el que se leen o escriben estas tablas **SIEMPRE** viene del token firmado, **JAMÁS** de un parámetro, cuerpo o cabecera de la petición.
>
> **Esta ADR queda invalidada** —y hay que poner RLS— en cuanto ocurra **cualquiera** de estas cosas:
> - un endpoint acepte el `organizationId` **desde la petición** (p. ej. un panel de superadministrador que opere sobre otra organización);
> - alguna consulta sobre estas tablas **deje de ser por PK** (un listado, un `JOIN` que las escanee, un informe);
> - se añadan **datos sensibles** a `organization_settings` (facturación del SaaS, claves de integración, límites de plan…), momento en el que dejarían de ser "metadatos estéticos/configurativos" y pasarían a ser datos de negocio.
>
> Cuando llegue el interceptor genérico de `set_config` (diferido a la primera capacidad de negocio), **el coste de añadir RLS aquí baja mucho**: conviene reevaluarlo entonces como defensa en profundidad, aunque el punto 1 siga siendo válido.

## Alternativas descartadas

- **Poner RLS igualmente (defensa en profundidad)** — es lo más conservador y a largo plazo probablemente lo correcto. Se descarta **por ahora** porque exige el interceptor genérico de `set_config`, que está diferido: hoy introduciría fallos en runtime sin cerrar ningún hueco real (el acceso es por PK y el id viene del token). **Reevaluar cuando el interceptor exista.**
- **Tratarlas como tablas de negocio y darles su propio `id` + `organization_id`** — añadiría una columna redundante (la relación ya es 1:1 con la organización) solo para poder aplicarles la política general. Complica el modelo sin beneficio.

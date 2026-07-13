# ADR-0002: Consultas con filtros — `GET` + query params (y no el método HTTP QUERY)

- **Estado:** Aceptado
- **Fecha:** 2026-07-12
- **Decisores:** David Velarde Alvarado

## Contexto

Las capacidades de negocio que vienen (`patients`, `appointments`, `treatments`, `billing`, `dashboard`) necesitarán **listados con filtros, paginación y orden**. Conviene fijar la forma de esas consultas **antes** de construir la primera, porque una vez que `patients` establezca un patrón, el resto lo copiará.

Se evaluó el método **HTTP `QUERY`** (draft del IETF): una petición **segura e idempotente pero con cuerpo**, pensada justo para filtros que no caben cómodos en una query string. Resuelve un problema real: hoy la alternativa a `GET` con URLs kilométricas es un `POST /search`, que miente sobre la semántica (un `POST` no es seguro ni cacheable, aunque solo lea).

La restricción decisiva es que en este proyecto **el contrato OpenAPI es la fuente única de verdad** (capacidad `api-type-contracts`): el frontend **deriva** de él sus tipos y su cliente, sin escribirlos a mano, y CI vigila que no haya drift en ninguna de las dos direcciones. Cualquier decisión de API que el contrato no pueda describir **rompe esa cadena**.

## Decisión

**Las consultas con filtros se hacen con `GET` + query params.** Paginación, orden y filtros viajan en la query string.

**No se adopta el método `QUERY`.** Razones:

- **Es un borrador**, no un estándar consolidado.
- **springdoc / OpenAPI no lo modelan** como método estándar → **el contrato no podría describirlo**, que es exactamente lo que la capacidad `api-type-contracts` existe para impedir.
- **orval no generaría cliente** para él → el frontend tendría que escribir a mano la llamada y sus tipos, rompiendo el propósito de la capacidad.
- Soporte irregular en **navegadores, proxies y CDNs**.

**Válvula de escape:** si aparece un filtro genuinamente complejo que no cabe en una query string (p. ej. una búsqueda clínica con muchos criterios anidados), se usará un **`POST` a un sub-recurso explícito** (`POST /patients/search`), asumiendo conscientemente que **no será cacheable ni idempotente para los intermediarios**. Es una concesión pragmática, no el caso por defecto: primero se intenta con `GET`.

## Consecuencias

- ✅ El contrato **puede describir** todas las consultas, y el frontend **deriva** sus tipos y su cliente sin escribir nada a mano.
- ✅ Los listados son **cacheables y enlazables** (una búsqueda filtrada es una URL que se puede compartir).
- ⚠️ Filtros muy complejos pueden dar **URLs largas**. Es el precio aceptado; si molesta de verdad, existe la válvula de escape.
- 🔄 **Reversible**: si `QUERY` se estandariza y springdoc/orval lo soportan, este ADR se revisa. La decisión depende del soporte de la cadena de herramientas, no de una preferencia estética.

## Alternativas descartadas

- **Método `QUERY`** — descrito arriba: el contrato no puede describirlo hoy.
- **`POST /search` como patrón por defecto** — funciona y no tiene límite de tamaño, pero **miente sobre la semántica** (un `POST` no es seguro ni cacheable), y renuncia a URLs compartibles. Queda solo como válvula de escape.

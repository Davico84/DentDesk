## 1. El enganche automático del contexto de tenant (design.md D1)

- [ ] 1.1 **Resolver la Open Question 1**: implementar el enganche en el punto por el que pasan **todas** las transacciones — candidata preferida: **el gestor de transacciones** (garantiza que ninguna se escapa); alternativa: interceptor de Hibernate. Reutiliza el `TenantSession.bind(...)` que ya existe
- [ ] 1.2 Aplicar las **reglas del enganche**: identidad **y** organización → se fijan ambas; solo identidad → solo esa; **ninguna** → **nada** (y RLS falla cerrado al tocar una tabla de negocio, que es lo correcto)
- [ ] 1.3 **NO tocar los flujos de auth** (design.md D2): en el registro la organización se crea **en la misma transacción**, así que no hay token del que leer el contexto. `AuthService` conserva su fijación explícita — no es una grieta en la regla: **en el bootstrap no hay nada que propagar**

## 2. La verificación — es lo que hace honesto al requirement (design.md D3, D4)

> El requirement dice *"una entidad de negocio nueva queda aislada **sin código propio**"*. Se demuestra **con una entidad que no tiene código propio**. No espera a `patients`: se prueba aquí, con una tabla de prueba.

- [ ] 2.1 **Test de aislamiento automático** (Scenario "Una entidad de negocio nueva queda aislada sin código propio"): una tabla de negocio de prueba con RLS por organización y **ni una línea de código de aislamiento** queda restringida a la organización de la petición. Contra **PostgreSQL real** y como el **rol de aplicación no-owner** — el único contexto donde RLS aplica de verdad (extiende el patrón que ya usa `OrganizationRlsIT` con su tabla-fixture)
- [ ] 2.2 **Test fail-closed** (Scenario "Una operación sin contexto falla de forma ruidosa"): sin contexto, la operación **lanza error**; **NO** devuelve vacío. *(Un vacío se confundiría con "esta clínica no tiene datos" y ocultaría el fallo durante meses.)*
- [ ] 2.3 **Test de los flujos previos a la sesión** (Scenario "Los flujos previos a la sesión siguen funcionando"): el alta de una organización con su primer administrador **se completa**, y el mecanismo automático **no la bloquea** por falta de contexto
- [ ] 2.4 Confirmar que **toda la suite de auth sigue en verde** (el enganche no debe alterar login, refresco ni logout)

## 3. Documentación (estándar perenne)

- [ ] 3.1 `docs/backend.md §8.1`: el contexto de tenant **se fija solo**. Documentar **QUÉ NO HAY QUE ESCRIBIR** en una capacidad de negocio nueva —igual que se hizo con los errores transversales del contrato—, porque el riesgo real a medio plazo es que alguien "arregle" un fallo cerrado **fijando el contexto a mano en su servicio**, devolviendo el aislamiento a la disciplina
- [ ] 3.2 `docs/testing.md`: el **test de aislamiento** como puerta de entrada obligatoria de toda capacidad de negocio

## 4. Cierre

- [ ] 4.1 `openspec validate update-multi-tenancy --strict`
- [ ] 4.2 `mvn verify` en verde (incluidos los `*IT` de RLS contra PostgreSQL real)
- [ ] 4.3 Commits por scope, Conventional Commits en español

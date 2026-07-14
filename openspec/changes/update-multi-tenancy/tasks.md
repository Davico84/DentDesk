## 1. El enganche automático del contexto de tenant (design.md D1)

- [x] 1.1 **Resolver la Open Question 1**: implementar el enganche en el punto por el que pasan **todas** las transacciones — candidata preferida: **el gestor de transacciones** (garantiza que ninguna se escapa); alternativa: interceptor de Hibernate
- [x] 1.2 🚨 **El `set_config` debe ir sobre la conexión de ESA transacción**. **NO reutilizar `TenantSession` tal cual**: inyecta un `@PersistenceContext EntityManager` (proxy compartido), y dentro de `doBegin` el `EntityManagerHolder` **todavía no está enlazado al hilo** → el proxy abriría **otro** `EntityManager` sobre **otra conexión**, y como `set_config(..., true)` es *transaction-local*, la variable **se fijaría en la conexión equivocada y se descartaría**. No es una fuga (RLS falla cerrado), pero **el código "se ve bien"** y se depura a ciegas. Orden correcto: `super.doBegin(...)` **primero**, y **después** fijar las variables sobre el `EntityManagerHolder` ya enlazado
- [x] 1.3 **El contexto se lee de `IdentityContext` y `OrganizationContext`** — los `ThreadLocal` que `JwtAuthenticationFilter` **ya puebla** desde el token verificado y limpia en su `finally`. No del `SecurityContext`
- [x] 1.4 Aplicar las **reglas del enganche**: identidad **y** organización → se fijan ambas; solo identidad → solo esa; **ninguna** → **nada** (y RLS falla cerrado al tocar una tabla de negocio, que es lo correcto)
- [x] 1.5 **NO tocar los flujos de auth** (design.md D2): en el registro la organización se crea **en la misma transacción**, así que no hay token del que leer el contexto. `AuthService` conserva su fijación explícita — no es una grieta en la regla: **en el bootstrap no hay nada que propagar**

## 2. La verificación — es lo que hace honesto al requirement (design.md D3, D4)

> El requirement dice *"una entidad de negocio nueva queda aislada **sin código propio**"*. Se demuestra **con una entidad que no tiene código propio**. No espera a `patients`: se prueba aquí, con una tabla de prueba.

- [x] 2.1 **Test de aislamiento automático** (Scenario "Una entidad de negocio nueva queda aislada sin código propio"): una tabla de negocio de prueba con RLS por organización y **ni una línea de código de aislamiento** queda restringida a la organización de la petición. Contra **PostgreSQL real** y como el **rol de aplicación no-owner** — el único contexto donde RLS aplica de verdad (extiende el patrón que ya usa `OrganizationRlsIT` con su tabla-fixture `test_business`)
- [x] 2.2 🚨 **El test NO debe llamar a `TenantSession.bind(...)`**. Si lo llama, prueba `bind()` —que ya funciona— y **no el enganche**: sería un test decorativo que pasa en verde con el mecanismo roto. Debe **poblar `OrganizationContext` como lo haría el filtro** y entrar por una operación `@Transactional` normal. **La ausencia de `bind` en el test es el aserto**
- [x] 2.3 **Test fail-closed**: sin contexto, la operación de negocio **lanza error**; **NO** devuelve vacío. *(Cumple el Scenario que `multi-tenancy` **ya tiene** —"Operación de negocio sin organización en el contexto"—, que hasta hoy era inverificable porque no había tablas de negocio. Un vacío se confundiría con "esta clínica no tiene datos" y ocultaría el fallo durante meses.)*
- [x] 2.4 **Test de los flujos previos a la sesión** (Scenario "Los flujos previos a la sesión siguen funcionando"): el alta de una organización con su primer administrador **se completa**, y el mecanismo automático **no la bloquea** por falta de contexto
- [ ] 2.5 Confirmar que **toda la suite de auth sigue en verde** (el enganche no debe alterar login, refresco ni logout)

## 3. El tripwire — la regla la defiende la build, no un documento (design.md D5)

> El riesgo real de este mecanismo no es que falle: es que **alguien lo desactive sin querer**. El día que un fallo cerrado bloquee una consulta, la reacción natural será *"pues fijo el contexto aquí y ya"* — y el aislamiento vuelve a depender de la disciplina, sin que nadie se entere.

- [x] 3.1 **Regla de ArchUnit** (Scenario "El código de negocio no puede fijar el contexto a mano"): `service` y `presentation` **NO** pueden llamar a `TenantSession`. Solo `infra/tenancy` y los flujos de auth (D2). Va en `MultiTenancyArchitectureTest`, junto al *tripwire* que ya bloquea `@Async`/`@Scheduled` sin cablear
- [x] 3.2 Verificar que la regla **falla de verdad**: introducir temporalmente una llamada desde un servicio y comprobar que **rompe la build** (una regla de arquitectura que nunca ha fallado no se sabe si funciona)

## 4. Documentación (estándar perenne)

- [x] 4.1 `docs/backend.md §8.1`: el contexto de tenant **se fija solo**. Documentar **QUÉ NO HAY QUE ESCRIBIR** en una capacidad de negocio nueva —igual que se hizo con los errores transversales del contrato— y mencionar que **la build lo impide** (§3.1), no solo que se desaconseja
- [x] 4.2 `docs/testing.md`: el **test de aislamiento** como puerta de entrada obligatoria de toda capacidad de negocio, y la regla de que **no puede fijar el contexto él mismo**

## 5. Cierre

- [x] 5.1 `openspec validate update-multi-tenancy --strict`
- [ ] 5.2 `mvn verify` en verde (incluidos los `*IT` de RLS contra PostgreSQL real). ⚠️ **Los `*IT` de Testcontainers se saltan en local (Windows/Docker Desktop): la verificación real es la de CI en Linux**
- [x] 5.3 Commits por scope, Conventional Commits en español

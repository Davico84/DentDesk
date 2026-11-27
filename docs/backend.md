# Estándares de Backend — OdontoRisas

Parte de la documentación de OdontoRisas. Estándares de código y convenciones del backend **Java 25 · Spring Boot 4.0.6**.
Complementa a `docs/architecture.md` (arquitectura por capas), `docs/testing.md` (pruebas: slices + Testcontainers) y `docs/commits.md`.
El equivalente de frontend es `docs/frontend.md` (y el estilo general de código en `docs/coding-style.md`).

---

## 1. Arquitectura por capas

Flujo de dependencias **estricto**:

```
presentation  →  service  →  persistence
                   ↑
infra (config · security · storage · mail) = adaptadores técnicos (transversal) · common = utilidades
```

| Capa | Paquete | Responsabilidad | NO debe |
|---|---|---|---|
| Presentación | `presentation.controller`, `presentation.dto` | Recibir HTTP, validar request (DTO + Bean Validation), delegar en services, construir respuesta | Contener lógica de negocio ni acceder a `persistence`/entidades |
| Servicio | `service.<dominio>` | **Lógica de negocio**, transacciones, orquestación, mapeo entidad→record del servicio (view) | Depender de `presentation` |
| Persistencia | `persistence.entity`, `persistence.repository`, `persistence.specification` | Entidades JPA, repositorios Spring Data, queries dinámicas (Specifications) | Contener reglas de negocio; depender de `presentation`/`service` |
| Infraestructura | `infra.config`, `infra.security`, `infra.storage`, `infra.mail` | Adaptadores técnicos: configuración del framework, seguridad, S3, mail | Contener lógica de negocio |
| Común | `common` | Utilidades transversales y tipos compartidos | Depender de un dominio |

**Reglas de oro:**
- La **lógica de negocio vive en `service.<dominio>`**, no en `infra` (infra = solo técnico).
- `presentation` **nunca** importa de `persistence` (ni entidades ni repositorios). Habla con `service`.
- Las **entidades JPA no salen de `persistence`... ni de `service`**: el service las convierte a **records propios** (views: `PatientView`, `Branding`, `AuthResult`) y `presentation` traduce esos records a DTO. Ver §6.
- `infra` (config/security) **sí** puede cablear servicios: es el composition root (por eso ArchUnit no restringe `infra → service`).
- Los controllers son **delgados**: sin lógica; orquestan y delegan.
- Un `<dominio>` (capacidad) tiene su paquete `service.<dominio>` (ver granularidad de capacidades en `docs/vision.md §5`).

### 1.1 Dependencias entre dominios `service.*` — declaradas y sin ciclos

`ServiceDomainBoundariesTest` (ArchUnit, [`ADR-0021`](adr/0021-limites-entre-modulos.md)) vigila los paquetes `service.<dominio>`:

- **Sin ciclos** (`slices().beFreeOfCycles()`): un ciclo entre dominios rompe la build y nombra los dos.
- **Solo las dependencias que declara el mapa `ALLOWED`** del test. El grafo actual, aceptado tal cual:

  ```
  admission        → clinicalhistory, dentalchart, staff
  appointment      → consultationnote, patient, staff
  auth             → organization, staff
  clinicalhistory  → patient
  consultationnote → clinicalhistory, patient, staff
  dentalchart      → patient
  platform         → auth, organization, staff
  organization, patient, staff → (ninguno)
  ```
- **Dominio nuevo ⇒ entrada nueva**: los dominios se toman de las clases, no de una lista fija, así que un paquete `service.<x>` sin entrada falla.
- **Excepciones compartidas de un flujo**: viven en el dominio que **no** crea el ciclo. `EmailAlreadyExistsException` e `InvalidInvitationException` están en `service.staff` (flujo de invitación); `auth` las importa de ahí porque `auth → staff` ya existía. Si una excepción la necesitan dos dominios que no se conocen, se decide en su change (un paquete transversal se descartó mientras haya dos consumidores).
- **No vigila:** `presentation` e `infra` entre dominios (orquestan y cablean), ni las clases sueltas de `service` (`BusinessException`). ArchUnit ve dependencias de bytecode: un `{@link}` de Javadoc no cuenta.

---

## 2. Convenciones de nombres

- **Idioma**: identificadores en **inglés** (convención Java); Javadoc/comentarios en español; mensajes de log/excepción en inglés. Elige un idioma para los términos de dominio y sé consistente en todo el backend.
- Clases/Interfaces: `PascalCase`. Métodos/variables: `camelCase`. Constantes: `UPPER_SNAKE_CASE`. Paquetes: `minúscula.sin.guiones`.
- **Sufijos por rol** (obligatorios):

| Elemento | Sufijo | Ejemplo |
|---|---|---|
| Controller | `Controller` | `AppointmentController` |
| Service | `Service` — **clase concreta por defecto**; interfaz + `ServiceImpl` **solo** cuando exista una segunda implementación real | `AppointmentService` |
| Repositorio | `Repository` | `AppointmentRepository` |
| Entidad | — (sustantivo) | `Appointment` |
| DTO | `Request`/`Response` | `PatientRequest`, `PatientResponse` |
| Record del servicio (view/command) | `View`/`Command`/sustantivo | `PatientView`, `RegisterCommand`, `Branding` |
| Specification | `Specification`/`Specs` | `AppointmentSpecifications` |
| Excepción | `Exception` | `AppointmentNotFoundException` |

---

## 3. SOLID y DRY (aplicado a Spring)

- **SRP**: una clase, una razón de cambio. El controller enruta, el service decide, el repository persiste.
- **OCP/DIP**: depende de **abstracciones** donde aporten. Regla del proyecto (§2): service como **clase concreta por defecto**; extrae la interfaz **cuando aparezca la segunda implementación**, no de forma especulativa.
- **ISP**: interfaces pequeñas y enfocadas.
- **DRY**: centraliza validación/reglas repetidas en el service o en validadores; no dupliques mapeos (una sola factoría `from(...)` por DTO, §6).

---

## 4. Inyección de dependencias

- **Siempre por constructor** con campos `final` + Lombok `@RequiredArgsConstructor`. **Prohibida** la inyección por campo (`@Autowired` en atributos).

```java
@Service
@RequiredArgsConstructor
public class AppointmentService {
    private final AppointmentRepository repository;
}
```

---

## 5. Controllers y diseño de API REST

- URLs por **recurso** bajo `/api`, en plural y kebab/lowercase: `/api/appointments`, `/api/appointments/{id}`.
- Métodos HTTP correctos: `GET` (leer), `POST` (crear), `PUT`/`PATCH` (actualizar), `DELETE` (borrar).
- Status codes correctos: `200/201/204`, `400/401/403/404/409`, `500`. Usa `ResponseEntity<T>`.
- **Nunca** recibir/retornar entidades JPA: usa DTOs (`*Request` entrada, `*Response` salida).
- Validación de entrada con `@Valid` sobre el DTO.
- Documenta con **springdoc-openapi** (`@Operation`, `@ApiResponse`) — la documentación (`/v3/api-docs` y Swagger UI) está **apagada por defecto** y se enciende con `SWAGGER_ENABLED=true` (§12).

```java
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentService service;

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }
}
```

### 5.1 Paginación

Patrón real (`PatientController`/`PatientService`): el controller recibe `Pageable` como parámetro
(`@ParameterObject @PageableDefault(size = 20) Pageable pageable`, `springdoc` lo documenta solo) y
el service lo pasa tal cual al repositorio (`JpaSpecificationExecutor.findAll(spec, pageable)`).

- Query params estándar (los da Spring Data): `page` (base 0), `size`, `sort`.
- `size` por defecto **20** vía `@PageableDefault`. Documenta el que uses en cada endpoint si difiere.
- **Nunca** devuelvas `Page<T>` de Spring Data directamente (su serialización JSON es inestable y no
  debe filtrarse a un contrato del que el frontend genera cliente) — envuelve en `PageResponse<T>`
  (`presentation.dto.PageResponse`, ya existe: `content`/`page`/`size`/`totalElements`/`totalPages`/`last`).
- **Pendiente de decidir**: hoy no hay tope máximo de `size` (un cliente podría pedir `size=100000`).
  Cuando aparezca el segundo endpoint paginado, cablear un `PageableHandlerMethodArgumentResolverCustomizer`
  con un máximo (p. ej. 100) — no se resuelve especulativamente para uno solo.

---

## 6. DTOs, validación y mapeo

- **DTOs** preferentemente `record` (inmutables). Separa entrada (`*Request`) de salida (`*Response`).
- **Bean Validation** en los `*Request`: `@NotNull`, `@NotBlank`, `@Email`, `@Size`, `@Positive`, etc. La validación de **formato** vive en el DTO; la de **reglas de negocio** en el service.
- **Mapeo en dos saltos, sin clases `Mapper`** (patrón del proyecto; MapStruct **no** está en el pom):
  1. El **service** mapea entidad → **record propio del servicio** (view: `PatientView`, `Branding`; entrada compleja: `RegisterCommand`). Las entidades JPA no salen de ahí.
  2. **`presentation`** traduce ese record a DTO con una **factoría estática** `Response.from(view)` en el propio DTO (así lo hacen `PatientResponse`, `BrandingResponse`, `AuthResponse`...).

  Ni el controller conoce entidades, ni el service conoce DTOs — es lo que vigila ArchUnit.
- **Paridad con el frontend**: las restricciones de **formato** de cada `*Request` (longitud, patrón, rango, requerido) deben reflejarse en el schema Zod del formulario correspondiente. El backend es la barrera definitiva; el frontend evita `400` de formato innecesarios. Política y tabla de correlación en `docs/coding-style.md §7`.

```java
public record CreateAppointmentRequest(
    @NotNull Long patientId,
    @NotNull @Future Instant scheduledAt,   // instante → Instant, nunca LocalDateTime (§8.2)
    @Size(max = 500) String notes
) {}
```

---

## 7. Services

- Contienen la **lógica de negocio** y orquestan repositorios/otros services.
- **Transaccionalidad**: `@Transactional` en métodos de escritura; `@Transactional(readOnly = true)` en lecturas. Preferir el límite transaccional en el service, no en el controller.
- Lanzan excepciones de dominio (§10) cuando una regla no se cumple.

---

## 8. Persistencia (JPA / Spring Data)

- **Entidades**:
  - No usar Lombok `@Data`/`@EqualsAndHashCode` por defecto en entidades (rompe con lazy loading y colecciones). Usa `@Getter/@Setter` puntuales; `equals/hashCode` basados en la clave de negocio o en el id con cuidado.
  - Relaciones **LAZY** por defecto; carga explícita con `@EntityGraph` o *fetch join*.
  - Nombres de tabla/columna definidos por Flyway; las anotaciones reflejan el esquema (no lo generan).
- **Repositorios**: interfaces `JpaRepository<Entity, Id>`. Consultas derivadas por nombre cuando sean simples; `@Query` para casos concretos.
- **Queries dinámicas**: usa `JpaSpecificationExecutor` + clases en `persistence.specification` (no concatenar strings de JPQL).
- **Rendimiento**: evita N+1 (fetch joins/`@EntityGraph`), selecciona solo lo necesario (proyecciones/DTO projections), pagina con `Pageable`.

---

## 8.1 Multi-tenancy — reglas de aislamiento (RLS)

El sistema es **multi-tenant**: cada organización (tenant) está aislada. El aislamiento es en dos capas (aplicación con Hibernate `@TenantId` + motor con Row-Level Security). Ver la capacidad `multi-tenancy` (`openspec/specs/multi-tenancy/`) y `docs/architecture.md`. **No negociable** al construir cualquier capacidad de negocio:

- **Toda `@Entity` de negocio** (pertenece a una organización) lleva:
  1. Columna `organization_id BIGINT NOT NULL` + campo `@TenantId` en la entidad (Hibernate la rellena y filtra sola).
  2. **Índice** sobre `organization_id` (sin él, RLS filtra con un *seq scan*: el índice es parte de la política de aislamiento, no una optimización posterior).
  3. En su migración: `ENABLE` + **`FORCE ROW LEVEL SECURITY`** y la política
     `CREATE POLICY organization_isolation ON <tabla> USING (organization_id = current_setting('app.organization_id')::bigint);`
     **sin `missing_ok`** (fallo ruidoso ante consulta sin contexto).
- **Acceso siempre transaccional**: cualquier operación sobre una entidad bajo RLS va en `@Transactional` (o `@Transactional(readOnly = true)`). Las variables de sesión (`set_config(..., true)`) son *transaction-local*; fuera de transacción el contexto se pierde y RLS bloquea. **Enforced por ArchUnit**.
- **El tenant se lee SOLO del token firmado** (`OrganizationContext`/`IdentityContext`, poblados por `JwtAuthenticationFilter` desde los claims). **Prohibido** derivar `organizationId`/`userId` de `@RequestHeader`, `@PathVariable` o `@RequestBody`.
- **Nada de `nativeQuery` sin filtro de organización** (aunque RLS lo cubra: la claridad importa).
- **Hilos async**: `@Async`/`@Scheduled` NO heredan el contexto (`ThreadLocal`). Antes de introducir el primero hay que cablear un `TaskDecorator` (async derivado de petición) o fijar el contexto explícitamente por tenant (jobs de fondo). Un test ArchUnit falla la build si aparece `@Async`/`@Scheduled` sin ese cableado.
- `spring.jpa.open-in-view` debe seguir en **`false`**.
- **Dominio de auth** (`users`, `refresh_tokens`): NO llevan RLS por organización (se consultan por credencial email/hash). `memberships` lleva RLS de **identidad** (`user_id = current_setting('app.user_id')`).
- **Tabla nueva ⇒ se clasifica en el mismo cambio.** Toda tabla de `public` figura en una de las listas de `TenantTableClassification` (test del backend; motivos en [`ADR-0022`](adr/0022-cobertura-rls-y-excepciones-por-clinica.md)): por organización, por identidad, excepción por clínica (con su ADR) o sin `organization_id`. `TenantTableInventoryIT` falla ante una tabla sin clasificar, una aislada sin RLS forzada o sin política, o una vista u objeto fuera de `public`.
- **Función `SECURITY DEFINER` nueva ⇒ se declara por firma con los tests que la cubren y fija `SET search_path = public`.** Corre con los privilegios del dueño y puede leer por encima de la RLS; el inventario falla si entra sin declarar o sin `search_path` fijo.

### 8.1.1 El contexto se fija SOLO — qué NO hay que escribir

**`TenantAwareTransactionManager` establece el contexto de tenant en toda transacción**, leyendo `IdentityContext`/`OrganizationContext`. Cumple con la lista de arriba y tu capacidad **ya está aislada**. No hay nada que recordar.

> **Por defecto, no por disciplina.** Un mecanismo que exija acordarse de una llamada acabará olvidándose — y ese olvido **no falla de forma visible: filtra historia clínica entre clínicas**.

**❌ NO escribas esto en un servicio de negocio:**

```java
@Service
public class PatientService {
    private final TenantSession tenantSession;          // ❌ la build falla

    @Transactional
    public List<Patient> findAll() {
        tenantSession.bind(userId, organizationId);     // ❌ ya está hecho
        ...
    }
}
```

**Una regla de ArchUnit rompe la build** si una clase de `service` (salvo `service.auth`) o de `presentation` depende de `TenantSession`. No es un consejo: es un candado. Y está probado que **falla de verdad** cuando se viola.

**Si una consulta te falla con "unrecognized configuration parameter app.organization_id"**, el impulso natural será fijar el contexto a mano. **No lo hagas** — ese fallo significa que estás fuera de una petición autenticada (una tarea de sistema, un test mal montado) o fuera de transacción. **Es el mecanismo funcionando**, no un estorbo: RLS **falla cerrado** en vez de devolverte una lista vacía que confundirías con *"esta clínica no tiene pacientes"*.

La única excepción es `service.auth`: en el registro la organización **se crea en esa misma transacción**, así que no hay token del que leer el contexto. Ahí `bindIdentity(...)` se llama explícitamente — no es una grieta, es que en el bootstrap **no hay nada que propagar**.

---

## 8.2 Fechas y zonas horarias — todo en UTC

La app se ve desde cualquier país: **ningún instante se representa con la zona horaria del
servidor**. Regla, ya vigente de facto en el código (`createdAt`/`updatedAt`/`expiresAt` en
`User`, `Organization`, `Membership`, `RefreshToken`, `Patient`):

- **Instante** (un momento concreto en el tiempo — cuándo se creó un registro, cuándo vence un
  token, cuándo empieza una cita) → Java `Instant` + columna Postgres `TIMESTAMPTZ`.
  `TIMESTAMPTZ` normaliza y guarda en UTC sin importar la zona de la sesión; `Instant` es UTC por
  definición. Genera el valor con `Instant.now()`.
- **Prohibido `LocalDateTime`** para representar un instante: no lleva zona, es ambiguo apenas hay
  más de un huso horario en juego (justo el caso de este SaaS). El ejemplo de `CreateAppointmentRequest`
  en §6 usa `Instant` por esto.
- **Fecha de calendario sin hora** (cumpleaños, `Patient.birthDate`) → Java `LocalDate` + columna
  `DATE`. No es un instante — es una fecha civil, no lleva ni necesita zona horaria.
- **Presentación**: Jackson serializa `Instant` a ISO-8601 con sufijo `Z` (UTC) automáticamente, sin
  anotar nada. La conversión a la hora local del usuario es responsabilidad del **frontend**, nunca
  del backend.

---

## 8.3 Concurrencia — bloqueo optimista (`@Version`)

Hoy **ninguna entidad lo usa** (no hace falta: los flujos actuales no tienen edición concurrente
real). Regla para cuando aparezca una que sí — candidatas previsibles: `appointments` (agenda
compartida por recepción/dentista) o cualquier entidad de facturación:

- Añade `@Version private Long version;` en la entidad + columna `version BIGINT NOT NULL DEFAULT 0`
  en su migración. Hibernate la gestiona sola: incrementa en cada `UPDATE` y lanza
  `ObjectOptimisticLockingFailureException` si la fila cambió entre lectura y escritura.
- Esa excepción se mapea a **`409`** en `GlobalExceptionHandler` (§10) el día que se introduzca el
  primer `@Version` — no antes (no hay nada que mapear todavía).
- No lo añadas de forma especulativa a entidades sin edición concurrente real: es ruido y una
  fuente de `409` que nadie espera.

### 8.3.1 Documentos sellables — bloquear **antes** de comprobar el estado

Un documento que se sella (Historia Clínica `DRAFT → COMPLETED`, odontograma `OPEN → SEALED`)
**no** se protege con `@Version`: sus escrituras incluyen filas de **otras** tablas (diagnósticos,
hallazgos), que el versionado de la fila padre no ve. La regla, que pagó dos bugs reales (`add-dental-chart`
2.8a/2.8f, `update-clinical-history`):

- **Toda** escritura del documento o de sus hijos pasa por un helper que hace `SELECT ... FOR UPDATE`
  y **después** comprueba el estado abierto: `ClinicalHistoryService.lockDraft`,
  `DentalChartService.lockOpenBlock`. Leer, comprobar y guardar sin bloqueo deja un hueco: si el
  sellado confirma en medio, como Hibernate escribe **todas** las columnas, guardar el documento con
  el estado leído antes **lo reabre**.
- **Primera carga:** el bloqueo tiene que ser la primera lectura de esa entidad en la transacción. Si
  ya está en el contexto de persistencia, Hibernate devuelve la instancia vieja **sin refrescarla** y
  el `FOR UPDATE` no protege nada. Por eso las consultas previas que solo preguntan si existe
  (`existsByPatientId`) no cargan la entidad.
- **Orden único** cuando una ruta toca varios: paciente → Historia → odontograma
  (`CloseAdmissionService`). Distinto orden en dos rutas = deadlock intermitente.
- Se prueba con **hilos reales** y orden forzado: un hilo toma el bloqueo, el otro arranca después y
  el primero confirma cuando el segundo queda esperando (`AbstractIntegrationTest.awaitLockWaitOrFinished`,
  que mira `pg_locks`). Primero contra el código sin fix, para ver el fallo.

---

## 8.4 Modelado de dominio — conjuntos cerrados y formatos

Lecciones de la auditoría de dominio de `patients` (`openspec/changes/archive/2026-07-16-add-patients/design.md`),
ya vigentes como estándar para toda capacidad nueva:

- **Conjunto cerrado de valores → enum, nunca `String` libre.** Se refleja en **tres** sitios para
  que el frontend lo herede: enum Java en la entidad, `CHECK` en la migración, y el enum aparece en
  el contrato OpenAPI (springdoc lo infiere del tipo Java). Ejemplos reales: `DocumentType`, `Sex`,
  `GuardianRelationship` en `Patient`.
- **Nunca tipo numérico para identificadores con formato** (documento, teléfono): pierden ceros a la
  izquierda y los pasaportes son alfanuméricos. `VARCHAR` + validación de formato explícita (Bean
  Validation o un validador propio — ver `ValidDocument`).
- **Colección embebida de una entidad bajo RLS → columna `JSONB` en la misma tabla, no tabla hija.**
  Una tabla hija sin su propio `organization_id` es un agujero de aislamiento: RLS no la protege.
  Ejemplo real: `allergies`/`conditions`/`currentMedication` en `Patient`.

---

## 8.5 `Optional` y valores ausentes

- **Sí**: `Optional<T>` como **tipo de retorno** de un método de repositorio o service que busca
  **un único resultado que puede no existir** (`findByEmail`, `findBySlug`, `findByTokenHash`) — es
  la convención de Spring Data y ya la sigue todo el código.
- **No**: `Optional` como **parámetro** de método, como **campo** de entidad/DTO/record, ni
  envolviendo una **colección** (`Optional<List<T>>` — usa lista vacía).
- Un `Optional` vacío que representa "no encontrado" en un flujo de negocio se traduce a la
  excepción de dominio correspondiente (`PatientNotFoundException`, §10) **en el service** — no
  propagues el `Optional` hasta `presentation`.

---

## 8.6 Auditoría de autoría (`createdBy`/`updatedBy`)

`docs/backend.md §8.1` ya trata la historia clínica como *"el dato más sensible del producto"* y el
spec de `patients` exige retención legal. RLS garantiza **qué organización** tocó un registro, pero
hoy **ningún** registro de negocio dice **qué usuario** dentro de esa organización lo hizo. Regla
para toda entidad de negocio nueva, con el mismo criterio de §8.1 ("no negociable"):

- Columnas `created_by BIGINT REFERENCES users(id)` y `updated_by BIGINT REFERENCES users(id)` junto
  a `created_at`/`updated_at` en la migración.
- Campos `@CreatedBy`/`@LastModifiedBy` (Spring Data JPA Auditing) en la entidad, con
  `@EntityListeners(AuditingEntityListener.class)` — **no confundir con** `@CreationTimestamp`/
  `@UpdateTimestamp` (Hibernate nativo), que es lo que ya rellena `created_at`/`updated_at` hoy: son
  **dos mecanismos distintos**. `@CreationTimestamp` no sabe quién hizo el cambio, solo cuándo.
- Habilitar `@EnableJpaAuditing` (`infra.config`) con un `AuditorAware<Long>` que lee
  `common.IdentityContext.get()` — el `userId` **ya está disponible por transacción** gracias a
  `TenantAwareTransactionManager` (§8.1.1); el `AuditorAware` es un adaptador fino sobre algo que ya
  existe, no una plomería nueva.
- Mismo caso límite que el contexto de tenant (§8.1.1): en `service.auth` durante el registro no hay
  `IdentityContext` (la identidad se crea en esa misma transacción) — el `AuditorAware` debe devolver
  `Optional.empty()` ahí, no lanzar.

---

## 9. Migraciones (Flyway)

- Única fuente del esquema: `spring.jpa.hibernate.ddl-auto=none`. Hibernate **no** crea ni altera tablas.
- Migraciones en `src/main/resources/db/migration`, nombre `V<n>__descripcion_snake.sql` (p. ej. `V1__init.sql`, `V2__add_appointments.sql`).
- Una migración **aplicada es inmutable**: nunca la edites; crea una nueva.
- Revisar cada script antes de mergear.

---

## 10. Manejo de errores — RFC 9457 (Problem Details)

**Estándar del proyecto**: todos los errores se devuelven como **`ProblemDetail` (RFC 9457)**, el tipo nativo de Spring Boot 3/4. No se crean DTOs de error propios.

- **Activación nativa**: `spring.mvc.problemdetails.enabled=true` en `application.yml`. Con esto, las excepciones propias de Spring MVC (404, 405, JSON malformado, etc.) ya responden en formato ProblemDetail.
- **Handler centralizado**: un único `@RestControllerAdvice` que **extiende `ResponseEntityExceptionHandler`** (para conservar el status de las excepciones de Spring y no convertirlas en 500). Vive en `presentation` (`GlobalExceptionHandler`).
- **Excepciones de negocio**: heredan de una base `service.BusinessException` que lleva el `HttpStatus` y un `errorType` (slug). Un solo `@ExceptionHandler(BusinessException.class)` las mapea a ProblemDetail — no hace falta un handler por excepción. Ejemplos: `EmailAlreadyExistsException` (409), `InvalidCredentialsException` (401).
- **`type` relativo**: `/errors/<errorType>` (agnóstico al entorno; nada de URLs absolutas por dominio). Usa `about:blank` solo si no aplica un tipo.
- **Extensiones**: se añaden `timestamp` (Instant) y `traceId` a cada error. El `traceId` lo genera un `TraceIdFilter` (en `infra.config`) que lo registra en el **MDC** (aparece en todos los logs de la petición) y lo devuelve en la cabecera `X-Trace-Id`. Así un error en producción es rastreable de punta a punta.
- **Validación (`@Valid`) → 400**: se sobrescribe `handleMethodArgumentNotValid` para incluir la extensión `errors` (lista de `{field, message}`).
- **Errores inesperados → 500**: red de seguridad `@ExceptionHandler(Exception.class)` que **loguea el stacktrace con el traceId** y responde un ProblemDetail genérico. **Nunca** se filtran stack traces ni detalles internos en la respuesta.

Ejemplo de respuesta (validación):
```json
{
  "type": "/errors/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Uno o más campos son inválidos.",
  "errors": [{ "field": "password", "message": "La contraseña debe tener al menos 8 caracteres" }],
  "timestamp": "2026-07-03T12:34:56Z",
  "traceId": "a1b2c3d4"
}
```

> El frontend consume estos errores de forma tipada vía el contrato OpenAPI (orval). El `Content-Type` es `application/problem+json`.

---

### 10.1 Un único formato de error — sin excepciones

**Regla dura: TODA respuesta de error sale de `presentation.ProblemDetails`.** Es la fábrica única del cuerpo. Si un error se escapa de ella, la API falla con dos formas distintas, el contrato no puede describirlas con un solo schema y el frontend no puede tipar el manejo de errores.

Dos fugas que hubo que tapar (no las reintroduzcas):

- **El `401` de sesión ausente.** El entry point de seguridad hacía `sendError(401)`, que devuelve el cuerpo por defecto del contenedor (`{timestamp,status,error,path}`). Ahora **delega en el `HandlerExceptionResolver` de MVC**, así que lo renderiza el mismo `GlobalExceptionHandler`. Nunca serialices un error a mano en un filtro: delega.
- **Los errores de la clase padre** (404, 405, JSON malformado). Construyen su `ProblemDetail` por su cuenta, sin pasar por la fábrica, así que salían **sin `timestamp` ni `traceId`**. Se sobrescribe `handleExceptionInternal` para enriquecerlos.

**Fidelidad del cuerpo** (la documentan `ApiProblem`/`ValidationProblem`, y **la fija `ErrorContractIT` comparando contra la respuesta real** — no te fíes de leer el handler: `instance` se emite sin que nadie lo asigne):

| Campo | Regla |
|---|---|
| `timestamp` | **siempre** |
| `traceId` | **opcional** — solo si el MDC lo tiene |
| `instance` | **sí se emite** — Spring lo rellena con la URI **al renderizar**, aunque el handler nunca llame a `setInstance` ⚠️ |
| `errors` | **opcional** — el `400` tiene dos formas: validación (con detalle por campo) y JSON malformado (sin él) |

**El `detail` de CUALQUIER error tiene que ser apto para el usuario final** (`update-api-type-contracts`, 2026-07-23) — no solo la forma, también el contenido. Se verificó en vivo que un `400` de JSON malformado respondía `"Failed to read request"`: el mensaje **interno** de Spring, en inglés, que el frontend (`getUserFriendlyError`) pinta tal cual en un toast. Esto aplica a **todo origen** de error, no solo a los que `GlobalExceptionHandler` declara: también a la **capa de Spring Security** (401/403 que nacen fuera del controller).

- **Los errores que resuelve el manejador por defecto de Spring MVC** (JSON malformado, 404, 405, 415, 406, 413, 503…) reemplazan su `detail` en `handleExceptionInternal` con un mapa `HttpStatus → mensaje en español`. Es un **fallback por status, no una traducción por tipo de excepción**: dos excepciones distintas con el mismo status comparten mensaje a propósito. Un status fuera del mapa cae en un genérico neutro — nunca en el texto interno del framework.
- **Los errores de Spring Security fuera del controller** (401 sin sesión, 403 por regla de rol a nivel de **ruta**) delegan en el mismo `HandlerExceptionResolver` que el resto: el `authenticationEntryPoint` y el `accessDeniedHandler` de `SecurityConfig` llaman a `handlerExceptionResolver.resolveException(...)`, así que terminan en los `@ExceptionHandler` de siempre (`handleAuthentication`/`handleAccessDenied`), con su `detail` ya curado.

⚠️ **Regla para cualquier `@ExceptionHandler` nuevo, método del framework sobreescrito, o punto de extensión de seguridad que añadas**: **queda fuera** del mapa automático de `handleExceptionInternal` (que solo procesa lo que la clase padre resuelve). Por tanto **debe proveer su propio `detail` en español** vía `ProblemDetails.of(...)` — igual que ya hacen `handleBusiness`, `handleAuthentication`, `handleAccessDenied`, `handleDataIntegrity` y `handleMethodArgumentNotValid`. Si no lo haces, ese path filtra el mensaje interno del framework al usuario.

### 10.2 Documentar una operación en el contrato

El contrato OpenAPI es la **fuente única de verdad** (capacidad `api-type-contracts`): el frontend deriva de él sus tipos, y `ContractDriftIT` **rompe la build** si el contrato no refleja el backend.

**Lo que NO tienes que escribir** en un endpoint nuevo — los errores transversales se inyectan solos (`OpenApiErrorsConfig`):

| Respuesta | Se añade sola cuando… |
|---|---|
| `500` | siempre |
| `401` | la ruta **no** es pública (ver `PublicPaths`) |
| `403` | el método lleva `@PreAuthorize` |

**Lo que SÍ tienes que declarar** (solo tú lo sabes):

- **El status de éxito, si no es `200`.** Los controllers devuelven `ResponseEntity`, así que la herramienta **no puede inferirlo**: declara `@ApiResponse(responseCode = "201"/"204", ...)`.
- ⚠️ **Si declaras cualquier `@ApiResponse`, la herramienta descarta la respuesta de éxito que infería.** Entonces **declara también el `200`** con su schema, o el contrato perderá el tipo de la respuesta exitosa.
- El **`400`** donde haya `@Valid` (schema `ValidationProblem`), el **`409`** de conflicto, y el `401` **semántico** de rutas públicas (p. ej. credenciales inválidas), que el customizer no añade.

Tras tocar la API: regenera `contracts/openapi.json` **y** el cliente del frontend. Y **exporta el contrato con `curl -o`, nunca canalizándolo por un script**: un `curl | python` en Windows lo dejó doble codificado (cp1252) y reventaba el guardián.

## 11. Seguridad

- **Spring Security + JWT** (`com.auth0:java-jwt`). Configuración en `infra.security`.
- Contraseñas con hashing fuerte (BCrypt). **Nunca** en texto plano ni en logs.
- Autorización por rol (`ADMIN`/`MEMBER`) con seguridad a nivel de método (`@PreAuthorize`) y/o `SecurityFilterChain`.
- El registro público no crea organizaciones: el primer `ADMIN` llega por invitación de plataforma y el `ADMIN` de una clínica invita al personal mediante `/staff/invitations`. Las invitaciones guardan únicamente el hash SHA-256 del token y vencen en siete días.
- **Sin secretos en el código ni en git**: viven en `secrets.properties` / variables de entorno (ignorados), con `secrets.properties.example` versionado (§12).
- Validar y sanear toda entrada; los DTOs + Bean Validation son la primera barrera.

### 11.0 Base de la API: `/api`

Toda la API se sirve bajo `server.servlet.context-path: /api` (`add-cloud-deployment` D1): `/api/auth/login`, `/api/patients`, `/api/actuator/health/liveness`. Sin prefijo, sus rutas chocaban con las pantallas de la app (`/patients/1`) y el proxy de Vercel no podía distinguirlas.

- Los `@RequestMapping`, `PublicPaths` y los matchers de Spring Security siguen **relativos** a la base (`/auth/**`, no `/api/auth/**`); los paths del contrato OpenAPI también, así que el cliente de orval no cambia.
- **MockMvc no aplica el context-path**: los tests con MockMvc usan las rutas relativas. El prefijo real lo prueba `ApiBasePathIT`, con servidor real.
- La cookie de refresh lleva `Path=<base>/auth` (`/api/auth`): `RefreshCookieFactory` lo arma desde `server.servlet.context-path`. Si cambia la base, la cookie la sigue sola.

### 11.1 Actuator — superficie expuesta

`spring-boot-starter-actuator` está en el `pom.xml`. Configuración real (`application.yml`):
`management.endpoints.web.exposure.include: health,info` — **explícito**, no el default del
framework (que además solo trae `health`; aquí se sumó `info` a propósito). No amplíes esa lista sin
decidir a la vez si el endpoint nuevo queda público o autenticado (siguiente punto).

- **Solo `/actuator/health/**` es público** (`PublicPaths` — fuente única compartida con el
  customizer de OpenAPI). `/actuator/info` sigue expuesto pero **autenticado** (nada lo usa, y así
  no queda una ruta pública cuyo contenido crezca sin revisión). Cualquier otro endpoint de actuator
  cae bajo `anyRequest().authenticated()`.
- **Ping de liveness**: `management.endpoint.health.probes.enabled: true` activa
  `GET /api/actuator/health/liveness` (la API vive bajo `/api`, ver abajo), el que llama el monitor externo para mantener despierto el
  backend en Render (`docs/deployment.md`). Su grupo **no incluye la base**: el ping no la despierta.
  No uses `/actuator/health` para eso, porque consulta la base en cada llamada.
- **`show-details: ${HEALTH_SHOW_DETAILS:always}`**: en local el detalle (estado de la BD, del
  disco) se ve; en despliegue `HEALTH_SHOW_DETAILS=never`, porque la ruta es pública. Si un
  *health indicator* propio llegara a exponer algo sensible, ni siquiera `always` en local es buena
  idea: baja a `when-authorized`.
- **Pool de conexiones (Hikari)**: `minimum-idle` 0, `idle-timeout` 60 s, `keepalive-time` 0 y
  máximo 5, por variables `DB_POOL_*`. Sin mínimo ni keepalive, el pool puede quedar vacío y la base
  (Neon) dormir; la siguiente petición abre una conexión nueva. No subas `minimum-idle` ni actives
  el keepalive sin medir: mantendrían la base despierta y agotarían su cupo gratuito.
- Antes de exponer un endpoint nuevo de actuator (`/env`, `/beans`, `/httptrace`...): añádelo a
  `exposure.include` **y** decide explícitamente si entra en `PublicPaths` — nunca por omisión. Si
  se expone `/env`, revisa que no filtre `app.security.jwt.secret` ni credenciales de BD.

### 11.2 CORS

Configurado en `SecurityConfig.corsConfigurationSource`; orígenes desde
`app.security.cors.allowed-origins` (externalizado, §12 — `http://localhost:5173` en local, por env
en despliegue).

- `allowCredentials(true)` porque el refresh token viaja en cookie `HttpOnly` (`RefreshCookieFactory`).
  **Regla dura**: con credenciales habilitadas, `allowedOrigins` **nunca** puede ser `*` — lista
  explícita de orígenes conocidos, siempre.
- ⚠️ **Gotcha de la spec CORS**: `allowedHeaders(List.of("*"))` **no actúa como comodín** en
  peticiones con credenciales — el navegador lo trata como el header literal `"*"`, no como "cualquier
  header". Si el frontend algún día añade un header custom (`X-Something`), el preflight fallará en
  silencio pese a que el código "dice" `*`. El día que haga falta, lista los headers explícitos en
  vez de depurar a ciegas.

### 11.3 Secreto de firma de los tokens (`JWT_SECRET`)

`app.security.jwt.secret` (`${JWT_SECRET}`, sin default) firma los access tokens con HMAC-256. Si
falta, Spring **no falla solo**: deja el placeholder sin resolver y el secreto valdría el literal
`${JWT_SECRET}`, que es público, así que cualquiera podría firmar un token de `ADMIN`. Por eso **la
app no arranca** si el secreto es inseguro (`JwtSecretRules`, capacidad `authentication`):

- **Reglas, en orden:** ausente, vacío o en blanco; con un placeholder sin resolver (`${…}`); igual
  al valor de `secrets.properties.example` (comparado sin espacios alrededor); o de **menos de 32
  bytes** en UTF-8, el mínimo para HMAC-256. El secreto **no se recorta**: el HMAC firma con el valor
  tal cual.
- **La validación vive en el constructor de `TokenService`**, no en el binding de `SecurityProperties`:
  un fallo de binding lo describe el `BindFailureAnalyzer` de Boot con "Value: …" e **imprimiría el
  secreto** en el log de arranque. Ningún mensaje de `JwtSecretRules` incluye el valor (lo fijan
  `JwtSecretRulesTest`, `JwtSecretStartupTest` y `JwtSecretStartupOutputTest`, este último sobre la
  salida real del arranque).
- **Para que la garantía siga valiendo:** **nunca** loguees `SecurityProperties` ni su record `Jwt`,
  porque su `toString()` incluye el secreto. Y no envuelvas la excepción con contexto que lo agregue.
- **Tests:** `AbstractIntegrationTest` registra `TEST_JWT_SECRET` sobre la propiedad final con
  `@DynamicPropertySource`, que gana a `secrets.properties`, a `JWT_SECRET` y a las `-D…`
  (`JwtSecretHermeticityIT`). **No** fijes `app.security.jwt.secret` con `@TestPropertySource` ni
  con `properties =` de `@SpringBootTest`: esas sí le ganan. Nunca imprimas un secreto en un test:
  compáralo por hash.

---

## 12. Configuración

- **Configuración única** (sin perfiles `dev/qa/pro`). Un solo `application.yml` con placeholders `${VAR}` para **todos** los valores configurables.
- **Todo externalizado como secreto**: los valores se inyectan por variables de entorno; en local se cargan de `secrets.properties` (ignorado por git) vía `spring.config.import=optional:file:./secrets.properties`. Se versiona solo `secrets.properties.example`.
- **`JWT_SECRET` es obligatorio en todo entorno**: al menos 32 bytes aleatorios (`openssl rand -base64 48`). Sin él la app no arranca (§11.3).
  - **Rotarlo invalida los access tokens emitidos** (dejan de verificar), pero **no cierra las sesiones**: el refresh token es opaco, se guarda hasheado en BD y no depende del secreto, así que el frontend renueva el access token de forma transparente.
  - Si la rotación busca expulsar a todos (por ejemplo, un secreto filtrado), hay que **revocar también los refresh tokens** (borrar `refresh_tokens`).
  - Con **varias réplicas**, todas deben compartir el mismo secreto: una réplica con otro rechazaría los tokens de las demás.
- **`SWAGGER_ENABLED`** enciende la documentación de la API (`/v3/api-docs` y `/swagger-ui.html`). Por defecto `false`: un despliegue que no la defina **no expone** la API. En local va en `true` en `secrets.properties`; el `.example` trae esa línea **comentada**, así un despliegue que copie el archivo tal cual no la enciende. No se usan overrides directos de springdoc (`SPRINGDOC_*`): el aviso de arranque solo anuncia Swagger si el documento y la interfaz están encendidos. Con el interruptor apagado las rutas siguen en `PublicPaths` pero no existen (`404`). Los tests de contrato la encienden con `@TestPropertySource`; ningún test depende de que falte (`update-api-docs-exposure`).
- **Nunca** hardcodear ni versionar URLs, credenciales ni parámetros de entorno sensibles.
- Tipar la configuración con `@ConfigurationProperties` (evitar `@Value` disperso).

---

## 13. Caché y logging

- **Caché**: Spring Cache + Caffeine. `@Cacheable`/`@CacheEvict` con nombres de caché explícitos; cachea solo lo que aporta (lecturas costosas y estables).
- **Logging**: SLF4J vía Lombok `@Slf4j`. Niveles adecuados (`info`, `warn`, `error`, `debug`). Log estructurado con contexto (ids), **sin** secretos ni PII de pacientes.

---

## 14. Lombok — uso recomendado

- **Sí**: `@RequiredArgsConstructor` (DI), `@Getter/@Setter` (DTOs mutables o config), `@Slf4j`, `@Builder` en DTOs.
- **Evitar**: `@Data`/`@EqualsAndHashCode`/`@ToString` en **entidades JPA**; `@ToString` con relaciones lazy.

---

## 15. Pruebas

No se duplica aquí: la estrategia (niveles, slices, **Testcontainers**, puente Scenario→test) está en **`docs/testing.md`**. Recordatorio operativo:
- Controller → `@WebMvcTest` (service mockeado). Seguridad → `+ spring-security-test`.
- Persistencia/Specifications/Flyway → `@SpringBootTest` acotado a Repository (no `@DataJpaTest`, ver `docs/testing.md §5.1`) + **Testcontainers (PostgreSQL)**.
- Nombre de test referenciando el scenario del spec; patrón AAA.
- `./mvnw -B verify` en verde (requiere Docker).

---

## 16. Workflow y calidad

- Commits: Conventional Commits en español (`docs/commits.md`).
- Antes de PR: `./mvnw -B verify` (compila + tests) en verde; sin warnings sin resolver.
- Ramas de feature pequeñas y enfocadas.

---

## 17. Checklist de pre-entrega (backend)

- [ ] ¿Toda entidad de negocio nueva cumple §8.1: `@TenantId` + índice sobre `organization_id` + `ENABLE`/`FORCE ROW LEVEL SECURITY` + política `organization_isolation` en su migración?
- [ ] ¿Lleva `created_by`/`updated_by` (§8.6), además de `created_at`/`updated_at`?
- [ ] ¿Respeta el flujo de capas (presentación no toca persistencia; entidades no salen de persistence ni de service — views §6)?
- [ ] ¿Controllers delgados y con DTOs (`*Request`/`*Response`), sin exponer entidades?
- [ ] ¿Validación de formato en DTO (Bean Validation) y de negocio en el service?
- [ ] ¿Inyección por constructor (`@RequiredArgsConstructor`), sin inyección por campo?
- [ ] ¿Transaccionalidad correcta (`@Transactional`, `readOnly` en lecturas)?
- [ ] ¿Sin N+1 (fetch join/`@EntityGraph`), consultas dinámicas vía `Specification`?
- [ ] ¿Cambios de esquema como nueva migración Flyway (no editar aplicadas)?
- [ ] ¿Errores centralizados con formato consistente; sin secretos en logs?
- [ ] ¿Instantes en `Instant`/`TIMESTAMPTZ` (nunca `LocalDateTime`, §8.2)? ¿`LocalDate` solo para fechas de calendario sin hora?
- [ ] ¿Conjunto cerrado de valores como enum (app + `CHECK` + contrato, §8.4)? ¿Documento/teléfono como `VARCHAR`, nunca numérico?
- [ ] ¿Pruebas por scenario según `docs/testing.md`; `./mvnw verify` verde?

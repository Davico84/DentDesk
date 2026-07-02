# Estándares de Backend — OdontoRisas

Parte de la documentación de OdontoRisas. Estándares de código y convenciones del backend **Java 25 · Spring Boot 4.0.6**.
Complementa a `docs/architecture.md` (arquitectura por capas), `docs/testing.md` (pruebas: slices + Testcontainers) y `docs/commits.md`.
El equivalente de frontend es `docs/coding-standards.md`.

---

## 1. Arquitectura por capas

Flujo de dependencias **estricto** (una capa solo conoce la de abajo):

```
presentation  →  infra  →  persistence
```

| Capa | Paquete | Responsabilidad | NO debe |
|---|---|---|---|
| Presentación | `presentation.controller`, `presentation.dto` | Recibir HTTP, validar request (DTO + Bean Validation), delegar en services, construir respuesta | Contener lógica de negocio ni acceder a `persistence`/entidades |
| Infraestructura | `infra.config`, `infra.security`, `infra.service.<dominio>`, `infra.utils`, `infra.mappers` | Lógica de negocio, transacciones, seguridad, mapeo DTO↔entidad, configuración | Exponer entidades JPA hacia fuera |
| Persistencia | `persistence.entity`, `persistence.repository`, `persistence.specification` | Entidades JPA, repositorios Spring Data, queries dinámicas (Specifications) | Contener reglas de negocio |

**Reglas de oro:**
- `presentation` **nunca** importa de `persistence` (ni entidades ni repositorios). Habla con `infra.service`.
- Las **entidades JPA no salen de la capa `persistence`**: se convierten a DTO en `infra.mappers` antes de llegar a `presentation`.
- Los controllers son **delgados**: sin lógica; orquestan y delegan.
- Un `<dominio>` (capacidad) tiene su paquete de servicio en `infra.service.<dominio>` (ver mapeo en `docs/capabilities.md`).

---

## 2. Convenciones de nombres

- **Idioma**: identificadores en **inglés** (convención Java); Javadoc/comentarios en español; mensajes de log/excepción en inglés. Elige un idioma para los términos de dominio y sé consistente en todo el backend.
- Clases/Interfaces: `PascalCase`. Métodos/variables: `camelCase`. Constantes: `UPPER_SNAKE_CASE`. Paquetes: `minúscula.sin.guiones`.
- **Sufijos por rol** (obligatorios):

| Elemento | Sufijo | Ejemplo |
|---|---|---|
| Controller | `Controller` | `AppointmentController` |
| Service (interfaz) | `Service` | `AppointmentService` |
| Service (impl) | `ServiceImpl` | `AppointmentServiceImpl` |
| Repositorio | `Repository` | `AppointmentRepository` |
| Entidad | — (sustantivo) | `Appointment` |
| DTO | `Request`/`Response`/`Dto` | `CreateAppointmentRequest`, `AppointmentResponse` |
| Mapper | `Mapper` | `AppointmentMapper` |
| Specification | `Specification`/`Specs` | `AppointmentSpecifications` |
| Excepción | `Exception` | `AppointmentNotFoundException` |

---

## 3. SOLID y DRY (aplicado a Spring)

- **SRP**: una clase, una razón de cambio. El controller enruta, el service decide, el repository persiste.
- **OCP/DIP**: depende de **abstracciones**. Los services que lo ameriten se definen como interfaz + impl; inyecta la interfaz.
- **ISP**: interfaces pequeñas y enfocadas.
- **DRY**: centraliza validación/reglas repetidas en el service o en validadores; no dupliques mapeos (usa el mapper).

---

## 4. Inyección de dependencias

- **Siempre por constructor** con campos `final` + Lombok `@RequiredArgsConstructor`. **Prohibida** la inyección por campo (`@Autowired` en atributos).

```java
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {
    private final AppointmentRepository repository;
    private final AppointmentMapper mapper;
}
```

---

## 5. Controllers y diseño de API REST

- URLs por **recurso** bajo `/api`, en plural y kebab/lowercase: `/api/appointments`, `/api/appointments/{id}`.
- Métodos HTTP correctos: `GET` (leer), `POST` (crear), `PUT`/`PATCH` (actualizar), `DELETE` (borrar).
- Status codes correctos: `200/201/204`, `400/401/403/404/409`, `500`. Usa `ResponseEntity<T>`.
- **Nunca** recibir/retornar entidades JPA: usa DTOs (`*Request` entrada, `*Response` salida).
- Validación de entrada con `@Valid` sobre el DTO.
- Documenta con **springdoc-openapi** (`@Operation`, `@ApiResponse`) — Swagger habilitado por perfil.

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

---

## 6. DTOs, validación y mapeo

- **DTOs** preferentemente `record` (inmutables). Separa entrada (`*Request`) de salida (`*Response`).
- **Bean Validation** en los `*Request`: `@NotNull`, `@NotBlank`, `@Email`, `@Size`, `@Positive`, etc. La validación de **formato** vive en el DTO; la de **reglas de negocio** en el service.
- **Mapeo DTO↔entidad** en `infra.mappers` (MapStruct recomendado, o mappers manuales). El mapeo no vive en controllers ni entidades.

```java
public record CreateAppointmentRequest(
    @NotNull Long patientId,
    @NotNull @Future LocalDateTime scheduledAt,
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

## 9. Migraciones (Flyway)

- Única fuente del esquema: `spring.jpa.hibernate.ddl-auto=none`. Hibernate **no** crea ni altera tablas.
- Migraciones en `src/main/resources/db/migration`, nombre `V<n>__descripcion_snake.sql` (p. ej. `V1__init.sql`, `V2__add_appointments.sql`).
- Una migración **aplicada es inmutable**: nunca la edites; crea una nueva.
- Revisar cada script antes de mergear.

---

## 10. Manejo de errores

- Manejo **centralizado** con `@RestControllerAdvice` + `@ExceptionHandler`.
- Excepciones de dominio propias (p. ej. `AppointmentNotFoundException`) mapeadas a status HTTP.
- **Formato de error consistente** y alineado con el frontend (ver contrato en `docs/architecture.md §4`). Formato base:

```json
{ "error": { "message": "Appointment not found", "code": "NOT_FOUND" } }
```

- No filtrar detalles internos (stack traces) en la respuesta; sí loguearlos.

---

## 11. Seguridad

- **Spring Security + JWT** (`com.auth0:java-jwt`). Configuración en `infra.security`.
- Contraseñas con hashing fuerte (BCrypt). **Nunca** en texto plano ni en logs.
- Autorización por rol (`ADMIN`/`USER`) con seguridad a nivel de método (`@PreAuthorize`) y/o `SecurityFilterChain`.
- **Sin secretos en el código ni en git**: viven en `application-dev-secrets.yml` / variables de entorno (ignorados), con `*.example` versionado.
- Validar y sanear toda entrada; los DTOs + Bean Validation son la primera barrera.

---

## 12. Configuración

- **Configuración única** (sin perfiles `dev/qa/pro`). Un solo `application.yml` con placeholders `${VAR}` para **todos** los valores configurables.
- **Todo externalizado como secreto**: los valores se inyectan por variables de entorno; en local se cargan de `secrets.properties` (ignorado por git) vía `spring.config.import=optional:file:./secrets.properties`. Se versiona solo `secrets.properties.example`.
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
- Persistencia/Specifications/Flyway → `@DataJpaTest`/`@SpringBootTest` + **Testcontainers (PostgreSQL)**.
- Nombre de test referenciando el scenario del spec; patrón AAA.
- `./mvnw -B verify` en verde (requiere Docker).

---

## 16. Workflow y calidad

- Commits: Conventional Commits en español (`docs/commits.md`).
- Antes de PR: `./mvnw -B verify` (compila + tests) en verde; sin warnings sin resolver.
- Ramas de feature pequeñas y enfocadas.

---

## 17. Checklist de pre-entrega (backend)

- [ ] ¿Respeta el flujo de capas (presentación no toca persistencia; entidades no salen de persistence)?
- [ ] ¿Controllers delgados y con DTOs (`*Request`/`*Response`), sin exponer entidades?
- [ ] ¿Validación de formato en DTO (Bean Validation) y de negocio en el service?
- [ ] ¿Inyección por constructor (`@RequiredArgsConstructor`), sin inyección por campo?
- [ ] ¿Transaccionalidad correcta (`@Transactional`, `readOnly` en lecturas)?
- [ ] ¿Sin N+1 (fetch join/`@EntityGraph`), consultas dinámicas vía `Specification`?
- [ ] ¿Cambios de esquema como nueva migración Flyway (no editar aplicadas)?
- [ ] ¿Errores centralizados con formato consistente; sin secretos en logs?
- [ ] ¿Pruebas por scenario según `docs/testing.md`; `./mvnw verify` verde?

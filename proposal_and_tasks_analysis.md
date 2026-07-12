# Análisis y Sugerencias de Mejora: Propuesta y Plan de Tareas de Multi-Tenancy

Este documento presenta una revisión crítica de la propuesta ([proposal.md](file:///d:/proyectos/drivenSpec/OdontoRisas/Odontorisas/openspec/changes/add-multi-tenancy/proposal.md)) y del plan de tareas ([tasks.md](file:///d:/proyectos/drivenSpec/OdontoRisas/Odontorisas/openspec/changes/add-multi-tenancy/tasks.md)) para la implementación de multi-tenancy en OdontoRisas. Se identifican inconsistencias y se proponen mejoras específicas para asegurar un plan de ejecución robusto y sin lagunas.

---

## 1. Alineación de Inconsistencias (Seguridad y Roles)

> [!IMPORTANT]
> **Inconsistencia Detectada:**
> * El archivo `proposal.md` (sección *Impact*) y `design.md` (sección *D7*) mencionan el uso de un **único usuario de base de datos** (`DB_USERNAME`) y la obligatoriedad de `FORCE ROW LEVEL SECURITY`.
> * Sin embargo, el plan en `tasks.md` (tareas **1.0**, **1.1** y **1.6**) define un esquema mucho más seguro con **dos usuarios de BD** (`odontorisas_admin` para DDL/Flyway y `odontorisas_app` para DML en runtime).
>
> **Sugerencia:**
> Debe confirmarse y alinearse la decisión de usar **dos roles de base de datos**. Es necesario actualizar `proposal.md` y `design.md` para reflejar esta arquitectura de dos roles, ya que es el estándar recomendado para evitar escaladas de privilegios y simplificar las migraciones.

---

## 2. Vacío de Implementación: Propagación de Contexto Asíncrono

> [!WARNING]
> **Omisión en las Tareas:**
> La tarea **7.4** de documentación menciona una *"convención async (D13: TaskDecorator para async-de-request...)"*. Sin embargo, **no existe ninguna tarea de desarrollo en las secciones 2 o 3 para implementar este decorador ni configurar el pool de tareas asíncronas de Spring.**
> 
> Si se omitiera la implementación de este `TaskDecorator`, cualquier llamada anotada con `@Async` o ejecución paralela perdería el contexto del tenant, resultando en excepciones ruidosas o consultas vacías en segundo plano.

### Tarea a Añadir en `tasks.md`:
Bajo la sección **"3. Backend — mecanismo de aislamiento"**, se debe insertar la siguiente tarea:
- [ ] **3.7 Decorador de Contexto Asíncrono:** Implementar `common/TenantContextTaskDecorator.java` que copie `IdentityContext` y `OrganizationContext` a los hilos hijos. Configurar un bean de tipo `Executor` (`ThreadPoolTaskExecutor`) que aplique este decorador para todas las operaciones `@Async` de Spring.

---

## 3. Robustez de RLS: Uso de `missing_ok` en `current_setting`

> [!NOTE]
> **Riesgo:**
> La tarea **1.7** define la política RLS de `memberships` usando `current_setting('app.user_id')::bigint` sin el parámetro `missing_ok = true` (es decir, fallando ruidosamente a nivel de base de datos si la variable no está fijada).
> 
> Aunque esto es seguro, provoca que cualquier consulta del sistema, script de administración, o herramienta externa que acceda a la tabla sin haber inicializado la sesión lance una excepción de Postgres SQL.

### Sugerencia:
Modificar la política en `V7__rls.sql` para usar `current_setting('app.user_id', true)::bigint`.
Al pasar `true` como segundo argumento, Postgres devuelve `NULL` en lugar de fallar. En SQL, `user_id = NULL` se evalúa como `UNKNOWN/FALSE`, manteniendo el comportamiento seguro por defecto (*fail-closed* o denegación por defecto) pero **evitando excepciones de base de datos** para consultas fuera del flujo de aplicación ordinario.

---

## 4. Gestión de Errores y Traducción de Excepciones en el Registro

> [!IMPORTANT]
> **Riesgo:**
> Durante el registro autoservicio (tarea **4.3**), se crean simultáneamente un `User` y una `Organization`. El nombre de la organización se convierte en un `slug` inmutable y único.
> 
> Si dos clínicas intentan registrarse con nombres que generen el mismo `slug` (o si hay una colisión no resuelta), la base de datos arrojará una excepción de clave duplicada (`DataIntegrityViolationException`). Sin un manejador adecuado, esto se convertirá en un error `500 Internal Server Error` genérico.

### Tarea a Añadir en `tasks.md`:
Bajo la sección **"4. Backend — autenticación..."**:
- [ ] **4.9 Manejo de Excepciones de Restricción:** Capturar `DataIntegrityViolationException` en el flujo de registro (en `AuthService` o a través de un `@ControllerAdvice`) y traducirlo a códigos de estado HTTP semánticos (ej. `409 Conflict` con un código de error específico como `CLINIC_SLUG_ALREADY_EXISTS` o `EMAIL_ALREADY_EXISTS`), facilitando al frontend mostrar un mensaje útil.

---

## 5. Rehidratación del Estado del Frontend al Recargar la Página

> [!TIP]
> **Sugerencia para el Frontend:**
> La tarea **5.3** detalla la creación de un Zustand store para la sesión que exponga la organización activa y el rol. 
> 
> Es crítico asegurar que al recargar la página (F5), el frontend realice el flujo de rehidratación llamando al endpoint de `/auth/refresh` o descodificando el access token recuperado, poblando nuevamente el Zustand store *antes* de que el `RouteGuard` evalúe los permisos de la ruta.

### Detalle a añadir en la tarea 5.3:
* Asegurar que el flujo de inicialización del frontend ejecute de forma transparente el refresco de token y repueble el store para evitar que el usuario sea redirigido al login por pérdida de estado en memoria.

---

## Resumen de Modificaciones Recomendadas para `tasks.md`

A continuación se muestra el bloque de tareas de **Backend** y **Cierre** enriquecidos con las mejoras propuestas:

```diff
 ## 3. Backend — mecanismo de aislamiento (design.md D6, D7, D10)
 ...
 - [ ] 3.5 **Regla transaccional (design.md D12)**: test ArchUnit que exija `@Transactional` en el acceso a entidades bajo RLS (`memberships` y futuras de negocio)
 - [ ] 3.6 Convención de RLS por tabla de negocio: helper/plantilla SQL reutilizable (`ENABLE`/`FORCE` + policy `organization_isolation`) documentada para las capacidades de negocio
++ - [ ] 3.7 **Decorador de Contexto Asíncrono (Propagación):** Implementar `TenantContextTaskDecorator` y registrar un `ThreadPoolTaskExecutor` en Spring para propagar `IdentityContext` y `OrganizationContext` a hilos `@Async`.
 
 ## 4. Backend — autenticación con identidad y organización activa
 ...
 - [ ] 4.8 **MDC (design.md D9)**: `JwtAuthenticationFilter` añade `organizationId`/`userId` al MDC junto al `traceId` y los retira en el `finally`
++ - [ ] 4.9 **Traducción de Excepciones de DB:** Manejo de colisiones en el registro (`DataIntegrityViolationException` por email/slug duplicado) convirtiéndolas en respuestas `409 Conflict` amigables.
```

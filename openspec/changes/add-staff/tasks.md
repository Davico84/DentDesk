> Commits separados por scope (`docs/commits.md`): backend · contrato + cliente generado · frontend · docs.
> Antes de tocar el frontend, skill `frontend-guard`. Tests derivados de los Scenario del spec `staff`.
> Revisión de Codex (2026-10-15) incorporada: concurrencia (design D7), orden contrato → `mvn verify`, sin
> `frontend-design` en pantallas autenticadas, 204 en revocar y cambiar estado, `manageable` en el DTO.

## 1. Backend: errores tipados y reglas existentes

- [x] 1.1 Excepciones `BusinessException` del módulo (design D4): `invitation-already-pending` (409, mensaje "pendiente o vencida" que sugiere reenviar), `invitation-not-found` (404), `invitation-not-pending` (409), `cannot-change-own-status` (409), `member-not-found` (404)
- [x] 1.2 `StaffService.invite`: invitación pendiente o vencida para el mismo correo → `invitation-already-pending` (hoy 500)
- [x] 1.3 `StaffService.revoke`: id inexistente o de otra clínica → 404; solo `MEMBER` en `PENDING` (vencida incluida); `ADMIN` → 403; aceptada o revocada → 409 (design D3); el endpoint responde `204`
- [x] 1.4 `StaffService.changeStatus`: propio → 409 y no miembro → 404 (hoy 500); se mantiene el 403 sobre un `ADMIN`; el endpoint responde `204`

## 2. Backend: listado, reenvío y concurrencia

- [x] 2.1 `InvitationRepository.findByOrganizationIdOrderByCreatedAtDesc` y `GET /staff/invitations`: estado derivado `EXPIRED` con el `Clock` inyectado (design D1); DTO sin token ni código, con `id`, correo, rol, estado, `createdAt`, `expiresAt` y `manageable`
- [x] 2.2 `InvitationRepository.findByIdForUpdate` y `findByTokenHashForUpdate` (`@Lock(PESSIMISTIC_WRITE)`), y `consume()` con `token_hash` en la condición (design D7)
- [x] 2.3 `POST /staff/invitations/{id}/resend` (design D2): carga con bloqueo como primera lectura, comprueba estado después, token, código y vigencias nuevos en la misma fila, intentos a 0, `verifiedAt` nulo; `saveAndFlush` y correo dentro de la transacción; mismas reglas que revocar (D3); `revoke` también carga con bloqueo
- [x] 2.4 `StaffService.verifyEmail` y `consume` usan la carga con bloqueo / la condición por hash (design D7), sin cambiar su comportamiento observable
- [x] 2.5 OpenAPI de los endpoints nuevos y de las respuestas nuevas de los existentes (200, 204, 404, 409, 503), con `operationId` únicos
- [x] 2.6 Regenerar `contracts/openapi.json` **antes** de correr `mvn verify` (lo exige `ContractDriftIT`)

## 3. Backend: tests (Scenario → test)

- [x] 3.1 IT de acceso: `MEMBER` 403, visitante 401, sesión de plataforma 401, y aislamiento entre dos clínicas (listar y actuar sobre recursos de otra → 404)
- [x] 3.2 IT de invitar: emitida (sin token en la respuesta), ya miembro 409, pendiente duplicada 409, vencida 409 con su mensaje, correo que falla → 503 y nada creado
- [x] 3.3 IT del listado: estados pendiente, aceptada, revocada y vencida (expiración forzada en BD), `manageable`, orden y ausencia de token/código
- [x] 3.4 IT de reenviar: código vencido → correo nuevo y registro posible con él; enlace y código anteriores rechazados; vencida vuelve a pendiente; aceptada/revocada 409; `ADMIN` 403; fallo de correo deja la invitación con su estado, enlace y código previos
- [x] 3.5 IT de revocar: pendiente → revocada (204) y su enlace inválido; aceptada 409; `ADMIN` 403
- [x] 3.6 IT de estado: desactivar (204) impide login y refresh y conserva la autoría de sus registros; reactivar devuelve el acceso; propio 409; otro `ADMIN` 403; no miembro 404
- [x] 3.7 IT de concurrencia con hilos reales y orden forzado (`awaitLockWaitOrFinished`), primero contra el código sin el fix: reenvío contra registro, verificación contra reenvío y revocación contra registro; nunca una aceptada vuelve a pendiente ni un enlace anulado registra
- [x] 3.8 `mvn verify` verde

## 4. Cliente generado

- [x] 4.1 Regenerar el cliente orval (`pnpm --filter dentdesk-frontend generate:api`) y commitear contrato + cliente aparte del código

## 5. Frontend

- [x] 5.1 Componer la pantalla con el sistema de diseño existente (`docs/frontend.md` §4, §4.1–§4.6) — sin `frontend-design`, que no aplica a pantallas autenticadas — y mostrar al usuario una captura antes de commitear
- [x] 5.2 `core/ui`: `pnpm dlx shadcn@latest add tabs`; `globals.css` intacto. **Sin `badge`**: el proyecto usa píldoras de dominio con tokens (patrón `PatientStatusBadge`), y un badge de shadcn rompería esa consistencia
- [x] 5.3 Hooks en `modules/staff/hooks/useStaffApi.ts`: claves `["staff","members"]` y `["staff","invitations"]`; invitar, reenviar, revocar y cambiar estado, con invalidaciones y toasts (design D6)
- [x] 5.4 Handlers de MSW para los endpoints de `staff` (MSW corre con `onUnhandledRequest: "error"`). En `modules/staff/test/staffApiMocks.ts` y no en `handlers.ts` global: el repo no registra handlers globales, cada test usa `server.use`
- [x] 5.5 Schema Zod del formulario de invitación en `modules/staff/schemas/` (paridad con el `@Email` del DTO) e `InviteStaffDialog`
- [x] 5.6 `MembersTab`: tabla con rol y `MemberStatusBadge`, búsqueda sin tildes ni mayúsculas, desactivar/reactivar con `ConfirmDialog`, sin acciones sobre la propia fila ni sobre un `ADMIN`; estados de carga, error y vacío. Cada fila, badge o estado vacío en su propio archivo (`react/no-multi-comp`)
- [x] 5.7 `InvitationsTab`: tabla con `InvitationStatusBadge`, filtro por estado con contadores, reenviar y revocar (con confirmación) solo si `manageable`; estado vacío distinto de "filtro sin resultados". Un componente por archivo
- [x] 5.8 `StaffManagement` con pestañas en la URL (`?tab=`) y botón Invitar común; `StaffSettingsScreen` en una línea
- [x] 5.9 Tests Vitest/MSW de cada Scenario visible: búsqueda (incl. tildes), filtro y contadores, confirmaciones (aceptar y cancelar), acciones ocultas en la propia fila y en invitaciones no gestionables, errores 409 mostrados, formulario inválido sin petición
- [ ] 5.10 `pnpm validate` verde y prueba manual en local (invitar, reenviar con código vencido, revocar, desactivar y reactivar)

## 6. Docs y cierre

- [ ] 6.1 `docs/domain.md`: estado derivado "vencida", reenvío y concurrencia de `Invitation`; `docs/vision.md`: `staff` en progreso → construida al archivar
- [ ] 6.2 `openspec validate add-staff --strict` y medir a mano que ningún requirement supere 500 caracteres

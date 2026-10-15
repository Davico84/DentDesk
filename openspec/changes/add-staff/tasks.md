> Commits separados por scope (`docs/commits.md`): backend · contrato + cliente generado · frontend · docs.
> Antes de tocar el frontend, skill `frontend-guard`. Tests derivados de los Scenario del spec `staff`.

## 1. Backend: errores tipados y reglas existentes

- [ ] 1.1 Excepciones `BusinessException` del módulo (design D4): `invitation-already-pending` (409), `invitation-not-found` (404), `invitation-not-pending` (409), `cannot-change-own-status` (409), `member-not-found` (404)
- [ ] 1.2 `StaffService.invite`: invitación pendiente duplicada → `invitation-already-pending` (hoy 500)
- [ ] 1.3 `StaffService.revoke`: id inexistente o de otra clínica → 404; solo `MEMBER` en `PENDING` (vencida incluida); `ADMIN` → 403; aceptada o revocada → 409 (design D3)
- [ ] 1.4 `StaffService.changeStatus`: propio → 409 y no miembro → 404 (hoy 500); se mantiene el 403 sobre un `ADMIN`

## 2. Backend: listado y reenvío de invitaciones

- [ ] 2.1 `GET /staff/invitations`: invitaciones de la organización activa, más recientes primero, con estado derivado `EXPIRED` (design D1); DTO sin token ni código, con `id`, correo, rol, estado, `createdAt`, `expiresAt` y si es gestionable desde la clínica
- [ ] 2.2 `POST /staff/invitations/{id}/resend` (design D2): token, código y vigencias nuevos en la misma fila, intentos a 0, `verifiedAt` nulo; `saveAndFlush` y correo dentro de la transacción; mismas reglas que revocar (D3)
- [ ] 2.3 OpenAPI de los endpoints nuevos y de los errores nuevos de los existentes (201/200/204, 404, 409, 503), con `operationId` únicos

## 3. Backend: tests (Scenario → test)

- [ ] 3.1 IT de acceso: `MEMBER` 403, visitante 401, token de plataforma 401, y aislamiento entre dos clínicas (listar y actuar sobre recursos de otra → 404)
- [ ] 3.2 IT de invitar: emitida (sin token en la respuesta), ya miembro 409, pendiente duplicada 409, correo que falla → 503 y nada creado
- [ ] 3.3 IT del listado: estados pendiente, aceptada, revocada y vencida (expiración forzada en BD), orden y ausencia de token/código
- [ ] 3.4 IT de reenviar: código vencido → correo nuevo y registro posible con él; enlace y código anteriores rechazados; vencida vuelve a pendiente; aceptada/revocada 409; `ADMIN` 403; fallo de correo deja válidos los anteriores
- [ ] 3.5 IT de revocar: pendiente → revocada y su enlace inválido; aceptada 409; `ADMIN` 403
- [ ] 3.6 IT de estado: desactivar impide login y refresh y conserva la autoría de sus registros; reactivar devuelve el acceso; propio 409; otro `ADMIN` 403; no miembro 404
- [ ] 3.7 `mvn verify` verde

## 4. Contrato

- [ ] 4.1 Regenerar `contracts/openapi.json` y el cliente orval (`pnpm --filter dentdesk-frontend generate:api`); commit aparte

## 5. Frontend

- [ ] 5.1 Diseño visual de la pantalla (pestañas, tablas, badges de estado, estados vacíos, acciones) con la skill `frontend-design`, aprobado por el usuario antes de codear
- [ ] 5.2 `core/ui`: `pnpm dlx shadcn@latest add tabs badge`; verificar con `git diff` que no tocó `globals.css`
- [ ] 5.3 Hooks en `modules/staff/hooks/useStaffApi.ts`: claves `["staff","members"]` y `["staff","invitations"]`; invitar, reenviar, revocar y cambiar estado, con invalidaciones y toasts (design D6)
- [ ] 5.4 Schema Zod del formulario de invitación en `modules/staff/schemas/` (paridad con el `@Email` del DTO) e `InviteStaffDialog`
- [ ] 5.5 `MembersTab`: tabla con rol y `MemberStatusBadge`, búsqueda sin tildes ni mayúsculas, desactivar/reactivar con `ConfirmDialog`, sin acciones sobre la propia fila ni sobre un `ADMIN`; estados de carga, error y vacío
- [ ] 5.6 `InvitationsTab`: tabla con `InvitationStatusBadge`, filtro por estado con contadores, reenviar y revocar (con confirmación) solo en `MEMBER` pendientes o vencidas; estado vacío distinto de "filtro sin resultados"
- [ ] 5.7 `StaffManagement` con pestañas en la URL (`?tab=`) y botón Invitar común; `StaffSettingsScreen` en una línea
- [ ] 5.8 Tests Vitest/MSW de cada Scenario visible: búsqueda (incl. tildes), filtro y contadores, confirmaciones (aceptar y cancelar), acciones ocultas en la propia fila y en invitaciones `ADMIN`, errores 409 mostrados, formulario inválido sin petición
- [ ] 5.9 `pnpm validate` verde y prueba manual en local (invitar, reenviar con código vencido, revocar, desactivar y reactivar)

## 6. Docs y cierre

- [ ] 6.1 `docs/domain.md`: estado derivado "vencida" y reenvío de `Invitation`; `docs/vision.md`: `staff` en progreso → construida al archivar
- [ ] 6.2 `openspec validate add-staff --strict` y medir a mano que ningún requirement supere 500 caracteres

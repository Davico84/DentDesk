# Tasks

## 1. Backend — modelo y correo

- [ ] 1.1 Migración Flyway `password_reset_tokens` (design D2): columnas, `token_hash` único, índice `(user_id, created_at)`, FK a `users` con `ON DELETE CASCADE`, sin RLS, grants condicionales a `odontorisas_app` como V26/V30 — *verifica: `mvn verify` aplica la migración y `DbRolesScriptIT` sigue en verde*
- [ ] 1.2 Entidad y repositorio: emitir (anular vigentes, borrar los de más de 24 h, insertar), contar los de las últimas 24 h, leer por hash sin bloqueo y `FOR UPDATE`, marcar usado y anular los demás — *verifica: compila*
- [ ] 1.3 `UserRepository`: `FOR UPDATE` de la fila **sin** el filtro de "no bloqueado" y `resetPassword` en bloque que conserva `locked_until` y el contador si el bloqueo es futuro, y si no los pone a cero (design D5.2, D5.5) — *verifica: compila*
- [ ] 1.4 Correo (design D4, D6): interfaz `AccountMailer` (enlace de recuperación; aviso de cambio con fecha y hora de Perú y qué hacer), adaptador SMTP con el mismo `MailProperties` y textos en español firmados por Dentdesk; variante deshabilitada que registra y descarta; `mailExecutor` dedicado (2 hilos, cola acotada, descarte con log, cierre ordenado); entrega por `afterCommit`; logs de fallo sin token, enlace ni contraseña. Tests del `compose` de ambos correos: el enlace apunta a `/auth/reset-password?token=` del frontend y el aviso no lleva enlaces que cambien la contraseña — *verifica: test unitario*
- [ ] 1.5 Regla ArchUnit: `infra.mail` no depende de `persistence` ni de `IdentityContext`/`OrganizationContext` (design D4) — *verifica: el test de arquitectura pasa y falla si se agrega esa dependencia*

## 2. Backend — pedido y restablecimiento

- [ ] 2.1 `POST /auth/password-recovery` (design D1, D3): `202` siempre; normaliza el correo; sin envío si no existe, no está `ACTIVE` o es administrador de plataforma; `FOR UPDATE` de `users`; límite de 3 en 24 h; correo después del commit. `@ApiResponses` con el 202 y el 400 de formato — *verifica: compila y aparece en `/v3/api-docs`*
- [ ] 2.2 `POST /auth/password-reset/check` y `POST /auth/password-reset` (design D1, D5) con el orden `users` → `password_reset_tokens` → `refresh_tokens`: `400 invalid-reset-link` único para inválido, vencido, usado, anulado o de plataforma; un `400` de validación no consume el enlace; borrar todos los refresh tokens sin emitir sesión; `outcome` `OK` · `LOCKED` (`lockedUntil`) · `ACCESS_DISABLED`; aviso después del commit. Excepción en `GlobalExceptionHandler` con `detail` en español — *verifica: compila y aparece en el contrato*
- [ ] 2.3 Los tres endpoints en la lista explícita de `PublicPaths`; actualizar `PublicPathsTest` — *verifica: el test pasa*
- [ ] 2.4 Aviso de cambio en `POST /auth/password` de Mi perfil (design D6), después del commit; un cambio rechazado no avisa — *verifica: `ChangePasswordIT` afirma el aviso en el éxito y su ausencia en los rechazos*
- [ ] 2.5 IT del pedido, derivado de *Pedir la recuperación de la contraseña*, *Límite de enlaces de recuperación* y *Recuperación excluida para administradores de plataforma*: correo registrado (envía, una sola vez y a ese correo); no registrado, identidad desactivada y plataforma (incluido plataforma que también es miembro): misma respuesta, sin envío; mayúsculas y espacios; formato inválido → 400; cuenta sin membresías activas sí recibe; 3.º envía, 4.º no; pasadas 24 h (reloj fijo) vuelve a enviar — *verifica: el IT pasa*
- [ ] 2.6 IT del restablecimiento, derivado de *Enlace de recuperación*, *Restablecer la contraseña con el enlace*, *Cerrar todas las sesiones al restablecer la contraseña* y *Estado de la cuenta al restablecer la contraseña*: vigente (check 204; login con la nueva sí, con la anterior no); vencido a los 30 min; usado; anulado por uno nuevo (el nuevo sigue valiendo); nueva corta → 400 sin consumir; todas las sesiones dejan de renovarse y no hay `Set-Cookie` de sesión nueva; bloqueada → `LOCKED` con la hora y el bloqueo intacto; identidad desactivada después del pedido y sin membresía activa → `ACCESS_DISABLED`; sin bloqueo → contador a cero; enlace emitido antes de pasar a plataforma → 400; aviso enviado solo en el éxito; un fallo del mailer no revierte el cambio — *verifica: el IT pasa*
- [ ] 2.7 IT de concurrencia (design D5), con hilos reales, `ExecutorService` propio y los puntos nuevos de `AuthConcurrencyProbe`: (a) dos restablecimientos con el mismo enlace → uno gana y el otro 400; (b) cuatro pedidos simultáneos de la misma cuenta con 0 previos → exactamente 3 envíos; (c) pedido de un enlace nuevo contra restablecimiento con el anterior → sin deadlock (timeout acotado) y estado consistente; (d) renovación de otra sesión contra restablecimiento → ningún refresh sobrevive. Comprobar que (a) y (b) **fallan sin el `FOR UPDATE`** y anotarlo — *verifica: el IT pasa y (a)/(b) fallan sin él*
- [ ] 2.8 Test con el executor real y un `AccountMailer` lento: `POST /auth/password-recovery` responde sin esperar el envío — *verifica: el test pasa con un tiempo acotado*
- [ ] 2.9 Regenerar `contracts/openapi.json` (conservar `"url":"http://localhost:8093/api"`) y el cliente orval — *verifica: `git diff` del contrato muestra solo lo nuevo*

## 3. Frontend — pantallas públicas

- [ ] 3.1 Schemas Zod en `modules/auth/schemas/`: pedido (el `emailSchema` del login) y restablecimiento (`newPasswordSchema` + confirmación); tests del schema — *verifica: Vitest*
- [ ] 3.2 Hooks de React Query: pedido, check y restablecimiento; el restablecimiento limpia la sesión en memoria si hubiera una — *verifica: typecheck*
- [ ] 3.3 `ForgotPasswordForm` (mensaje neutro único tras enviar) y `ResetPasswordForm` (token leído una vez y URL reemplazada sin la query; check al abrir con estado de carga; enlace inválido con botón para pedir otro; navega al login con el aviso según `outcome`) en `modules/auth/components/`; screens de una línea; rutas en `paths.ts` dentro del layout del login (design D7) — *verifica: render en el navegador*
- [ ] 3.4 `LoginForm`: "¿Olvidaste tu contraseña?" pasa a `Link` accesible por teclado; el login muestra el aviso recibido del restablecimiento (`OK`, `LOCKED` con HH:MM, `ACCESS_DISABLED`) — *verifica: test del login*
- [ ] 3.5 `<meta name="referrer" content="no-referrer">` en `index.html` (design D7) — *verifica: inspección del HTML servido*
- [ ] 3.6 Tests Vitest + MSW derivados de los Scenarios: pedido (mismo mensaje con 202, formato inválido sin petición); restablecimiento (el token desaparece de la URL; enlace inválido; nueva corta y confirmación distinta sin petición; éxito y los tres avisos en el login); el enlace del login lleva a la pantalla de pedido — *verifica: Vitest*

## 4. Verificación y docs

- [ ] 4.1 `mvn verify` y `pnpm --filter dentdesk-frontend validate` en verde — *verifica: los dos comandos*
- [ ] 4.2 `docs/deployment.md`: procedimiento de recuperación del administrador de plataforma (design D8) y verificación del seguimiento de clics de Brevo — *verifica: revisión del responsable*
- [ ] 4.3 Responsable: apagar el seguimiento de clics de Brevo para estos correos, o confirmar que está apagado — *verifica: lo confirma el responsable*
- [ ] 4.4 Prueba manual del responsable con correo real: pedir con un correo registrado y uno no registrado (mismo mensaje, un solo correo recibido); abrir el enlace (la URL queda sin el token); restablecer con otra sesión abierta (cae a más tardar a los 15 min); usar el mismo enlace de nuevo; recibir el aviso de cambio; el aviso de "podrás entrar a las HH:MM" con la cuenta bloqueada — *verifica: lo confirma el responsable*
- [ ] 4.5 `docs/vision.md`: enlazar el change en progreso en el estado de `authentication` — *hecho al proponer*
- [ ] 4.6 `openspec validate update-authentication-password-recovery --strict` y medir a mano los requirements (≤ 500 caracteres) — *verifica: válido*

## Workflow follow-up

- Al archivar: ampliar el Purpose de `authentication` (recuperación de la contraseña y aviso de cambio), marcar el estado en `docs/vision.md` y sumar el enlace de recuperación al glosario de `docs/domain.md` si corresponde.
- Siguiente en el roadmap: la puerta de lectura (`ADR-0016` D2–D4) y el aviso de vencimiento próximo de la credencial.

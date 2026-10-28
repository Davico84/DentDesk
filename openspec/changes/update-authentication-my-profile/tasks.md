# Tasks

## 1. Backend — perfil propio

- [x] 1.1 `GET /me/profile` y `PATCH /me/profile` (design D1, D5): DTOs `ProfileResponse` (`fullName`, `email`) y `ProfileRequest` (solo `fullName`, sin espacios en los extremos, 2–120 caracteres), servicio que siempre usa el usuario de la sesión, `@ApiResponses` con el 400 y el 401 — *verifica: compila y aparece en `/v3/api-docs`*
- [x] 1.2 IT del perfil derivado de los Scenarios de *Ver el perfil propio* y *Corregir el nombre propio*: ver; siempre el propio (con `ADMIN` e intento de indicar otro usuario); sin sesión → 401; sesión de plataforma → 401; corregir; nombre corto, largo o solo espacios → 400; un correo en el cuerpo no cambia el correo; una nota firmada antes conserva el nombre de entonces y una firma nueva lleva el corregido (`ClinicalSignerFixtures`) — *verifica: el IT pasa*

## 2. Backend — cambio de contraseña y sesiones

- [x] 2.1 `POST /auth/password` (design D1, D2, D4) con el protocolo de D3.1: `ChangePasswordRequest` (`currentPassword`, `newPassword` de 8 o más caracteres); verificar **sin bloqueo** (bloqueada → 429 antes de comparar; actual incorrecta → `LoginAttemptService.recordFailure` y 400 `current-password-incorrect`; nueva igual a la actual → 400 `new-password-same-as-current`); después `FOR UPDATE` de la fila como lectura fresca (consulta del repositorio con `PESSIMISTIC_WRITE`, no la entidad ya cargada) y re-chequeo bajo el bloqueo (hash ya cambiado → 400 sin contar el fallo; bloqueada en el medio → 429); si todo es válido: hash nuevo, `failed_login_attempts = 0` y `locked_until = NULL`, `deleteByUser` y tokens nuevos con la membresía de la organización del contexto (activa, si no → 401) y su rol; responde `AuthResponse` más la cookie. **Nunca** llamar a `recordFailure` con la fila bloqueada. Excepciones en `GlobalExceptionHandler` con `detail` en español; `@ApiResponses` completos — *verifica: compila y aparece en el contrato*
- [x] 2.2 Login y renovación en el protocolo (design D3.2, D3.3): `resetLoginAttemptsIfNotLocked` exige además `passwordHash = <el verificado>` (0 filas → 401 genérico, sin tokens); `FOR SHARE` de la fila en `AuthService.refresh` antes de consumir el token; actualizar los Javadoc — *verifica: los ITs existentes de login, bloqueo y renovación siguen en verde sin tocarlos*
- [x] 2.3 IT del cambio de contraseña derivado de los Scenarios de *Cambiar la contraseña propia*: cambio exitoso (login con la nueva sí, con la anterior no); actual incorrecta (400, nada cambia, cuenta como fallo); adivinar hasta el límite (bloquea, y con la correcta sigue rechazando mientras dure); un cambio exitoso deja `failed_login_attempts = 0` y `locked_until = NULL`, también con un bloqueo ya vencido; nueva corta → 400; nueva igual a la actual → 400; sesión de plataforma → 401 — *verifica: el IT pasa*
- [x] 2.4 IT de las sesiones derivado de *Cerrar las otras sesiones al cambiar la contraseña*: el refresh de otra sesión responde 401 tras el cambio; usuario con membresía en dos clínicas (fixture): el cambio desde la clínica A devuelve un access de A y la sesión de B no puede renovarse; la sesión actual renueva con la cookie nueva; el `Set-Cookie` de la respuesta es HttpOnly, con el `Secure`/`SameSite` del entorno y `Path=/api/auth`; un cambio rechazado no cierra ninguna sesión — *verifica: el IT pasa*
- [x] 2.5 IT de concurrencia (design D3), con hilos reales, `ExecutorService` propio e intercalado forzado con latches (punto de espera inyectable solo en tests), no por timing: (a) refresh de otra sesión contra cambio → la otra sesión nunca conserva un refresh válido; (b) login con la contraseña anterior contra cambio → ningún refresh emitido sobrevive; (c) contraseña actual incorrecta contra cambio → ninguna petición se cuelga (timeout acotado en el test) y el fallo queda contado; (d) dos cambios con la misma contraseña actual → uno gana y el otro responde 400. Comprobar que (a) y (b) **fallan sin el bloqueo** y anotarlo — *verifica: el IT pasa con el protocolo y (a)/(b) fallan sin él*
- [x] 2.6 Regenerar `contracts/openapi.json` (conservar `"url":"http://localhost:8093/api"`) y el cliente orval — *verifica: `git diff` del contrato muestra solo lo nuevo*

## 3. Frontend — Mi perfil

- [ ] 3.1 Schemas Zod en `modules/auth/schemas/profile.ts`: nombre (2–120 sin contar los espacios de los extremos) y cambio de contraseña (actual obligatoria, nueva de 8 o más, compartida con `registerSchema`, confirmación que coincide); tests del schema — *verifica: Vitest*
- [ ] 3.2 Hooks de React Query en `modules/auth/hooks/`: perfil (query y mutación que actualiza el `user` de `useSessionStore`) y cambio de contraseña (guarda el access nuevo con `setSession`) — *verifica: typecheck*
- [ ] 3.3 Componentes en `modules/auth/components/`: `ProfileDetailsForm` (correo de solo lectura), `ChangePasswordForm` (limpia los campos y avisa con un toast que las otras sesiones se cerraron; muestra junto al campo los errores 400 y 429 del servidor) y `ProfilePage` con las tres secciones; la credencial de `staff` como tercera sección con el ancla `#credencial`; skeleton de carga — *verifica: render en el navegador*
- [ ] 3.4 Ruta `PATHS.PROFILE` = `/profile` con su screen de una línea; `/profile/credential` redirige a `/profile#credencial`; el menú de la cuenta pasa a "Mi perfil"; `SigningCapacityNotice` enlaza al ancla — *verifica: los tests existentes de enlaces se actualizan y pasan*
- [ ] 3.5 Ayuda del campo "Habilitado hasta": fecha de vigencia de la constancia de habilitación del COP — *verifica: test de `ProfessionalCredentialForm`*
- [ ] 3.6 Tests Vitest + MSW derivados de los Scenarios: ver el perfil; corregir el nombre y verlo en el menú de la cuenta sin recargar; errores del nombre sin petición; cambio de contraseña exitoso (toast, campos limpios, sesión actualizada); actual incorrecta (error junto al campo); bloqueada (mensaje); nueva corta y confirmación distinta sin petición; la redirección desde `/profile/credential` — *verifica: Vitest*

## 4. Verificación y docs

- [ ] 4.1 `mvn verify` y `pnpm --filter dentdesk-frontend validate` en verde — *verifica: los dos comandos*
- [ ] 4.2 Prueba manual del responsable: corregir el nombre y verlo en el menú; cambiar la contraseña con otra sesión abierta en otro navegador, y comprobar que esa sesión cae a más tardar a los 15 min mientras la actual sigue; contraseña actual incorrecta; la credencial dentro de Mi perfil y el enlace desde el aviso de firma — *verifica: lo confirma el responsable*
- [x] 4.3 `docs/vision.md`: enlazar el change en progreso en el estado de `authentication` — *hecho al proponer*
- [ ] 4.4 `openspec validate update-authentication-my-profile --strict` y medir a mano los requirements (≤ 500 caracteres) — *verifica: válido*

## Workflow follow-up

- Al archivar: actualizar el Purpose de `authentication` (agrega el perfil propio y el cambio de contraseña) y marcar el estado en `docs/vision.md`. `docs/domain.md` no cambia (sin modelo nuevo).
- Cerrar la Open Question de la constancia en el design archivado de `update-staff-professional-credential` con una nota que remita a este change.
- Siguiente change: recuperación de contraseña olvidada (`LoginForm.tsx`, "¿Olvidaste tu contraseña?").

## 1. Decisión previa (bloquea el delta de ADR-0005, no el resto)

- [ ] 1.1 Revisión humana: resolver las Open Questions de ADR-0005 (qué campos pasan a obligatorios, si sobrevive la excepción del bebé sin documento, si `fullName` se parte en nombres+apellidos). Solo entonces se añade su delta de spec. **No bloquea los grupos 2–5.**

## 2. Contacto telefónico (backend) — ADR-0010, D1/D2

- [ ] 2.1 Consulta "¿este teléfono ya está en otros pacientes de la clínica activa?" en el repositorio de pacientes, acotada por `organizationId` (respeta el aislamiento). Devuelve al menos un conteo.
- [ ] 2.2 Validación condicional en el DTO/servicio: si no hay teléfono propio, el contacto de emergencia (nombre + teléfono + relación) es obligatorio; si lo hay, opcional. (D2)
- [ ] 2.3 Exponer el aviso de número compartido en la respuesta del alta/edición (o endpoint de verificación), sin bloquear.

## 3. Contacto telefónico (frontend) — ADR-0010, D1/D2

- [ ] 3.1 Regla condicional en `patientSchema` (Zod `superRefine`): contacto de emergencia obligatorio si `phone` vacío; mensaje claro del porqué. Paridad con el backend.
- [ ] 3.2 Aviso **no bloqueante** de teléfono compartido en el formulario, visualmente distinto de un error de campo (deja guardar).

## 4. Ficha existente ante documento duplicado — ADR-0009, D3

- [ ] 4.1 Backend: el `409` `patient-document-taken` (alta y edición) incluye el identificador de la ficha existente de la clínica activa, sin volcar datos innecesarios.
- [ ] 4.2 Frontend: ante ese `409`, ofrecer "Abrir registro existente" (navega a la ficha) además de "Cancelar".

## 5. Tests (uno por Scenario del delta)

- [ ] 5.1 Dos pacientes comparten teléfono → alta completa + aviso no bloqueante.
- [ ] 5.2 Sin teléfono propio y sin contacto de emergencia → rechazo indicando que se requiere.
- [ ] 5.3 Sin teléfono propio pero con contacto de emergencia → alta completa.
- [ ] 5.4 Con teléfono propio y sin contacto de emergencia → alta completa.
- [ ] 5.5 Teléfono repetido en **otra** clínica → sin aviso, sin fuga entre clínicas.
- [ ] 5.6 Conflicto de documento identifica la ficha existente en la respuesta.
- [ ] 5.7 Abrir la ficha existente desde el aviso de conflicto navega a ella.
- [ ] 5.8 Documento en otra clínica no genera conflicto ni revela nada de la otra.

## 6. Cierre (al archivar)

- [ ] 6.1 `docs/vision.md`: enlazar el change bajo `patients` en "Estado de capacidades".
- [ ] 6.2 `docs/domain.md`: registrar la regla de contacto de emergencia (obligatorio si no hay teléfono propio) y el teléfono compartido.
- [ ] 6.3 Marcar en los ADR `0009`/`0010` la parte pendiente como implementada (o enlazar el change archivado).

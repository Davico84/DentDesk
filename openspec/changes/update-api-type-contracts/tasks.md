## 1. Mensaje de error apto para el usuario (backend)

- [ ] 1.1 `GlobalExceptionHandler`: añadir el mapa `HttpStatus → detail en español` (400, 404, 405, 415, 406, 413) más una constante de mensaje genérico para los statuses no mapeados. (D1)
- [ ] 1.2 En el override de `handleExceptionInternal`, reemplazar el `detail` del `ProblemDetail` por el del mapa (o el genérico) **antes** de devolverlo, junto a las extensiones que ya añade. (D1)
- [ ] 1.3 Verificar que los `@ExceptionHandler` propios (`handleBusiness`, `handleAuthentication`, `handleAccessDenied`, `handleDataIntegrity`, `handleUncaught`) **no** cambian: no pasan por `handleExceptionInternal` y ya construyen su `detail` curado. (D2)

## 2. Corrección de documentación desactualizada

- [ ] 2.1 `ProblemDetails.java`: corregir el Javadoc que afirma que `instance` no se emite — Spring lo rellena solo al renderizar. (D3)
- [ ] 2.2 `ErrorContractIT.java`: corregir el comentario de clase (`API_PROBLEM_FIELDS`) con la misma afirmación, que ya se contradice con el comentario correcto del test de validación en el mismo archivo. (D3)

## 3. Tests backend (uno por Scenario del delta)

- [ ] 3.1 `ErrorContractIT`: un error resuelto por el framework (JSON malformado) trae un `detail` en español y **no** el mensaje interno de Spring (`"Failed to read request"`).
- [ ] 3.2 `ErrorContractIT`: un status resuelto por el framework y no mapeado cae en el mensaje genérico, sin filtrar texto interno.
- [ ] 3.3 Confirmar que los tests existentes de forma de error (`ErrorContractIT`, `GlobalExceptionHandlerTest`) siguen verdes: este change cambia el **contenido** de `detail`, no la forma ni el status.

## 4. Frontend

- [ ] 4.1 Sin cambios: `getUserFriendlyError` ya consume `detail` correctamente (`docs/frontend.md §2.6`). Confirmar que no hace falta tocarlo.

## 5. Cierre (al archivar)

- [ ] 5.1 `docs/vision.md`: enlazar el change archivado bajo `api-type-contracts` en "Estado de capacidades".
- [ ] 5.2 `docs/backend.md §10`: dejar constancia de que el `detail` apto para el usuario es una garantía de **todos** los errores, incluidos los del manejador por defecto.

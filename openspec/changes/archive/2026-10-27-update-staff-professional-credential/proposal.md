# Proposal

## Why

`ADR-0016` D7 decidió que la capacidad clínica es una **credencial**, no un rol, y `ADR-0018` D6 precisó que la
credencial es la **habilitación** vigente (COP art. 65), no el número de colegiatura. Ninguna de las dos existe
todavía: hoy cualquier miembro de la clínica, incluida la recepción, cierra la admisión y sella notas de
consulta, y de los dos actos de firma solo la nota guarda quién firmó, como un puntero al usuario vivo. Es la
parte pendiente de `staff` y bloquea `ADR-0016` D1–D3; conviene cerrarla ahora, antes de cargar pacientes reales,
para que no crezca lo firmado sin credencial.

## What Changes

- **Credencial profesional del usuario.** Cada profesional declara **él mismo** su número de colegiatura COP y
  la fecha hasta la que está habilitado, con una declaración jurada explícita. Nadie declara la credencial de
  otro. La credencial es de la persona y vale en todas sus clínicas; renovarla es declarar la fecha nueva.
- **La habilitación vence sola.** Pasada la fecha "habilitado hasta" (en la hora de Perú, la del COP que la emite), el
  profesional deja de poder firmar sin que nadie toque nada.
- **Retiro por clínica.** El `ADMIN` puede **retirar** la capacidad clínica de un `MEMBER` en su clínica, y
  restituirla; nunca otorgarla. El retiro vive en la membresía: no afecta a otras clínicas del mismo profesional.
- **Capacidad clínica en el listado del personal.** El `ADMIN` ve, por miembro: sin credencial, habilitado
  hasta tal fecha, habilitación vencida o retirada.
- **Firmar exige capacidad clínica.** Los dos actos de firma —**cerrar la admisión** (Historia Clínica +
  odontograma inicial, en una operación) y **sellar una nota de consulta**— se rechazan con acceso denegado y
  el motivo concreto si quien firma no tiene capacidad clínica en la clínica activa.
- **La credencial se fotografía al firmar** (`ADR-0016` D1, `ADR-0018` D6). La Historia Clínica, el odontograma
  y la nota guardan quién firmó, su nombre, su número COP y su "habilitado hasta" **copiados en ese momento**.
  Hoy la Historia y el odontograma no guardan quién los firmó; pasan a guardarlo.
- **Lo ya sellado no se reescribe.** Lo firmado antes de este cambio queda marcado como *firmado antes del
  registro de credenciales* y se muestra así.

## Non-goals

- **Puerta de lectura y secreto profesional** (`ADR-0016` D2–D3) y la **auditoría de acceso clínico** (D4): van
  en el change siguiente. Mientras tanto, un miembro sin credencial sigue pudiendo **ver y cargar** borradores
  clínicos; lo que no puede es **firmarlos**.
- **Verificación contra el registro de habilitados del COP.** La habilitación sigue siendo **autodeclarada**,
  como declara `ADR-0018` D6; el modelo admite verificarla después sin cambiar de forma.
- **Aviso de vencimiento próximo** ("tu habilitación vence en 7 días"). El rechazo al firmar ya dice que venció
  y cuándo.
- **Historial de declaraciones** de la credencial. Lo que importa conservar —la credencial con que se firmó
  cada acto— queda en la foto de cada registro.
- **Otros actos clínicos que todavía no existen** (recetas, tratamientos, citas): adoptarán esta misma regla
  cuando se construyan.
- **Borrar o regularizar lo ya sellado**: no se toca (libro de actas).

## Capabilities

### New Capabilities

_Ninguna._

### Modified Capabilities

- `staff`: credencial profesional declarada por el propio profesional, capacidad clínica efectiva en la clínica
  activa, retiro y restitución por el `ADMIN`, y estado de la capacidad clínica en el listado del personal.
- `clinical-history`: cerrar la admisión y sellar la nota exigen capacidad clínica, y la Historia y la nota
  guardan la foto de la credencial de quien firmó.
- `dental-chart`: el odontograma inicial sellado guarda la foto de la credencial de quien cerró la admisión.

## Impact

- **Base de datos:** migración nueva con la tabla de credenciales (una por usuario, con RLS propia: el usuario
  escribe solo la suya y el `ADMIN` lee las de los miembros de su clínica, con el patrón de `V24`), el retiro en
  `memberships`, y las columnas de la foto en `historia_clinica`, `dental_chart_block` y `consultation_note`,
  con la marca de *firmado antes del registro de credenciales* para las filas ya selladas.
- **Backend:** servicio de credencial y de capacidad clínica efectiva; endpoints para la credencial propia y
  para retirar/restituir en `/staff/members`; comprobación en `CloseAdmissionService` y en el sellado de la
  nota; errores RFC 9457 con el motivo (`docs/backend.md`).
- **Frontend:** pantalla para declarar la credencial propia, accesible a todo miembro; columna y acción de
  retiro en Personal; botones de firma deshabilitados con el motivo y un enlace para declarar; registros
  firmados que muestran al firmante y su COP.
- **Docs, al archivar:** `docs/domain.md` (credencial profesional en el ER y la tabla de actores),
  `docs/vision.md`, la fila *Autoría por profesional habilitado* de `docs/compliance/regulatory-matrix.md`, y una
  nota en `ADR-0016` D7 con la asimetría otorgar/retirar y los dos niveles credencial/membresía.
- **Operación:** al desplegar, los profesionales de la clínica en producción tienen que declarar su credencial
  antes de volver a firmar. Hoy es una sola clínica, con datos de prueba.

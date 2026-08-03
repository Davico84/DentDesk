# ADR-0016: Responsabilidad clínica — autoría, secreto profesional, acceso y confidencialidad

- **Estado:** Aceptado (decisión); **pendiente de implementar** — varias decisiones **bloqueadas por `staff`**
- **Fecha:** 2026-08-03
- **Decisores:** David Velarde Alvarado
- **Cubre:** los huecos **A6, B2, B3, B4, C1** de [`docs/compliance/regulatory-matrix.md`](../compliance/regulatory-matrix.md)
- **Relacionado:** complementa [`ADR-0014`](0014-ciclo-de-vida-del-documento-clinico.md) — allí la **integridad** del documento, acá la **confidencialidad** y la **responsabilidad** sobre él

## Contexto

El Código de Ética del COP y la Ley N°29733 imponen obligaciones que el sistema hoy **no cumple**, y que no son de infraestructura sino de dominio clínico:

- **Autoría individual** (COP art. 42): toda anotación pertenece a **un** cirujano dentista identificado por su colegiatura. Hoy la autoría se registra por `userId`, sin vínculo al COP.
- **Secreto profesional** (COP arts. 31–36): los datos clínicos pertenecen al ámbito del secreto del odontólogo tratante. Hoy **cualquier miembro del tenant ve todo** — recepción incluida.
- **Trazabilidad del acceso** (Ley 29733): toda lectura de datos sensibles debe dejar rastro. No existe.
- **Confidencialidad en reposo** (Ley 29733): los datos clínicos se guardan en claro.

> ⚠️ **Dependencia dura de `staff`.** D1, D2 y D3 necesitan el modelo de roles real (`ADMIN`/`DENTIST`/`RECEPTIONIST`) y el perfil profesional con su colegiatura. Hoy solo existe `ADMIN`/`USER`. **Este ADR decide el modelo; `staff` lo habilita.** Sin esa capacidad, D1–D3 no se pueden implementar — y eso la empuja hacia arriba en el roadmap.

> ⚠️ **Alcance de la evidencia.** Los artículos provienen de los análisis de `docs/compliance/`, que traducen las normas pero no las reproducen. Las decisiones se sostienen por sus argumentos; el **cumplimiento** queda sujeto a validación contra el texto oficial.

## Decisión

### D1 — La autoría se **fotografía**, no se referencia

Cada registro clínico guarda **quién lo firmó y con qué credenciales lo hizo en ese momento**: identificador del profesional **más su número de colegiatura COP copiado al momento del acto**. No un puntero al perfil vigente.

**Es lo contrario de lo que se decidió para la filiación, y a propósito.** `clinical-history` **deriva** la filiación de `Patient` para que refleje siempre el dato actual (`ADR-0011`). La autoría hace lo opuesto:

| | Filiación del paciente | Autoría del profesional |
|---|---|---|
| Pregunta que responde | *¿quién **es** este paciente?* | *¿quién **firmó** esto?* |
| Verdad relevante | la **actual** | la del **momento del acto** |
| Mecanismo | **derivada** de `Patient` | **copiada** al registrar |

Si mañana un odontólogo cambia de colegiatura, se le suspende, o deja la clínica, el documento firmado hace tres años debe seguir mostrando **la colegiatura que tenía cuando firmó**. Un puntero al perfil vigente reescribiría el pasado en silencio — exactamente lo que el libro de actas prohíbe.

**Se prohíben las cuentas compartidas** (COP art. 42): una sesión pertenece a una persona. Sin eso, la autoría copiada certifica una ficción.

### D2 — Se clasifica el **dato**, no el endpoint

El secreto profesional se aplica sobre **clases de información**, no sobre rutas:

| Clase | Qué incluye | Quién accede |
|---|---|---|
| **Administrativa** | filiación, contacto, citas, cobros, estado del expediente | todo el personal de la clínica |
| **Clínica** | motivo de consulta, antecedentes, anamnesis, diagnósticos CIE10, hallazgos del odontograma, evolución SOAP, notas | **solo rol clínico** |

**Por qué el dato y no la ruta.** Clasificar por endpoint parece más simple hasta que un endpoint devuelve las dos clases —`ClinicalHistoryResponse` ya mezcla filiación (administrativa) con diagnósticos (clínica)— o hasta que se agrega un endpoint nuevo y nadie recuerda clasificarlo. La clasificación en el dato **viaja con él**, y un endpoint nuevo hereda la regla sin decidir nada.

Esta clasificación es también la que alimenta D5 (qué se cifra) y D4 (qué acceso se audita): un solo criterio, tres usos.

### D3 — Al acceso clínico no autorizado se le **deniega**, no se le filtra

Un usuario sin rol clínico que pide un recurso clínico recibe un **rechazo explícito**, no una respuesta con los campos clínicos vacíos o recortados.

**Por qué no filtrar el payload**, que es lo que sugiere el análisis del COP:

- Una respuesta parcial es **mentira por omisión**: el cliente no distingue *"este paciente no tiene diagnósticos"* de *"no te dejo verlos"*, y muestra una historia clínica vacía como si estuviera vacía de verdad.
- Cada campo nuevo hay que acordarse de filtrarlo. Es el modo de falla que este proyecto ya conoce: una regla que solo funciona si alguien la recuerda **termina no funcionando**.
- El rechazo es **testeable de una sola forma**; el filtrado exige un test por campo.

La recepción **no necesita** una historia clínica parcial: necesita la ficha administrativa, que es un recurso distinto y sigue accesible.

### D4 — Auditoría de acceso clínico: qué se registra, y que **bloquea**

No es un log técnico. Es un **registro clínico de quién vio qué**, exigido por la Ley 29733.

| | Decisión |
|---|---|
| **Qué se audita** | solo el acceso a datos de **clase clínica** (D2). Listar pacientes o consultar una agenda no se audita — auditarlo todo genera ruido que entierra lo que importa |
| **Qué se registra** | usuario, paciente, recurso, momento, origen (IP) |
| **Dónde** | tabla propia, **append-only**, con el mismo mecanismo de `ADR-0014` D4 (`DELETE`/`UPDATE` revocados) |
| **Si el registro falla** | **la operación falla.** Un acceso no auditado es un acceso que no ocurrió, legalmente |

**Que bloquee es la decisión incómoda y es la correcta.** Acopla la disponibilidad de la lectura clínica al registro de auditoría. Se acepta porque la alternativa —seguir sirviendo el dato y perder el rastro— produce exactamente el agujero que la ley busca cerrar. Y el costo real es bajo: **las lecturas clínicas no son de alta frecuencia** (un odontólogo abre decenas de expedientes por día, no miles), a diferencia de los listados administrativos, que no se auditan.

### D5 — Cifrado en reposo: dos niveles, y **una sola llave gestionada**

| Nivel | Alcance | Dueño |
|---|---|---|
| **Base** | disco, backups, réplicas | **infraestructura** — sin dueño técnico asignado hoy (ver Open Questions) |
| **Dominio** | columnas de **clase clínica** (D2) | esta decisión |

**Qué se cifra**: texto libre clínico — motivo de consulta, antecedentes, anamnesis, notas, evolución, especificaciones.
**Qué NO se cifra**: identificadores, fechas, estados, códigos CIE10 y claves foráneas. Cifrarlos destruye índices, búsquedas y las políticas RLS, sin ganancia real: el valor sensible está en la narrativa, no en que exista una fila.

**Una sola llave por entorno, gestionada por KMS — no una llave por clínica.** Es la parte contraintuitiva de esta decisión:

- El modelo de amenaza real es **un volcado o un backup filtrado**, y contra eso una llave por entorno protege igual que mil.
- El aislamiento entre clínicas **ya lo garantiza la RLS de Postgres**, verificada con tests como rol no-superusuario. Llaves por tenant duplicarían esa garantía por otro medio.
- Y traerían un riesgo asimétrico grave: **perder la llave de una clínica es perder su historia clínica**, con obligación legal de conservarla 15 años (`ADR-0014` D5). Multiplicar las llaves multiplica ese riesgo sin reducir el que importa.

⚠️ **El cifrado no afecta al hash de `ADR-0014`**: el hash se calcula sobre el documento canónico —los valores del dominio, ya descifrados—, no sobre los bytes almacenados. Cambiar la llave o rotar el cifrado **no invalida ningún hash**. Es una propiedad deseable de haber separado integridad de confidencialidad.

### D6 — El consentimiento se versiona y se revoca; nunca se reescribe

`ADR-0007` decidió que el tutor firma por el menor, y `treatments` prevé la entidad `Consent`. Falta lo que hace que un consentimiento **pruebe** algo:

- **Versión del texto firmado.** Se guarda **qué versión** del documento de consentimiento aceptó el paciente, no solo que aceptó. Sin eso es imposible demostrar qué le informaron: si el texto cambia en 2027, no hay forma de saber qué decía cuando firmó en 2024.
- **La revocación es un asiento nuevo**, no un borrado ni un cambio de estado sobre la firma original. El consentimiento otorgado **ocurrió**; revocarlo es un hecho posterior. Mismo principio de libro de actas que el resto del expediente.
- El texto de cada versión **se conserva íntegro**: referenciar una versión que ya no existe no prueba nada.

## Consecuencias

- ✅ **Un solo criterio de clasificación** (D2) alimenta el control de acceso, la auditoría y el cifrado. Tres obligaciones normativas distintas resueltas con una sola decisión de modelo.
- ✅ **La autoría fotografiada sobrevive a los cambios de personal**, que es justo cuando un documento firmado se vuelve importante.
- ✅ **Cifrado e integridad no se estorban**: el hash se calcula sobre valores del dominio, así que rotar llaves no invalida documentos sellados.
- ✅ **Denegar en vez de filtrar** (D3) elimina una familia entera de bugs: los campos que se olvidó filtrar.
- ⚠️ **D1, D2 y D3 están bloqueados por `staff`.** No hay roles clínicos ni perfil profesional con colegiatura. El ADR define el modelo para que `staff` sea cableado y no rediseño — pero **hasta que exista, el incumplimiento sigue vigente**.
- ⚠️ **D4 acopla disponibilidad a auditoría**: si la tabla de auditoría no acepta escrituras, la lectura clínica se detiene. Es deliberado y acotado a datos clínicos.
- ⚠️ **El cifrado de columnas rompe la búsqueda sobre ellas.** Hoy no se busca por texto clínico; si mañana se quisiera, hay que resolverlo con otra técnica (índices cifrados, búsqueda fuera de la base) y no revirtiendo el cifrado.
- ⚠️ **Toca capacidades construidas**: `clinical-history` y `patients` ganan cifrado en columnas existentes (migración con datos), y toda lectura clínica gana auditoría.
- ⚠️ **El nivel base del cifrado (backups, disco) no tiene dueño.** No es de este ADR ni de `ADR-0014`: es de despliegue, y está sin asignar.

## Alternativas descartadas

- **Referenciar el perfil profesional vigente en vez de copiar la colegiatura** — descartada (D1): reescribiría el pasado cuando cambie una colegiatura o se dé de baja a un profesional. Es el mismo error que el libro de actas evita, aplicado a la firma en lugar del contenido.
- **Clasificar por endpoint en vez de por dato** — descartada (D2): `ClinicalHistoryResponse` ya mezcla ambas clases, y un endpoint nuevo quedaría sin clasificar hasta que alguien lo recuerde.
- **Filtrar el payload para roles no clínicos** (lo que sugiere el análisis del COP) — descartada (D3): produce respuestas parciales indistinguibles de datos ausentes, y exige recordar filtrar cada campo nuevo.
- **Llaves de cifrado por tenant** — descartada (D5): multiplica el riesgo de pérdida irrecuperable contra una obligación de conservar 15 años, para reforzar un aislamiento que la RLS ya garantiza y contra una amenaza que no es la real.
- **Cifrar todas las columnas** — descartada: destruye índices, búsqueda y RLS a cambio de proteger identificadores y fechas que no son el dato sensible.
- **Auditar toda lectura, no solo la clínica** — descartada (D4): el volumen de los listados administrativos enterraría los accesos que de verdad importan, y encarecería la operación sin ganancia legal.
- **Que el fallo de auditoría no bloquee la lectura** — descartada (D4): deja accesos sin rastro, que es precisamente el agujero que la Ley 29733 busca cerrar.

## Open Questions

1. **⚠️ ¿`ADMIN` es un rol clínico?** El dueño de una clínica pequeña suele ser el odontólogo, y `docs/domain.md` ya asume que `ADMIN` accede a lo clínico. Pero un gerente administrativo con rol `ADMIN` tendría acceso al secreto profesional. `domain.md` anota la contrapartida; **con el COP de por medio deja de ser aceptable por omisión** y hay que decidirlo — probablemente desdoblando rol clínico y administración, como ese mismo documento sugiere.
2. **¿Quién es el dueño técnico del cifrado de backups e infraestructura?** Heredada de `ADR-0014`: no cae en integridad ni en confidencialidad de aplicación, y sigue sin asignar.
3. **¿La auditoría de acceso se conserva 15 años como el expediente?** La Ley 29733 exige el registro pero no fija el plazo. Si se conserva igual que la historia, su volumen supera al del dato clínico.
4. **¿El paciente puede consultar quién vio su historia?** La Ley 29733 da derechos de acceso al titular. Está fuera de alcance hoy —el paciente no es usuario del sistema—, pero el registro de D4 es lo que haría posible responderlo.

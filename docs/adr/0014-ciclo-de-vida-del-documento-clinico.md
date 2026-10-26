# ADR-0014: Ciclo de vida del documento clínico — integridad, conservación y archivo

- **Estado:** Aceptado (decisión); **pendiente de implementar**
- **Fecha:** 2026-08-03
- **Decisores:** David Velarde Alvarado
- 🔴 **Superado en parte por [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) D1: el plazo de conservación son 20 años, no 15.** Afecta a **D5**, **D6** y **D7**. El razonamiento de las tres decisiones sobrevive intacto; el número estaba mal, tomado de un análisis derivado en vez del texto oficial (NTS 139 **5.3.1 (6) e** y COP **art. 33**). `ADR-0018` D4 además le quita una de sus dos patas normativas a **D2**: el COP no menciona la firma digital en ningún artículo.
- **Cubre:** los huecos **A1–A5, A7, A8** de [`docs/compliance/regulatory-matrix.md`](../compliance/regulatory-matrix.md)
- **Relacionado:** aplica a los documentos sellables de [`ADR-0011`](0011-historia-clinica-documento-inicial.md) y [`ADR-0012`](0012-odontograma-libro-de-asientos.md) · el **cifrado en reposo** (A6) se mueve a `ADR-0016` (ver Decisión)

## Contexto

El sistema ya sella documentos clínicos: la Historia Clínica (`DRAFT→COMPLETED`, construida) y el odontograma (propuesto). El sellado los vuelve inmutables **en el servidor** y está testeado, incluso contra ataque directo a la API.

Pero al mapear la normativa ([`regulatory-matrix.md`](../compliance/regulatory-matrix.md)) aparecieron seis obligaciones que ningún ADR cubría, todas sobre **qué pasa con el documento después de sellarlo**:

- **Probar** que no fue alterado (Ley 27269; los COP arts. 52–54 que citaba esta línea son **otra materia** — [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) D4).
- **Firmarlo** con certificado acreditado, para que sustituya al papel con valor probatorio.
- **Exportarlo** como expediente formal. ⚠️ El plazo lo fija NTS 139 **5.3.3 r**: copia al paciente en **5 días** ([`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) D7.5).
- **Conservarlo** 20 años desde la última atención (NTS 139 **5.3.1 (6) e**; la redacción original decía 15 años y 6.6.1 a — ambos corregidos por [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) D1 y D3).
- **Separarlo** en archivo activo (5 años, NTS 139 **5.3.1 (4) k**) y pasivo (15 años más, **5.3.1 (4) l**).
- **Destruirlo** solo con acta del Comité (NTS 139 **5.3.1 (6) e** · **5.3.2 (1) c**).

Hoy el sellado garantiza que *el sistema* no permita modificar. No garantiza que se pueda **demostrar** que no se modificó, ni dice nada sobre cuánto tiempo vive el documento.

> 🔴 **Alcance de la evidencia — la advertencia se cumplió.** Los numerales de la redacción original venían de los análisis de `docs/compliance/`, que **traducen** las normas sin reproducirlas, y este ADR los citó como si fueran la norma. Al leer el texto oficial, [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) encontró que **ninguno existía** (D3) y que el plazo de conservación era más corto que el legal (D1). Los § de esta sección ya están reemplazados por los reales. Las decisiones técnicas se sostuvieron por sus argumentos, que es lo que salvó el ADR: **cambió la cita, no el diseño.**

## Decisión

> **Sobre el cifrado en reposo (A6).** Se mueve a `ADR-0016`. Su driver no es el ciclo de vida del documento sino la **confidencialidad** — Ley 29733 y el secreto profesional del COP (**arts. 74–77**; esta línea decía 31–36, corregido por [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) D4), que es materia de *Clinical Accountability*. Agruparlo acá por "estar en la base de datos" mezclaría dos problemas distintos.
>
> **El corte entre ambos ADR**: acá se decide la **integridad** (que el documento no cambió: hash y firma); en `ADR-0016`, la **confidencialidad** (que solo quien corresponde lo vea: cifrado y control de acceso). Son propiedades independientes — un documento puede estar íntegro y expuesto, o cifrado y adulterado.
>
> ⚠️ El **cifrado de backups y de la infraestructura** no cae en ninguno de los dos: es responsabilidad de despliegue. **Las copias** ya tienen dueño (`add-database-backups`, 2026-10-26: copia cifrada con `age` fuera del proveedor, ver Open Question 4); el **cifrado del disco** del proveedor sigue siendo del proveedor y sin decisión propia.

### D1 — Hash de integridad al sellar, sobre una representación canónica

Al sellar un documento se calcula y persiste su **SHA-256**, dentro de la misma transacción del sellado. Cada documento sellable (Historia Clínica, bloque de odontograma) lleva el suyo.

**Va ahora, aunque la firma se posponga.** Si se sellan documentos sin hash y la firma llega en dos años, **los sellados en el medio nunca podrán probar que no fueron alterados** — no hay contra qué comparar. El hash cuesta una columna y una función; la firma cuesta meses. Se desacoplan en el tiempo, no en el orden.

**El contrato del hash queda fijado acá**, no en la implementación. Decir solo "SHA-256" no alcanza: dos serializaciones distintas del mismo documento producirían hashes distintos y la verificación fallaría sin que nada esté mal.

| Elemento | Decisión |
|---|---|
| **Algoritmo** | SHA-256 |
| **Serialización** | **JSON canónico según RFC 8785 (JCS)** — estándar publicado, con implementaciones existentes; evita inventar reglas de orden y escape propias |
| **Contexto mínimo incluido** | `organizationId`, `patientId`, tipo de documento, identificador del documento y su fecha de sellado — **además** del contenido clínico |
| **Versión del formato** | se persiste junto al hash |

**Por qué el contexto va dentro del hash**: sin él, dos documentos con el mismo contenido clínico (dos historias igual de vacías, por ejemplo) producirían el mismo hash, y un hash podría "reutilizarse" para justificar otro documento. Incluir identidad y contexto ata el hash a **ese** documento de **ese** paciente en **esa** clínica.

⚠️ **La canonicalización es la parte frágil.** Si se cambia su algoritmo después de sellar documentos, los hashes viejos dejan de reproducirse y la integridad se vuelve inverificable. Por eso se **versiona**: cada documento guarda con qué versión se calculó, la versión existente **nunca se edita**, y una regla nueva se agrega como versión nueva. El verificador elige el algoritmo según la versión del documento, no según la última.

### D2 — La firma digital se pospone, pero se deja el punto de extensión

**No se integra un proveedor IOFE ahora.** Acreditarse ante INDECOPI/RENIEC, integrar DNIe o certificados en la nube, y sostener esa integración es trabajo de meses que no tiene sentido antes de un producto estable.

Lo que sí se decide, para no rehacer nada después:

- **Se firma el hash, no el documento entero.** Es el patrón estándar y hace la decisión reversible: cuando llegue el proveedor, firma un valor de 32 bytes que ya existe.
- **La firma es un dato aparte**, no una columna del documento sellado: un documento puede existir sin firma (hoy) y ganarla después sin reabrirse — lo que sería imposible si la firma viviera dentro del propio documento inmutable.
- **Los documentos sellados sin firma no se pueden firmar retroactivamente con validez**: la firma prueba *quién y cuándo*, y eso no se puede reconstruir. Es una limitación asumida, no un descuido — y es la razón de que D1 vaya primero.

### D3 — Dos artefactos con nombre propio: el **documento canónico** y la **exportación**

Decir *"el artefacto canónico es el que se exporta"* sería ambiguo y peligroso: se puede leer como *"hashea el PDF"*. Son **dos cosas distintas**, y solo una se hashea.

| | **Documento canónico** | **Exportación** |
|---|---|---|
| Qué es | representación **estructurada y versionada** del documento sellado | lo que recibe un humano o un auditor |
| Formato | JSON canónico (RFC 8785), determinista | PDF u otro render legible |
| Se almacena | no — se **reconstruye** de forma reproducible | no — se genera a demanda |
| Se hashea | ✅ **este** | ❌ nunca |
| Se firma | ✅ su hash (D2) | ❌ |

```
documento sellado (BD)
      │
      ▼  serialización canónica versionada
documento canónico  ──SHA-256──▶  hash  ──(futuro)──▶  firma
      │
      ▼  render
  exportación (PDF)  ──lleva adjunto──▶  hash + firma como evidencia
```

**Nunca se hashea el render.** Un PDF depende de fuentes, versiones de librería y del sistema operativo: regenerarlo en dos años puede producir bytes distintos con el mismo contenido, y el hash dejaría de coincidir sin que nada se haya alterado. El documento canónico es reproducible por construcción.

La exportación **transporta** el hash y (cuando exista) la firma como evidencia adjunta, para que quien la reciba pueda verificar contra el documento canónico.

*Alternativa descartada:* generar el PDF al sellar, almacenarlo y firmarlo (patrón XAdES sobre PDF). Es lo que hacen muchos sistemas y tiene la ventaja de que el objeto firmado es exactamente lo que un humano ve. Se descarta porque obliga a **almacenar un binario por documento** desde el día uno, congela el diseño visual del expediente para siempre, y no aporta garantía adicional: el PDF sigue siendo derivable de los datos firmados.

### D4 — Inmutabilidad también en la base, no solo en el servicio

Hoy la inmutabilidad se aplica en el servicio y está testeada. La norma (NTS 139 **5.3.1 (8) b** · **5.3.3 j**; esta línea citaba 5.4.1 i, inexistente — [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) D3) pide además que la **capa de persistencia** no admita la operación. Dos medidas, precisas:

| Medida | Cómo |
|---|---|
| **`DELETE` revocado** | El rol de aplicación **nunca** borra registros clínicos. `REVOKE DELETE` sobre las tablas clínicas para `odontorisas_app` — el borrado no es legítimo en ningún flujo, así que revocarlo no rompe nada. |
| **`UPDATE` bloqueado sobre lo sellado** | **No se puede revocar `UPDATE` a nivel de tabla**: el mismo registro es mutable en `DRAFT` e inmutable en `COMPLETED`. Un permiso de tabla no distingue filas. Se resuelve con un **trigger** `BEFORE UPDATE` que rechaza si la fila ya está sellada. |

**Alcance del trigger — no solo el aggregate root.** Un documento sellado incluye sus **tablas hijas**: los diagnósticos de la Historia Clínica, los hallazgos del odontograma. Proteger solo la tabla raíz dejaría el contenido modificable, que es justamente lo que la norma prohíbe. El trigger cubre:

- `UPDATE` sobre la raíz cuando ya está sellada;
- `UPDATE` sobre una fila hija cuya raíz está sellada;
- `DELETE` de filas hijas — revocado igual que en la raíz, e **impedido también por diseño del esquema**: ninguna FK de un documento clínico usa `ON DELETE CASCADE`, para que no exista un borrado indirecto que esquive la revocación.

**Traducción del error.** El rechazo llega desde la base como `SQLException` con `SQLSTATE` propio, no como una excepción de dominio. Se traduce a un `errorType` dedicado con el mismo formato RFC 9457 del resto de la API — no puede filtrarse un error crudo de Postgres al cliente.

**Cómo se testea**: SQL directo con el **rol de aplicación** (no el owner, que salta las restricciones), verificando que el `UPDATE` sobre una fila sellada y el `DELETE` fallen. El proyecto ya tiene la infraestructura exacta para esto — `AbstractTenantAwareIntegrationTest` corre como rol no-superusuario, que es como se testean las políticas RLS.

**Es defensa en profundidad, no reemplazo**: el servicio sigue rechazando y sus tests siguen valiendo. Lo que agrega es cobertura ante un bug del servicio, una conexión directa a la base o un desarrollador futuro que evite la capa de dominio.

⚠️ **El trigger sería el primero del proyecto.** Es un patrón nuevo, con su costo: la excepción sube por JDBC como error de base, hay que traducirla, y no es visible leyendo el código Java. Se acepta porque el objeto protegido es un documento médico-legal, no un dato de negocio cualquiera.

*Alternativa descartada:* mover los documentos sellados a tablas separadas de solo-inserción, y revocar `UPDATE` ahí. Más limpio conceptualmente, pero duplica el esquema de cada capacidad sellable y complica toda consulta que mezcle estados.

### D5 — Retención: 20 años desde la última atención

> 🔴 **Corregido por [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) D1.** Esta decisión fijaba **15 años**, tomados de un análisis derivado. El texto oficial da tres plazos y 15 es **el tramo pasivo**, no el total: activo 5 años (NTS 139 **5.3.1 (4) k**) + pasivo 15 (**5.3.1 (4) l**) = **20 años de conservación** desde la última atención (**5.3.1 (6) e**), confirmados de forma independiente por **COP art. 33** (*"un mínimo de veinte (20) años"*). **40 años** si hay diagnóstico de cáncer de origen ocupacional. Lo que sigue —la definición de `ultimaAtencion`— es correcto y no cambia.

El expediente **no se puede purgar** mientras la diferencia entre hoy y la **última atención** sea ≤ 20 años (≤ 40 si hay diagnóstico de cáncer de origen ocupacional).

**`ultimaAtencion` es un concepto propio del dominio, no un atajo sobre los documentos sellados.** La primera redacción de este ADR la definía como *"la fecha del documento sellado más reciente"*, y estaba **mal**: una atención puede producir un documento que quedó sin sellar, una evolución, o una cita atendida — y ninguno contaría. Eso arrancaría el plazo de conservación **antes de tiempo**, que es el error peligroso: permitiría purgar un expediente todavía protegido.

```
ultimaAtencion(paciente) = máx( fecha de CUALQUIER registro clínico del paciente )
```

- **Incluye documentos sin sellar.** Si un odontólogo abrió una Historia y no la cerró, el paciente fue atendido igual. Contar solo lo sellado castigaría al expediente por un trámite incompleto.
- **Cada capacidad clínica aporta su fecha.** Hoy: Historia Clínica y odontograma. Cuando existan `appointments`, `treatments` o la evolución, cada una suma la suya **sin cambiar la definición** — por eso se define como concepto y no como consulta a una tabla concreta.
- **Ante la duda, la fecha más reciente gana.** Empezar el plazo tarde conserva de más; empezarlo temprano permite destruir de menos. El error asimétrico manda: se conserva de más.

La regla es **una precondición del dominio**, no una tarea de limpieza: el sistema **no ofrece purga** (ver D7). Se documenta igual porque cualquier operación futura de borrado —incluida la baja de un tenant— debe consultarla.

### D6 — Archivo activo/pasivo es **derivado**, no un estado almacenado

Un expediente es **pasivo** si su última atención tiene más de 5 años; **activo** si no. Eso es **una función de una fecha que ya existe**, no un estado que haya que mantener.

```
esArchivoPasivo(paciente) = (hoy − últimaAtención) > 5 años
```

**Ventajas de derivarlo:**

- **No hace falta un job programado.** El paso automático activo→pasivo desaparece como problema — y con él, el bloqueo de la regla ArchUnit que prohíbe `@Scheduled`. No se agrega infraestructura para mantener sincronizado algo que se calcula en una resta.
- **No puede desincronizarse.** Un flag almacenado que nadie actualiza es peor que no tenerlo: diría "activo" sobre un expediente que lleva ocho años sin atención.

**Pero derivar el estado no produce evidencia, y la evidencia también es obligación** (hueco **C2**: *trazabilidad de las transiciones de archivo*). Un valor calculado responde *"¿está pasivo hoy?"*; no responde *"¿el sistema aplicó la política?"*. Son dos preguntas distintas y la segunda es la que hace una auditoría. Se separan:

| | Cómo |
|---|---|
| **Estado, para consultar** | **derivado** — `(hoy − últimaAtención) > 5 años`. No se almacena. |
| **Evidencia, para auditar** | **reporte de conservación** generable a demanda: por expediente, su última atención, la fecha calculada de paso a pasivo y su clasificación actual. Es **determinista y reproducible** — la misma consulta sobre los mismos datos da el mismo resultado en cualquier momento. |
| **Acciones derivadas del estado** | cuando el sistema **actúa** en función de ser pasivo (excluirlo de un listado, restringir escritura, moverlo de almacenamiento), **esa acción sí se registra**: es un hecho ocurrido, no un cálculo. |

La distinción es la que salva la decisión: **no hace falta registrar una transición que nadie ejecutó** —es derivable de una fecha—, pero sí hace falta registrar **lo que el sistema hizo** basándose en ella.

**El traslado físico a almacenamiento frío no se hace.** La norma exige la **separación lógica** y la conservación, no una tecnología de almacenamiento. Particionar o mover datos es una optimización de coste, legítima cuando el volumen lo justifique — hoy no lo justifica, y hacerlo temprano acopla el cumplimiento a una decisión de infraestructura.

### D7 — La destrucción controlada **no se construye**

Cumplidos los 20 años, la norma permite destruir con acta del Comité de Historias Clínicas y doble verificación. **No se implementa nada de eso.**

**v1 adopta una política de conservación sin destrucción.** No hay endpoint de borrado y D4 revoca el `DELETE` al rol de aplicación. Construir el flujo completo —carga del acta, firma del director, soft delete auditado— sería trabajo para un escenario que **no puede ocurrir hasta dentro de 20 años**, con requisitos que para entonces habrán cambiado.

⚠️ **Esto NO equivale a cumplimiento automático**, y decirlo así sería sobreafirmar. Lo que la ausencia de destrucción protege, y lo que **no**:

| Protegido | **No protegido** |
|---|---|
| Borrado desde la aplicación (sin endpoint) | Privilegios del **rol owner** (`odontorisas_admin`, migraciones Flyway) |
| Borrado accidental por el rol de app (`DELETE` revocado) | Operaciones **administrativas** directas sobre la base |
| Borrado indirecto por FK (sin `ON DELETE CASCADE`) | **Purgas de infraestructura**: retención de backups, políticas del proveedor cloud |
| | **Cancelación del tenant** — sin decisión tomada (Open Question 3) |

Es decir: **v1 no puede destruir por sí mismo, pero el dato sigue siendo destruible desde fuera del dominio.** Cerrar eso pertenece a infraestructura y a la política de backups. Las **copias** ya tienen dueño (`add-database-backups`): retención de 90 días en GitHub más las copias mensuales a disco, que son también una vía por la que el dato sobrevive fuera del dominio. Las operaciones administrativas directas sobre la base y la cancelación del tenant siguen sin dueño.

⚠️ Consecuencia asumida: **el almacenamiento solo crece.** Es el precio correcto para un registro médico-legal.

## Consecuencias

- ✅ **El hash cierra la ventana de documentos improbables.** Todo lo que se selle desde su implementación es verificable, aunque la firma llegue años después.
- ✅ **La firma queda a un paso**, no a un rediseño: firmar un hash que ya existe, con la firma como dato aparte.
- ✅ **Exportación e integridad son el mismo artefacto**, no dos implementaciones que mantener sincronizadas.
- ✅ **D6 elimina un bloqueo de infraestructura**: al derivar el estado de archivo, no hace falta el job que la regla ArchUnit impide, ni gestión de husos horarios, ni un flag que se desincronice.
- ⚠️ **Derivar el estado no produce evidencia por sí solo.** El hueco C2 exige trazabilidad de las transiciones, y un valor calculado no la da: se cubre con el **reporte de conservación** determinista y con el registro de las **acciones** que el estado provoca. Es trabajo adicional que la primera redacción de D6 pasaba por alto.
- ✅ **D7 convierte trabajo en una decisión documentada**: no construir la destrucción es más seguro que construirla mal. ⚠️ Pero **no equivale a cumplimiento**: el dato sigue siendo destruible desde fuera del dominio (owner, backups, infraestructura).
- ⚠️ **El trigger de D4 es un patrón nuevo en el proyecto.** Errores desde la base son menos legibles que una excepción de dominio y hay que traducirlos al formato de error de la API.
- ⚠️ **La canonicalización de D1 es el punto frágil.** Cambiarla invalida los hashes previos. Mitigado versionándola, pero exige disciplina: es código que se agrega, nunca se edita.
- ⚠️ **Los documentos ya sellados** (si los hubiera al implementar) quedan **sin hash**, y no se les puede calcular retroactivamente con valor probatorio: nada garantiza que no hayan cambiado antes. Hoy no hay ninguno en producción — otra razón para hacerlo ya.
- ⚠️ **Toca capacidades construidas**: `clinical-history` gana el hash al sellar. Sus tests de sellado deberán verificarlo.
- ⚠️ **El almacenamiento solo crece** (D7). Asumido.

## Alternativas descartadas

- **Posponer también el hash hasta que llegue la firma** — descartada, y es la decisión más importante de este ADR. Parece razonable ("total, sin firma el hash no prueba autoría") pero deja un **agujero irrecuperable**: todo documento sellado antes del hash queda para siempre sin forma de demostrar integridad. El hash es barato y su ausencia no se puede reparar después.
- **Firmar el PDF en vez de los datos** — ver D3.
- **Almacenar el estado de archivo como columna, con job programado** — descartada por D6: agrega infraestructura bloqueada por ArchUnit para mantener sincronizado un valor que es una resta, con riesgo de desincronización que no existe si se deriva.
- **Construir ahora la destrucción controlada** — ver D7.
- **Tablas separadas de solo-inserción para lo sellado** — ver D4.
- **Cubrir también el cifrado en reposo acá** — descartada: su driver es la confidencialidad (Ley 29733, COP secreto profesional), no el ciclo de vida. Va a `ADR-0016`, junto al secreto profesional y el control de acceso, que es donde se decide **quién puede ver qué**.

## Open Questions

1. ✅ **Canonicalización — RESUELTO** en D1: **RFC 8785 (JCS)**, versionada, con contexto del documento incluido en el hash. Queda como detalle de implementación elegir la librería.
2. **¿El hash cubre los adjuntos?** Si mañana se suben radiografías (`images`, fila 9 del roadmap), ¿forman parte del documento sellado o son anexos con su propio hash? Afecta qué se firma.
3. **⚠️ ¿Qué pasa con el expediente al dar de baja un tenant?** — es también el principal hueco de D7. El análisis de retención plantea que las historias **no se borran** aunque la clínica cancele la suscripción: quedarían accesibles en modo lectura para cumplir la custodia. Es una decisión de negocio con implicaciones de coste, y no está tomada.
4. ✅ **¿Quién es el dueño técnico del cifrado de backups e infraestructura? — RESUELTO para las copias** (2026-10-26): el change `add-database-backups` (el workflow implementa la copia diaria cifrada con `age`, restaurada y verificada antes de guardarse; guía en `docs/deployment.md` §4.3–4.4; verificación operativa en curso). El cifrado del disco del proveedor no es nuestro. Sigue abierto el **cifrado en reposo** de columnas (`ADR-0016` D5), que es de la aplicación. Ojo con D7: la copia es otra vía por la que el dato **sobrevive** fuera del dominio (90 días en GitHub más las copias mensuales a disco); la destrucción, cuando se construya, tiene que alcanzarlas.
5. **¿Qué formato tiene la exportación además de PDF?** D3 fija que el documento canónico es JSON determinista y el PDF un render. Falta decidir si la exportación para auditoría entrega **ambos** (el canónico para verificar el hash, el PDF para leer) o solo uno.

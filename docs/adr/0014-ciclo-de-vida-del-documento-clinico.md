# ADR-0014: Ciclo de vida del documento clínico — integridad, conservación y archivo

- **Estado:** Aceptado (decisión); **pendiente de implementar**
- **Fecha:** 2026-08-03
- **Decisores:** David Velarde Alvarado
- **Cubre:** los huecos **A1–A5, A7, A8** de [`docs/compliance/regulatory-matrix.md`](../compliance/regulatory-matrix.md)
- **Relacionado:** aplica a los documentos sellables de [`ADR-0011`](0011-historia-clinica-documento-inicial.md) y [`ADR-0012`](0012-odontograma-libro-de-asientos.md) · el **cifrado en reposo** (A6) se mueve a `ADR-0016` (ver Decisión)

## Contexto

El sistema ya sella documentos clínicos: la Historia Clínica (`DRAFT→COMPLETED`, construida) y el odontograma (propuesto). El sellado los vuelve inmutables **en el servidor** y está testeado, incluso contra ataque directo a la API.

Pero al mapear la normativa ([`regulatory-matrix.md`](../compliance/regulatory-matrix.md)) aparecieron seis obligaciones que ningún ADR cubría, todas sobre **qué pasa con el documento después de sellarlo**:

- **Probar** que no fue alterado (Ley 27269, COP arts. 52–54).
- **Firmarlo** con certificado acreditado, para que sustituya al papel con valor probatorio.
- **Exportarlo** como expediente formal (COP Sec. III, Cap. II).
- **Conservarlo** 15 años desde la última atención (NTS 139, 6.6.1 a).
- **Separarlo** en archivo activo y pasivo (NTS 139, 6.6.1/6.6.2).
- **Destruirlo** solo con acta del Comité (NTS 139, 6.6.1 e).

Hoy el sellado garantiza que *el sistema* no permita modificar. No garantiza que se pueda **demostrar** que no se modificó, ni dice nada sobre cuánto tiempo vive el documento.

> ⚠️ **Alcance de la evidencia.** Los numerales provienen de los análisis de `docs/compliance/`, que **traducen** las normas pero no las reproducen. Las decisiones técnicas se sostienen por sus argumentos; el **cumplimiento** de cada una queda sujeto a validación contra el texto oficial.

## Decisión

> **Sobre el cifrado en reposo (A6).** Se mueve a `ADR-0016`. Su driver no es el ciclo de vida del documento sino la **confidencialidad** — Ley 29733 y el secreto profesional del COP (arts. 31–36), que es materia de *Clinical Accountability*. Agruparlo acá por "estar en la base de datos" mezclaría dos problemas distintos.

### D1 — Hash de integridad al sellar, sobre una representación canónica

Al sellar un documento se calcula y persiste su **SHA-256**, dentro de la misma transacción del sellado. Cada documento sellable (Historia Clínica, bloque de odontograma) lleva el suyo.

**Va ahora, aunque la firma se posponga.** Si se sellan documentos sin hash y la firma llega en dos años, **los sellados en el medio nunca podrán probar que no fueron alterados** — no hay contra qué comparar. El hash cuesta una columna y una función; la firma cuesta meses. Se desacoplan en el tiempo, no en el orden.

**Sobre una representación canónica**, no sobre las filas: el mismo documento debe producir el mismo hash siempre. Eso exige serialización determinista — orden de campos estable, colecciones ordenadas por criterio explícito, fechas en formato fijo, sin espacios accidentales. Un hash que cambia al reordenar una lista no prueba nada.

⚠️ **La canonicalización es la parte frágil de esta decisión.** Si se toca su algoritmo después de sellar documentos, los hashes viejos dejan de reproducirse y la integridad se vuelve inverificable. Por eso se versiona: cada documento guarda **con qué versión de canonicalización** se calculó su hash, y esa versión nunca se modifica — se agrega una nueva.

### D2 — La firma digital se pospone, pero se deja el punto de extensión

**No se integra un proveedor IOFE ahora.** Acreditarse ante INDECOPI/RENIEC, integrar DNIe o certificados en la nube, y sostener esa integración es trabajo de meses que no tiene sentido antes de un producto estable.

Lo que sí se decide, para no rehacer nada después:

- **Se firma el hash, no el documento entero.** Es el patrón estándar y hace la decisión reversible: cuando llegue el proveedor, firma un valor de 32 bytes que ya existe.
- **La firma es un dato aparte**, no una columna del documento sellado: un documento puede existir sin firma (hoy) y ganarla después sin reabrirse — lo que sería imposible si la firma viviera dentro del propio documento inmutable.
- **Los documentos sellados sin firma no se pueden firmar retroactivamente con validez**: la firma prueba *quién y cuándo*, y eso no se puede reconstruir. Es una limitación asumida, no un descuido — y es la razón de que D1 vaya primero.

### D3 — El documento canónico es lo que se exporta y lo que se firma

La **exportación formal del expediente** (A8) y la representación canónica de D1 son **el mismo artefacto**. No se construyen dos.

- El artefacto canónico es **datos estructurados**, no un PDF. El PDF es una **representación para humanos**, generada a demanda a partir de los datos, y no se almacena.
- **Se hashea y se firma el dato, no el render.** Un PDF depende de fuentes, versiones de librería y hasta del sistema operativo: regenerarlo dos años después puede producir bytes distintos con el mismo contenido, y el hash dejaría de coincidir. Los datos canónicos son reproducibles por construcción.

*Alternativa descartada:* generar el PDF al sellar, almacenarlo y firmarlo (patrón XAdES sobre PDF). Es lo que hacen muchos sistemas y tiene la ventaja de que el objeto firmado es exactamente lo que un humano ve. Se descarta porque obliga a **almacenar un binario por documento** desde el día uno, congela el diseño visual del expediente para siempre, y no aporta garantía adicional: el PDF sigue siendo derivable de los datos firmados.

### D4 — Inmutabilidad también en la base, no solo en el servicio

Hoy la inmutabilidad se aplica en el servicio y está testeada. La norma (NTS 139, 5.4.1 i) pide además que la **capa de persistencia** no admita la operación. Dos medidas, precisas:

| Medida | Cómo |
|---|---|
| **`DELETE` revocado** | El rol de aplicación **nunca** borra registros clínicos. `REVOKE DELETE` sobre las tablas clínicas para `odontorisas_app` — el borrado no es legítimo en ningún flujo, así que revocarlo no rompe nada. |
| **`UPDATE` bloqueado sobre lo sellado** | **No se puede revocar `UPDATE` a nivel de tabla**: el mismo registro es mutable en `DRAFT` e inmutable en `COMPLETED`. Un permiso de tabla no distingue filas. Se resuelve con un **trigger** `BEFORE UPDATE` que rechaza si la fila ya está sellada. |

**Es defensa en profundidad, no reemplazo**: el servicio sigue rechazando y sus tests siguen valiendo. Lo que agrega es cobertura ante un bug del servicio, una conexión directa a la base o un desarrollador futuro que evite la capa de dominio.

⚠️ **El trigger sería el primero del proyecto.** Es un patrón nuevo, con su costo: la excepción sube por JDBC como error de base, hay que traducirla, y no es visible leyendo el código Java. Se acepta porque el objeto protegido es un documento médico-legal, no un dato de negocio cualquiera.

*Alternativa descartada:* mover los documentos sellados a tablas separadas de solo-inserción, y revocar `UPDATE` ahí. Más limpio conceptualmente, pero duplica el esquema de cada capacidad sellable y complica toda consulta que mezcle estados.

### D5 — Retención: 15 años desde el último documento clínico sellado

El expediente **no se puede purgar** mientras la diferencia entre hoy y la **última atención** sea ≤ 15 años.

**"Última atención" se define como la fecha del documento clínico sellado más reciente del paciente** (Historia Clínica o bloque de odontograma). No se usa la fecha de la última cita: `appointments` no está construida, y una cita agendada y no atendida no es una atención. Cuando exista, podrá afinarse.

La regla es **una precondición del dominio**, no una tarea de limpieza: el sistema **no ofrece purga** (ver D7), así que hoy se cumple por ausencia. Se documenta igual porque cualquier operación futura de borrado —incluida la baja de un tenant— debe consultarla.

### D6 — Archivo activo/pasivo es **derivado**, no un estado almacenado

Un expediente es **pasivo** si su última atención tiene más de 5 años; **activo** si no. Eso es **una función de una fecha que ya existe**, no un estado que haya que mantener.

```
esArchivoPasivo(paciente) = (hoy − últimaAtención) > 5 años
```

Consecuencias de que sea derivado, y por qué es la decisión correcta:

- **No hace falta un job programado.** El paso automático activo→pasivo desaparece como problema — y con él, el bloqueo de la regla ArchUnit que prohíbe `@Scheduled`. No se agrega infraestructura para mantener sincronizado algo que se calcula en una resta.
- **No puede desincronizarse.** Un flag almacenado que nadie actualiza es peor que no tenerlo: diría "activo" sobre un expediente que lleva ocho años sin atención.
- **La evidencia de la transición sigue siendo derivable**: la fecha exacta en que un expediente pasó a pasivo es `últimaAtención + 5 años`, computable para cualquier auditoría, sin haberla registrado.

**El traslado físico a almacenamiento frío no se hace.** La norma exige la **separación lógica** y la conservación, no una tecnología de almacenamiento. Particionar o mover datos es una optimización de coste, legítima cuando el volumen lo justifique — hoy no lo justifica, y hacerlo temprano acopla el cumplimiento a una decisión de infraestructura.

### D7 — La destrucción controlada **no se construye**

Cumplidos los 15 años, la norma permite destruir con acta del Comité de Historias Clínicas y doble verificación. **No se implementa nada de eso.**

**El sistema simplemente no borra expedientes clínicos.** No hay endpoint de borrado, y D4 revoca el `DELETE` al rol de aplicación. Eso **cumple por construcción**: es imposible destruir un documento antes de tiempo porque es imposible destruirlo.

Construir el flujo de destrucción controlada —carga del acta, firma del director, soft delete auditado— sería trabajo para un escenario que **no puede ocurrir hasta dentro de 15 años**, con requisitos que para entonces habrán cambiado. Se decide explícitamente **no construirlo** y dejar registrado por qué, para que su ausencia se lea como una decisión y no como un olvido.

⚠️ Esto tiene una consecuencia real: **el almacenamiento solo crece.** Es el precio correcto para un registro médico-legal.

## Consecuencias

- ✅ **El hash cierra la ventana de documentos improbables.** Todo lo que se selle desde su implementación es verificable, aunque la firma llegue años después.
- ✅ **La firma queda a un paso**, no a un rediseño: firmar un hash que ya existe, con la firma como dato aparte.
- ✅ **Exportación e integridad son el mismo artefacto**, no dos implementaciones que mantener sincronizadas.
- ✅ **D6 elimina un bloqueo de infraestructura**: al derivar el estado de archivo, no hace falta el job que la regla ArchUnit impide, ni gestión de husos horarios, ni un flag que se desincronice.
- ✅ **D7 convierte trabajo en una decisión documentada**: no construir la destrucción es más seguro que construirla mal, y cumple por ausencia.
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

1. **¿Qué versión de canonicalización se adopta?** Hay estándares (JCS, RFC 8785) y la opción de definir una propia, más simple. Conviene elegir uno **antes** de sellar el primer documento con hash, porque cambiarlo después obliga a mantener dos algoritmos vivos.
2. **¿El hash cubre los adjuntos?** Si mañana se suben radiografías (`images`, fila 9 del roadmap), ¿forman parte del documento sellado o son anexos con su propio hash? Afecta qué se firma.
3. **¿Qué pasa con el expediente al dar de baja un tenant?** El análisis de retención plantea que las historias **no se borran** aunque la clínica cancele la suscripción: quedarían accesibles en modo lectura para cumplir la custodia. Es una decisión de negocio con implicaciones de coste, y no está tomada.
4. **¿"Última atención" cambia cuando exista `appointments`?** D5 la define por documento sellado. Con citas construidas, una atención sin documento sellado podría contar — habría que revisarlo.

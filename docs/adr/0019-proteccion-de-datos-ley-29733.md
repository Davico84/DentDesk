# ADR-0019: Protección de datos personales — Dentdesk es el *encargado*, no el titular

- **Estado:** Aceptado (decisión); **pendiente de implementar**. Abre **tres gaps 🔴 sancionables hoy** y **cierra** `ADR-0016` D4
- **Fecha:** 2026-09-23
- **Decisores:** David Velarde Alvarado
- **Supersede:** [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) **D8** (versionado de textos oficiales — ver **D12**) · resuelve la **Open Question 3** de [`ADR-0014`](0014-ciclo-de-vida-del-documento-clinico.md) D7 · **cierra** [`ADR-0016`](0016-responsabilidad-clinica.md) **D4** y refuerza **D2/D3**, **D5** y **D6**
- **Fuentes — texto oficial, completo y vigente, versionado en [`docs/compliance/normas/`](../compliance/normas/):**
  - **Ley N°29733** — arts. 1–40, Títulos Preliminar a VII, 12 disposiciones complementarias. ⚠️ texto original de 2011, sin modificatorias incorporadas
  - **D.S. N°016-2024-JUS** — reglamento **vigente**, arts. 1–135, El Peruano 30.11.2024, en vigor desde el **30.03.2025**
- ⚠️ **No se cita:** el **D.S. N°003-2013-JUS**, auténtico y completo pero **derogado** por el anterior; y la **Directiva de Seguridad de la ANPDP** (R.D. 019-2013-JUS), ilegible y desplazada. Ambos se conservan como evidencia — ver **D12**

## Contexto

La Ley 29733 sostenía cuatro decisiones del proyecto —`ADR-0016` D4 (log de accesos), D5 (cifrado en reposo), el consentimiento de datos de `ADR-0018` D7.4 y la parte abierta del COP art. 78— y **ninguna se había escrito con el texto delante**.

Llegar al texto costó **tres documentos descartados**, y cada uno falló por un motivo distinto. Vale la pena dejarlo escrito, porque son **tres pruebas independientes** y el proyecto solo conocía la primera.

### Documento 1 — un resumen disfrazado de ley

Se descartó por evidencia **interna**, sin necesidad del texto oficial:

| Lo que decía el resumen | Lo que dice la Ley |
|---|---|
| *"Para el tratamiento de los datos personales **es mediato** el consentimiento"* (art. 4) | *"debe **mediar** el consentimiento de su titular"* (**art. 5**) — el resumen invertía el sentido |
| *"datos referidos a **la** origen racial"* | *"datos referidos **al** origen racial y étnico"* |
| *"TÍTULO II: DERECHOS DE LOS TITULARES (**DERECHOS ARCO**)"* | Título II es **Tratamiento de datos personales**; los derechos son el **Título III**. *"Derechos ARCO"* es glosa doctrinal, no título de ley peruana |
| consentimiento art. 4, seguridad art. 8 | consentimiento **art. 5**, seguridad **art. 9** — todo corrido un número |
| saltaba del art. 10 al 18, y del 22 al 38 | **arts. 1–40 continuos** |

**Prueba 1 — coherencia interna.** Un texto promulgado no tiene faltas de concordancia, no renumera sus títulos y no usa glosas de manual como encabezados. **Esta prueba no requiere la norma**, que es justo lo que la hace valiosa: sirve incluso cuando no hay con qué comparar.

### Documento 2 — un reglamento auténtico, completo… y derogado

El **D.S. N°003-2013-JUS** llegó impecable: 131 artículos, El Peruano del 22.03.2013, sin un error. Pasó la prueba 1 sin despeinarse. Se leyó entero y **contenía exactamente lo que faltaba**: el plazo de conservación del encargado, la secuencia de baja, el log de accesos, los respaldos verificables, los campos de la inscripción registral, los plazos de respuesta al paciente.

**Estaba derogado hacía dieciocho meses**, por el D.S. N°016-2024-JUS.

🔴 **Diez decisiones estuvieron a punto de escribirse citando un reglamento sin vigor.** Lo único que lo evitó fue preguntar, antes de escribir, si un texto de 2013 seguía en pie en 2026.

**Prueba 2 — vigencia.** **El documento no la declara.** Un reglamento derogado se ve idéntico al día en que se publicó: mismo sello, misma numeración, misma prosa. Es la única de las tres pruebas que **exige una fuente externa al documento**.

### Documento 3 — la directiva ilegible

La **Directiva de Seguridad de la ANPDP** (R.D. 019-2013-JUS) llegó como **38 páginas escaneadas sin capa de texto** — `pdftotext` extrae 38 bytes, uno por salto de página. Y además quedó desplazada: el D.S. 016-2024-JUS **subió las obligaciones de seguridad al propio texto reglamentario**, así que dejó de ser la pieza clave.

**Prueba 3 — legibilidad.** Trivial comparada con las otras dos, pero conviene comprobarla antes de planificar trabajo sobre un documento que no se puede leer.

### Lo que quedó

Dos textos oficiales, completos y vigentes: **la Ley y el D.S. 016-2024-JUS**. Con ellos, lo que sigue ya no es hipótesis.

## Decisión

### D1 — El marco legal: la clínica es el **titular**, Dentdesk es el **encargado**

Esto no estaba escrito en ningún artefacto del proyecto, y es la decisión de la que cuelgan las demás.

| Rol | Quién | Qué le corresponde |
|---|---|---|
| **Titular del banco de datos** | **la clínica** (el tenant) | define finalidad y contenido del banco, el tratamiento y las medidas de seguridad |
| **Encargado del tratamiento** | **Dentdesk** (el operador del SaaS) | presta el servicio de tratamiento **por cuenta** del titular |
| **Titular de los datos personales** | **el paciente** | es el sujeto de los derechos del Título III |

⚠️ **Ojo con el vocabulario de la ley**: *"titular"* nombra **dos cosas distintas** —el titular del *banco* (la clínica) y el titular de los *datos* (el paciente)— y confundirlas invierte quién le debe qué a quién. Acá y en la matriz se escriben siempre completos.

**Del art. 30 de la Ley y del art. 31 del Reglamento salen tres reglas:**

1. **Los datos no pueden usarse con un fin distinto al del contrato.** Esto convierte la tercera regla de `ADR-0018` D5 —*ninguna funcionalidad monetiza el dato clínico*— de principio ético del COP a **obligación legal exigible**, y de paso la saca de *"sin decisión escrita"*: **queda decidida acá**. Ni benchmarking entre clínicas, ni venta de estadísticas, ni entrenamiento de modelos, mientras no esté en el contrato. Usar datos para otra finalidad es **infracción grave** (Regl. art. 133 num. 7).
2. **El encargado no puede transferir a terceros** salvo que la clínica lo autorice **y** el paciente consienta (Regl. art. 31.1). Aplica a sub-encargados de nube.
3. **Al terminar el servicio hay que devolver y suprimir.** Ver **D2**.

### D2 — Dar de baja un tenant: **devolver a la clínica, después borrar**. Máximo dos años

`ADR-0014` **D7** dejó esto como **Open Question 3**, literalmente *"cancelación del tenant — sin decisión tomada"*. El reglamento vigente no solo la cierra: **prescribe la secuencia**.

> **Art. 31.2** — *"El plazo para la conservación de datos personales al que hace referencia el artículo 30 de la Ley es como **máximo de dos (2) años** contados desde la finalización del último encargo realizado. La conservación (…) en un plazo mayor sólo puede realizarse (…) **porque así lo dispone expresamente una disposición normativa vigente**, para tal caso **los datos deben ser devueltos al responsable de tratamiento para su conservación mientras la obligación legal persista**. **La conservación indeterminada de los datos personales, por regla general, está prohibida.**"*

Las dos obligaciones que parecían chocar **obligan a personas distintas**, y el artículo las encaja:

| Obligación | De quién | Fuente |
|---|---|---|
| **Suprimir** al terminar el contrato, máximo 2 años después | **Dentdesk**, como encargado | Ley **art. 30** · Regl. **art. 31.2** |
| **Conservar** la historia clínica 20 años | **la clínica**, como titular | NTS 139 **5.3.1 (6) e** · COP **art. 33** |

```
 1. la clínica solicita la baja
        │
        ▼
 2. DEVOLUCIÓN del expediente completo a la clínica   ◀── Regl. art. 31.2
        │                                                 ADR-0014 D3 deja de ser opcional
        ▼
 3. acuse de recepción
        │
        ▼
 4. supresión en Dentdesk — a más tardar 2 años        ◀── Regl. art. 31.2
    desde el fin del encargo
```

**Borrar sin devolver deja a la clínica en incumplimiento de NTS 139** y le quita 20 años de expedientes. **No borrar nunca está prohibido de forma expresa**: *"la conservación indeterminada está prohibida"*. El paso 2 es lo que hace compatibles ambas obligaciones, y **es el reglamento el que lo ordena**, no una inferencia nuestra.

🔴 **Esto asciende `ADR-0014` D3 (exportación formal del expediente)** de *"mismo artefacto que el hash"*, sin urgencia, a **requisito de una operación que el producto necesita el día que se vaya el primer cliente** — y que hoy no existe.

### D3 — El secreto profesional pasa de deber ético a **condición de legalidad, sancionable**

El art. 14 numeral 6 de la Ley exime de pedir consentimiento para tratar datos de salud, **con condición expresa**:

> *"…siempre que dicho tratamiento sea realizado en establecimientos de salud o por profesionales en ciencias de la salud, **observando el secreto profesional**"*

Hoy **cualquier miembro del tenant ve toda historia clínica** — decisión explícita y temporal de `add-clinical-history`, diferida a `staff` (`ADR-0016` D2/D3). La cadena:

```
no se observa secreto profesional
        │
        ▼
la exención del art. 14 num. 6 no aplica
        │
        ▼
se tratan datos SENSIBLES sin consentimiento válido
        │
        ▼
Regl. art. 133 num. 3 — INFRACCIÓN GRAVE (>5 a 50 UIT)
```

Y hay una segunda vía, **independiente del consentimiento**:

> **Art. 133 num. 5** — es **infracción grave** *"Realizar tratamiento de datos personales **sensibles** incumpliendo las medidas de seguridad establecidas en la normativa sobre la materia."*

⚠️ **Sin exigir perjuicio.** Basta incumplir las medidas. Y si además hay exposición no autorizada, sube a **muy grave** (art. 134 num. 4, >50 a 100 UIT).

🔴 **`staff` cambia de prioridad, no solo de justificación.** El argumento era ético (COP arts. 74–77); ahora es una infracción tipificada.

### D4 — Consentimiento: **escrito, con firma electrónica válida, y revocable**

Los datos de salud son **sensibles** por definición expresa (Ley art. 2 num. 5). Eso activa reglas que `ADR-0016` D6 había decidido por buen criterio y ahora tienen artículo:

| Regla | § |
|---|---|
| Previo, informado, expreso e inequívoco | Ley **13.5** |
| Para datos sensibles, **además por escrito**, mediante *"firma manuscrita, digital, **electrónica** o cualquier otra modalidad que garantice la voluntad del titular"* | Regl. **art. 8** |
| **La carga de la prueba del consentimiento recae siempre en el titular del banco** (la clínica) | Regl. **art. 9** |
| Revocable **en cualquier momento**, con los mismos requisitos del otorgamiento | Ley **13.7** · Regl. **art. 10** |
| Menores y adolescentes actúan por sus representantes legales | Ley **13.3** |

**Cuatro consecuencias concretas:**

- ✅ **La captura del consentimiento dentro de Dentdesk es legalmente válida.** El art. 8 admite *"firma electrónica o cualquier otra modalidad que garantice la voluntad"* — **no exige certificado IOFE**. Esto desacopla el consentimiento de la firma digital que `ADR-0014` D2 pospuso: **son problemas distintos y el del consentimiento se puede resolver ya**.
- 🔴 **Hay que poder *probar* el consentimiento**, y la carga es de la clínica. Sin registrar **qué versión** del texto se firmó, cuándo y con qué mecanismo, no se prueba el *"informado"*. Eso es exactamente `ADR-0016` **D6**, que gana el mejor argumento que tenía: no es prolijidad, es carga probatoria.
- **La revocación exige la misma formalidad** que el otorgamiento. No es un botón sin artefacto.
- **`ADR-0007` (tutor de menores) gana respaldo directo** en el art. 13.3, además del Código Civil y el COP.

⚠️ **Siguen siendo dos consentimientos distintos** (`ADR-0018` D7.4): el **clínico** (autoriza el procedimiento) y el de **tratamiento de datos** (autoriza el manejo de la información). El proyecto modela uno solo.

### D5 — El log de accesos: `ADR-0016` **D4 queda cerrado**, y hay que ajustarlo

`ADR-0016` D4 decidió un registro de *"quién leyó qué historia, de qué paciente, cuándo"*, y `ADR-0018` lo dejó **sin respaldo normativo** porque la Ley no menciona logs. El reglamento vigente sí, y con precisión quirúrgica:

> **Art. 46 num. 3** — *"La generación y el mantenimiento de registros que provean evidencia de las interacciones con los datos lógicos, incluyendo para los fines de la trazabilidad, la información de cuentas de usuario con acceso al sistema, horas de inicio y cierre de sesión y acciones referidas a procesamiento, **visualización**, modificación, eliminación, importación y exportación de datos personales."*

✅ **`ADR-0016` D4 acertó**: *"visualización"* está en el texto. El registro de **lectura** es obligatorio, no una elección de diseño.

**Pero el reglamento agrega cuatro cosas que D4 no tenía, y una lo contradice:**

| Exigencia | § | Estado en `ADR-0016` D4 |
|---|---|---|
| Registrar **visualización**, además de modificación, eliminación, importación y **exportación** | 46.3 | ✅ cubierto |
| **Horas de inicio y cierre de sesión** por cuenta | 46.3 | ⚠️ no contemplado — es de sesión, no de dato |
| Generarse **de manera continua** y estar **disponibles de manera inmediata** | 46.3 | ⚠️ no contemplado |
| **Conservarse mínimo dos (2) años** | 46.3 | ⚠️ no contemplado |
| Tener **procedimiento de disposición, almacenamiento, transferencia y destrucción** | 46.3 | 🔴 **contradice** el *"rastro no borrable"* de D4 |

🔴 **El último punto corrige a D4**: el log **no es eterno**. Se conserva un mínimo de dos años y luego se dispone conforme a un procedimiento documentado. *"No borrable"* era más estricto que la norma, y lo más estricto no siempre es lo correcto: un log sin política de disposición es, él mismo, un banco de datos personales sin plazo de tratamiento — justo lo que el art. 31.2 prohíbe.

**El art. 46 exige además tres cosas de control de acceso que ningún artefacto menciona:**

- **Gestión del ciclo de vida del acceso**, desde el alta hasta la baja, *"incluyendo eventualidades periódicas como periodos vacacionales o permisos esporádicos"* (46.1.a).
- **Verificación periódica de privilegios, en un intervalo mínimo semestral** (46.1.c). Es una obligación **operativa con reloj**, no una función.
- **Planes de capacitación del personal** según sus roles (46.2).

⚠️ Y el **num. 4** prohíbe que personal no autorizado genere copias, y exige que el uso de **mensajería instantánea, correo o redes sociales no institucionales** esté *"debidamente aprobado y formalmente establecido"*. En una clínica dental, mandar una radiografía por WhatsApp es la vía natural: **es exactamente el riesgo que el artículo nombra**, y es política, no código.

### D6 — El **Documento de Seguridad**: un artefacto obligatorio que no existe

> **Art. 47.1** — *"El responsable del tratamiento de datos personales **debe contar con un documento de seguridad**, el cual debe ser aprobado formalmente y contar con **fecha cierta**."*

Contenido mínimo exigido:

| Debe contener | § |
|---|---|
| Procedimientos de **gestión de accesos**, **gestión de privilegios** y **verificación periódica** de los privilegios asignados | 47.1 |
| Políticas internas que contemplen el **ciclo de vida del dato**: obtención, uso y **posterior supresión** | 47.3 |
| Un **inventario de datos personales y de los sistemas** empleados, **especificando si se tratan datos sensibles** | 47.3 |
| Ser de **cumplimiento obligatorio para el personal** con acceso, que debe conocer las consecuencias de incumplirlo | 47.2 |
| Referencia sugerida: **NTP-ISO/IEC 27001 vigente** | 47.1 |

🔴 **No existe.** Y no es código: es un documento de gobernanza con fecha cierta. ✅ Lo bueno: **el inventario del 47.3 es parcialmente derivable del esquema** —las tablas, sus columnas y cuáles son de clase clínica ya están decididas en `ADR-0016` D5—, así que la parte más tediosa está medio hecha.

⚠️ **No se decide acá quién lo firma ni qué alcance tiene** —Dentdesk como encargado, cada clínica como titular, o ambos—. Es una decisión de producto y contrato que necesita su propio ADR.

### D7 — Notificación de incidentes de seguridad: **48 horas**, y para Dentdesk **inmediata**

Obligación nueva, ausente de todo artefacto, y con reloj.

| Quién avisa | A quién | Plazo | § |
|---|---|---|---|
| **Dentdesk** (encargado) | a la **clínica** | **de forma inmediata** | **art. 36** |
| La **clínica** (titular) | a la **ANPD** | **48 horas** desde tomar conocimiento | **art. 34.1** |
| La **clínica** | al **paciente** afectado, en lenguaje sencillo y claro | **48 horas** | **art. 34.3** |
| La **clínica** | además al **Centro Nacional de Seguridad Digital** si el incidente es en entorno digital | — | **art. 34.5** |

Se dispara *"cuando se trate de **datos sensibles**"* (34.1), o sea **siempre** en este producto. Y dos detalles que cambian el diseño del procedimiento:

- ⚠️ *"Esta obligación permanece **aun cuando** el responsable considere que tal incidente haya sido **subsanado o resuelto internamente**"* (34.1). **Arreglarlo rápido no exime de avisar.**
- Pasarse de 48 horas no es imposible, pero la notificación **debe ir acompañada de los motivos y el sustento probatorio** de la demora.
- **Art. 35**: todo incidente debe quedar **documentado** —hechos, efectos y medidas—, y esa documentación es lo que la ANPD usa para verificar cumplimiento.

🔴 **No comunicar un incidente es infracción grave** (art. 133 num. 12). Y el plazo de Dentdesk no es de 48 horas: es **inmediato**, porque somos el encargado.

### D8 — **Oficial de Datos Personales**: obligatorio para Dentdesk y para cada clínica

> **Art. 37.1** — deben designarlo el titular, el responsable **y el encargado** cuando *"(…) **3. realicen actividades principales o de giro de negocio que comprendan el tratamiento de datos sensibles**."*

**Dentdesk encaja por el numeral 3 sin margen de interpretación**: el giro del negocio *es* tratar historias clínicas. Las clínicas también. Y el numeral 2 lo repite por la vía de los datos sensibles.

| Obligación | § |
|---|---|
| **Designarlo** | 37.1 |
| **Publicar sus datos de contacto en lugar visible** para los titulares de datos | 37.4 |
| **Comunicarlos a la ANPD dentro de 15 días** de la designación o de cualquier actualización | 37.5 |
| Funciones mínimas: asesorar, **verificar el cumplimiento**, cooperar con la ANPD y ser su **punto de contacto** | 39.1 |

✅ **No exige contratar a nadie.** El art. 38.2 admite que sea *"una persona que desempeñe otras funciones"* y **incluso externa a la organización**. Lo que sí exige el 38.1 son **conocimientos acreditados** en la materia.

⚠️ **Y conecta con D7**: el art. 34.2 num. 2 obliga a que la notificación de incidente incluya *"el nombre y los datos de contacto del **Oficial de datos personales**"*. Sin Oficial designado, **no se puede notificar en forma** — las dos obligaciones están trabadas entre sí.

💡 **La evaluación de impacto (art. 40) es facultativa**, no obligatoria — *"De manera facultativa y previa al tratamiento…"*—, aunque el artículo la recomienda especialmente para datos sensibles y menores. Se anota para no confundirla con el Oficial, que sí es obligatorio.

### D9 — Cifrado: **en tránsito lo pide la norma; en reposo, no**

> **Art. 52** — el intercambio de datos *"hacia cualquier destino fuera de las instalaciones físicas"* procede solo con autorización del titular del banco, *"implementando las medidas necesarias (entre las que se encuentran el **cifrado de datos**, uso de **firmas y certificados digitales**, **checksum de verificación**, entre otros)"*.

| | Qué dice la norma | Efecto |
|---|---|---|
| **Cifrado en reposo** (`ADR-0016` **D5**) | **no está mandado** en ningún artículo | Sobrevive como **decisión de ingeniería**, sin el respaldo normativo que la matriz le atribuía. ✅ Confirmado contra dos textos: `AES-256` y `TLS 1.3` **no salen de la norma** |
| **Cifrado en tránsito / exportación** | **sí**, art. 52 | 🔴 Aplica a `ADR-0014` **D3** y a la devolución de **D2**: la exportación debe llevar **cifrado + checksum de verificación** |
| **Respaldos** | art. 48.1 num. 3: *"copias de respaldo **seguras y continuas** y **verificación de integridad**"* | 🔴 Cierra el hueco *"respaldos cifrados y recuperables — sin dueño técnico"* de la matriz: ahora tiene § y exige **verificar la integridad**, no solo hacer la copia |

💡 Nótese que el art. 52 menciona **firmas y certificados digitales** como medida de transferencia. Es un punto de apoyo lateral para `ADR-0014` **D2**, que se quedó sin fundamento COP en `ADR-0018` D4.

### D10 — Los derechos del paciente tienen **plazos duros**, y el más corto manda

> **Art. 69** — información: **8 días** · acceso: **20 días** · rectificación, cancelación y oposición: **10 días**. Contados desde el día siguiente de la solicitud.

⚠️ **Choca con NTS 139 y gana el más corto.** NTS 139 **5.3.3 r** da **5 días** para entregar copia de la historia clínica. Un pedido de copia del expediente es *derecho de acceso* para la Ley 29733 (20 días) y *copia de la historia* para NTS 139 (5 días). **La operación de exportación debe cumplir 5 días**, no 20: la norma sectorial es más exigente y es la que aplica al documento clínico.

🔴 **No atender el ejercicio material de los derechos es infracción grave** (art. 133 num. 1).

**Y falta un estado que el sistema no tiene: el bloqueo.** La Ley (art. 20, párrafo 3) obliga a bloquear los datos mientras se tramita una rectificación o supresión, *"quedando impedido de permitir que terceros accedan a ellos"*. No equivale a ningún estado existente:

| | Qué significa |
|---|---|
| `DRAFT` / `COMPLETED` (`ADR-0011`) | etapa del **documento** clínico |
| **bloqueo** (Ley art. 20) | los datos existen, pero **nadie externo accede** mientras se resuelve un reclamo del paciente |

⚠️ Choca con el gate `enabledForTreatment`: un expediente bloqueado no debería habilitar atención, pero tampoco está incompleto. **Se decide que existe y falta**; cómo se modela va en `update-clinical-history`.

### D11 — Inscripción en el Registro Nacional: obligación **de la clínica**, y es **gratuita**

> **Art. 42.1** — *"Las personas naturales o jurídicas del sector privado (…) que **creen, modifiquen o cancelen** bancos de datos personales están obligadas a tramitar la inscripción de dichos actos ante el Registro Nacional."*
> **Art. 43.3** — *"La inscripción (…) **es gratuita**, incluyendo la modificación y cancelación."*

**A quién le toca**: al **titular del banco**, o sea **a la clínica** (D1). Dentdesk figura como **encargado**.

**Decisión de producto, en dos partes:**

- **Dentdesk no inscribe nada por su cliente.** Es una declaración de la clínica sobre finalidad y domicilio propios. Asumirla sería responder por una declaración que no controlamos.
- ✅ **Pero Dentdesk debe darle a la clínica lo que necesita para inscribirse**, y eso **incluye algo que solo nosotros tenemos**: la **descripción técnica de las medidas de seguridad**. Va junto con qué datos trata el sistema, con qué finalidad, cuánto los conserva (20 años) y quién es el encargado. Es contenido de **onboarding y contrato**, no código — y se solapa casi por completo con el Documento de Seguridad de **D6**, así que se escriben juntos.

💡 **Es argumento comercial, no solo riesgo**: el cliente descubre una obligación que probablemente no sabía que tenía, el trámite **no cuesta dinero**, y el proveedor llega con la ficha resuelta.

### D12 — Se **revierte** `ADR-0018` D8, y se agrega la **prueba de vigencia**

`ADR-0018` **D8** decidió no versionar los textos oficiales —terceros, licencia no analizada, peso— aceptando a cambio una limitación declarada: *"las citas descansan en la lectura de la sesión, no en un artefacto del repo"*.

**Esa limitación se cobró dos cosas en una sola sesión**: hubo que descartar un resumen falso que nadie podría re-descartar sin el PDF, y un reglamento derogado que **ninguna lectura del documento habría detectado**.

**El corte no es "versionar o no": es por tamaño y tipo.**

| | Se versiona | No se versiona |
|---|---|---|
| **Qué** | leyes, decretos supremos, directivas | los PDF de las **NTS del MINSA** y el Código del COP |
| **Por qué** | decenas o pocos cientos de KB, publicación oficial abierta | cientos de páginas; el del COP es publicación de un colegio profesional |
| **Cómo se cita** | ruta en [`docs/compliance/normas/`](../compliance/normas/) | norma + instrumento + **fecha de consulta** en la cabecera del ADR |

**Y la regla nueva, que es la que más valor tiene:**

> 🔴 **Un texto oficial se verifica en TRES propiedades independientes: auténtico, completo y VIGENTE.** Un ✅ en las dos primeras no arrastra la tercera. La vigencia **no se puede comprobar leyendo el documento** — exige una fuente externa, y es obligatoria cuanto más viejo sea el texto.

**Un documento derogado no se borra**: se renombra con prefijo `DEROGADO-` y se conserva. Es la evidencia de qué se creyó y por qué.

## Consecuencias

**Tres gaps 🔴 nuevos, los tres sancionables hoy:**

| Gap | Qué falta | Tipificación |
|---|---|---|
| **Documento de Seguridad** (D6) | no existe; con fecha cierta, inventario de datos y procedimientos de acceso | art. 133 num. 5 — **grave** |
| **Procedimiento de incidentes** (D7) | no existe; aviso inmediato a la clínica, 48 h a la ANPD y al paciente | art. 133 num. 12 — **grave** |
| **Oficial de Datos Personales** (D8) | no designado, ni publicado, ni comunicado a la ANPD | traba D7: sin Oficial no se notifica en forma |

**Cambios sobre decisiones existentes:**

- ✅ **`ADR-0016` D4 se cierra** (D5): el log de accesos tiene § y su diseño acertó en lo esencial. Hay que **agregarle** horas de sesión, generación continua, disponibilidad inmediata y retención mínima de 2 años — y **quitarle** el *"no borrable"*, que era más estricto que la norma.
- 🔴 **`ADR-0014` D3 (exportación) asciende y D7 cierra su Open Question 3** (D2): la baja de un tenant exige devolver antes de borrar, con tope de 2 años, y *la conservación indeterminada está prohibida*.
- 🔴 **`staff` sube de prioridad** (D3): el secreto profesional es condición de legalidad, y su incumplimiento está tipificado como grave por dos vías independientes.
- ⚠️ **`ADR-0016` D5 pierde respaldo normativo y sobrevive como ingeniería** (D9). El cifrado obligatorio es el de **tránsito y exportación**, no el de reposo.
- ✅ **`ADR-0016` D6 gana el mejor argumento que tenía** (D4): la carga de la prueba del consentimiento es de la clínica, así que la versión firmada debe quedar registrada.
- ✅ **El consentimiento se desacopla de la firma digital** (D4): el art. 8 admite firma electrónica sin certificado IOFE. `ADR-0014` D2 puede seguir pospuesto sin bloquear el consentimiento.
- ✅ **Se cierra el hueco de respaldos** de la matriz (D9): art. 48.1 num. 3, con verificación de integridad.
- 🔴 **Falta el estado de bloqueo** (D10), que además choca con `enabledForTreatment`.
- ⚠️ **Dos artefactos de producto obligatorios y ninguno es código** (D6, D11): el Documento de Seguridad y la ficha de inscripción registral, que se solapan y se escriben juntos.
- ✅ **Ninguna capacidad construida cambia de comportamiento por este ADR.** Todo lo abierto es decisión pendiente, artefacto de gobernanza o requisito no cubierto.

## Qué queda pendiente

| Pendiente | Por qué | Prioridad |
|---|---|---|
| **Modificatorias de la Ley N°29733** | el texto versionado es el original de 2011; la denominación de la autoridad ya cambió | ⚠️ media |
| **Ley N°27269** (firma digital) | `ADR-0014` D2 quedó sin su otra fuente tras `ADR-0018` D4. **Menos urgente ahora**: D4 mostró que el consentimiento no la necesita | ⚠️ baja |
| **Lineamientos de la ANPD** sobre notificación de incidentes | el art. 34.7 la faculta a emitirlos; afectarían el procedimiento de D7 | ⚠️ baja |
| **Quién firma el Documento de Seguridad** y con qué alcance | decisión de producto y contrato (D6) | 🔴 **necesita ADR** |

## Alternativas descartadas

- **Usar el D.S. 003-2013-JUS porque traía lo que faltaba.** Descartada (D12): estaba derogado. Un texto no fundamenta nada por bien escrito que esté. Lo que sí se conservó fue la sospecha de que el reglamento vigente exigiría **al menos tanto** — y exige más.
- **Esperar a tener todo para escribir un solo ADR.** Descartada: D3 y D7 describen incumplimientos **vigentes y tipificados**. Lo que falta queda declarado **dentro** de este ADR en vez de producir un correctivo.
- **Apoyarse en la exención del art. 14 num. 6 y no pedir consentimiento de datos.** Descartada (D3/D4): el *"en circunstancia de riesgo"* admite lectura estrecha, NTS 139 exige el consentimiento escrito igual (Anexo 11), y el art. 8 del reglamento hace barata la captura electrónica. Construir sobre la exención sería elegir el camino frágil para ahorrar un formulario.
- **Inscribir el banco de datos de cada clínica desde Dentdesk.** Descartada (D11): es una declaración del titular sobre finalidad y domicilio propios.
- **Mantener el *"rastro no borrable"* de `ADR-0016` D4.** Descartada (D5): más estricto que la norma **y peor** — un log sin política de disposición es un banco de datos personales con conservación indeterminada, prohibida por el art. 31.2.
- **Tratar el bloqueo del art. 20 como un estado más del documento clínico.** Descartada (D10): es una **restricción de acceso con causa y duración**, no una etapa del documento.
- **Designar un Oficial de Datos Personales a tiempo completo.** Descartada (D8): el art. 38.2 admite función compartida e incluso persona externa. Lo exigible son **conocimientos acreditados**, no dedicación exclusiva.

## Lección registrada

El proyecto ya tenía dos reglas sobre citar normas. Esta sesión agrega la tercera, y muestra que son **independientes**:

| # | Regla | De dónde | Qué detecta | ¿Necesita fuente externa? |
|---|---|---|---|---|
| 1 | Un **análisis derivado** no es la norma | `ADR-0017` | un documento que *interpreta* | no |
| 2 | Un **requisito correcto no valida su cita** | `ADR-0018` | § fabricado bajo una afirmación verdadera | sí, el texto |
| 3 | 🔴 **Auténtico y completo no es lo mismo que VIGENTE** | este ADR | derecho que dejó de serlo | **sí, y no basta el texto** |

La tercera es la más difícil de aplicar, y por dos razones que conviene tener presentes:

**Primero, no deja rastro en el documento.** Un resumen falso se delata por sus propias costuras; un § inventado se cae al abrir la norma. **Un reglamento derogado no tiene ningún defecto**: es el texto exacto que se publicó, con su sello y su fecha. La única señal disponible es **la fecha**, y solo si a alguien se le ocurre preguntarse qué pasó después.

**Segundo, llega cuando ya bajaste la guardia.** El D.S. 003-2013-JUS apareció después de descartar un resumen falso, y **pasó con holgura la prueba que acababa de fallar el anterior**. Esa sensación de *"por fin el documento bueno"* es exactamente el momento de mayor riesgo: se había leído entero y se habían extraído diez decisiones antes de que a nadie se le ocurriera mirar si seguía vigente.

Y una cuarta observación, sobre revertir decisiones: **`ADR-0018` D8 se equivocó y duró cuatro días.** No porque el razonamiento fuera malo —sus tres argumentos eran ciertos— sino porque **aplicó una regla única a documentos que no se parecen**: una ley de 95 KB y una NTS de 142 páginas no son el mismo problema. Cuando una decisión acepta una limitación *"declarada"*, conviene preguntarse cuánto tarda esa limitación en cobrarse algo. Acá tardó menos que el propio ADR en llegar a `dev`.

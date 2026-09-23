# Textos oficiales de las normas

Esta carpeta guarda el **texto oficial** de las normas que el proyecto cita, para que las citas de los ADR y de [`../regulatory-matrix.md`](../regulatory-matrix.md) se puedan **re-verificar desde el repositorio** y no dependan de la lectura de una sesión.

> 🔴 **Un texto acá NO es automáticamente citable.** Hay que comprobar **tres** cosas, y son independientes: que sea **auténtico** (no un resumen), que esté **completo** (no un extracto) y que esté **VIGENTE** (no derogado). Esta carpeta ya contiene un documento que pasa las dos primeras y falla la tercera. Ver [`ADR-0019`](../../adr/0019-proteccion-de-datos-ley-29733.md).

## Qué hay

| Archivo | Norma | Auténtico y completo | **Vigencia** | Usable |
|---|---|---|---|---|
| `ley-29733-proteccion-datos-personales-2011.pdf` | **Ley N°29733**, Ley de Protección de Datos Personales | ✅ arts. 1–40, Título Preliminar + Títulos I–VII, 12 disposiciones complementarias, firmas de promulgación (02.07.2011) | ✅ **VIGENTE**. El D.S. 016-2024-JUS derogó su reglamento, **no la ley**. ⚠️ Texto original 2011, **sin modificatorias incorporadas** — la denominación de la autoridad cambió desde entonces | ✅ **sí**, es la fuente de [`ADR-0019`](../../adr/0019-proteccion-de-datos-ley-29733.md) |
| `DEROGADO-ds-003-2013-jus-reglamento-ley-29733-2013.pdf` | **D.S. N°003-2013-JUS**, reglamento | ✅ arts. 1–131, El Peruano 22.03.2013 | 🔴 **DEROGADO** por el **D.S. N°016-2024-JUS** (El Peruano, 30.11.2024), en vigor desde el **30.03.2025** | 🔴 **NO**. Se conserva como evidencia del episodio, no como fuente |
| `Decreto-Supremo-016-2024-JUS-LPDerecho.pdf` | **D.S. N°016-2024-JUS**, reglamento **vigente** | ✅ arts. 1–135, El Peruano 30.11.2024 | ✅ **VIGENTE** desde el **30.03.2025**. Deroga expresamente al D.S. 003-2013-JUS | ✅ **sí**, es la segunda fuente de [`ADR-0019`](../../adr/0019-proteccion-de-datos-ley-29733.md) |
| `directiva-seguridad-anpdp-2013-ESCANEADA-SIN-TEXTO.pdf` | **Directiva de Seguridad de la Información Administrada por los Bancos de Datos Personales** (R.D. N°019-2013-JUS/DGPDP) | ⚠️ 38 páginas **escaneadas como imagen, sin capa de texto** — `pdftotext` extrae 38 bytes, uno por salto de página | ⚠️ de 2013, escrita sobre el reglamento derogado. El D.S. 016-2024-JUS subió las obligaciones de seguridad al propio texto reglamentario | 🔴 **NO**, por partida doble: ilegible y desplazada |

## Qué falta

| Norma | Por qué importa | Estado |
|---|---|---|
| **Modificatorias de la Ley N°29733** | El texto versionado es el original de 2011 | ⚠️ sin verificar |
| **Ley N°27269** + D.S. N°052-2008-PCM — firma digital | Tras `ADR-0018` D4 es la **única** fuente de la firma digital: el COP no la menciona en ningún artículo | ⚠️ pendiente |
| **NTS N°139-MINSA/2018/DGAIN** · **NTS N°188-MINSA/DGIESP-2022** · **Código de Ética del COP (2024)** | Leídas y usadas en `ADR-0017` y `ADR-0018`, **no versionadas** — ver *Qué se versiona* | ⚠️ citadas por fecha de consulta |

## Qué se versiona y qué no

| | Se versiona | No se versiona |
|---|---|---|
| **Qué** | Leyes, decretos supremos y directivas: documentos **chicos** (decenas o pocos cientos de KB) de **publicación oficial abierta** | Los PDF de las **NTS del MINSA** y el Código del COP: **cientos de páginas**, y el del COP es publicación de un colegio profesional |
| **Cómo se cita lo no versionado** | por ruta en esta carpeta | norma + instrumento de aprobación + **fecha de consulta**, en la cabecera del ADR que la usó |

⚠️ **Consecuencia asumida:** las citas de NTS 139, NTS 188 y el COP **siguen sin ser re-verificables desde el repo**. La carpeta reduce el problema, no lo elimina.

## Reglas

1. **La tabla de arriba declara las tres propiedades por separado** — auténtico, completo, vigente. Un ✅ en las dos primeras no arrastra la tercera, y confundirlas es lo que casi mete diez decisiones basadas en un reglamento derogado (`ADR-0019`).
2. **Un documento derogado no se borra: se renombra con el prefijo `DEROGADO-`** y se conserva. Es la evidencia de qué se creyó y por qué — y en este caso, de por qué existe la regla.
3. **El nombre del archivo lleva el número de norma y el año del texto**, no el nombre del archivo de origen. Si llega una versión con modificatorias incorporadas, entra como archivo nuevo — **el texto viejo no se sobrescribe**, porque los ADR ya escritos citan contra él.
4. **Un PDF acá no convierte en verificado lo que nadie leyó.** La fila de la matriz se cierra citando el artículo, no la existencia del archivo.
5. **Antes de citar, comprobar la vigencia contra una fuente externa.** El documento no la declara: un texto derogado se ve idéntico al día que se publicó. Cuanto más viejo, más obligatoria la comprobación — el reglamento derogado tenía **trece años**.

## Por qué existe esta carpeta

`ADR-0018` **D8** decidió **no versionar** los textos oficiales —documentos de terceros, licencia no analizada, peso— y aceptó a cambio una limitación declarada: *"las citas descansan en la lectura de la sesión, no en un artefacto del repo"*.

Esa limitación mostró su costo a los pocos días. Para llegar al texto de la Ley 29733 hubo que **descartar primero un resumen** que corrompía el artículo del consentimiento (*"es mediato"* por *"debe mediar"*) y renumeraba los títulos. Solo el PDF permitió distinguirlos — y sin versionarlo, la próxima sesión repetiría el descarte desde cero.

Los tres argumentos de D8 se sostienen para las NTS de cientos de páginas. **Ninguno se sostiene para una ley de 95 KB de publicación oficial abierta.** De ahí el corte por tamaño y tipo de documento, en vez de una regla única para todo.

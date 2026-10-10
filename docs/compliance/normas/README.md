# Textos oficiales de las normas

Esta carpeta guarda el **texto oficial** de las normas que el proyecto cita, para que las citas de los ADR y de [`../regulatory-matrix.md`](../regulatory-matrix.md) se puedan **re-verificar desde el repositorio** y no dependan de la lectura de una sesión.

> 🔴 **Un texto acá NO es automáticamente citable.** Hay que comprobar **tres** cosas, y son independientes: que sea **auténtico** (no un resumen), que esté **completo** (no un extracto) y que esté **VIGENTE** (no derogado). Esta carpeta ya contiene un documento que pasa las dos primeras y falla la tercera. Ver [`ADR-0019`](../../adr/0019-proteccion-de-datos-ley-29733.md).

## Qué hay

| Archivo | Norma | Auténtico y completo | **Vigencia** | Usable |
|---|---|---|---|---|
| `ley-29733-proteccion-datos-personales-2011.pdf` | **Ley N°29733**, Ley de Protección de Datos Personales | ✅ arts. 1–40, Título Preliminar + Títulos I–VII, 12 disposiciones complementarias, firmas de promulgación (02.07.2011) | ✅ **VIGENTE**. El D.S. 016-2024-JUS derogó su reglamento, **no la ley**. ⚠️ Texto original 2011, **sin modificatorias incorporadas** — la denominación de la autoridad cambió desde entonces | ✅ **sí**, es la fuente de [`ADR-0019`](../../adr/0019-proteccion-de-datos-ley-29733.md) |
| `DEROGADO-ds-003-2013-jus-reglamento-ley-29733-2013.pdf` | **D.S. N°003-2013-JUS**, reglamento | ✅ arts. 1–131, El Peruano 22.03.2013 | 🔴 **DEROGADO** por el **D.S. N°016-2024-JUS** (El Peruano, 30.11.2024), en vigor desde el **30.03.2025** | 🔴 **NO**. Se conserva como evidencia del episodio, no como fuente |
| `Decreto-Supremo-016-2024-JUS-LPDerecho.pdf` | **D.S. N°016-2024-JUS**, reglamento **vigente** | ✅ **Título Preliminar con artículos en ROMANOS** (I–VII) + arts. **1–135** en arábigos, El Peruano 30.11.2024. ⚠️ Un barrido que solo busque `Artículo <número>` **se saltea el Título Preliminar**, donde viven el ámbito de aplicación y las definiciones | ✅ **VIGENTE** desde el **30.03.2025**. Deroga expresamente al D.S. 003-2013-JUS | ✅ **sí**, es la segunda fuente de [`ADR-0019`](../../adr/0019-proteccion-de-datos-ley-29733.md) |
| `nts-188-odontograma-rm-559-2022-ESCANEADA.pdf` | **NTS N°188-MINSA/DGIESP-2022**, uso del Odontograma (RM N°559-2022/MINSA) | ⚠️ **24 páginas escaneadas, sin capa de texto** (`pdftotext` extrae 24 bytes). ✅ **Legible por extracción de imágenes** — ver *Cómo leer un PDF escaneado*. Contiene el catálogo **6.1.1–6.1.38** | ✅ **VIGENTE**. Deroga la NTS N°150 (RM N°595-2019) | ✅ **sí** — fuente de [`ADR-0017`](../../adr/0017-catalogo-odontograma-nts-188.md). ⚠️ **El catálogo necesita re-verificación completa entrada por entrada** |
| `directiva-seguridad-anpdp-2013-ESCANEADA-SIN-TEXTO.pdf` | **Directiva de Seguridad de la Información Administrada por los Bancos de Datos Personales** (R.D. N°019-2013-JUS/DGPDP) | ⚠️ 38 páginas **escaneadas como imagen, sin capa de texto** — `pdftotext` extrae 38 bytes, uno por salto de página | ⚠️ de 2013, escrita sobre el reglamento derogado. El D.S. 016-2024-JUS subió las obligaciones de seguridad al propio texto reglamentario | 🔴 **NO**, por partida doble: ilegible y desplazada |
| `nts-139-historia-clinica-rm-214-2018.pdf` | **NTS N°139-MINSA/2018/DGAIN**, Gestión de la Historia Clínica (RM N°214-2018/MINSA) | ✅ texto con capa de texto, completo con anexos. ⚠️ **No** trae incorporada su modificatoria (va aparte, abajo) | ✅ **VIGENTE**, modificada por la RM N°265-2018/MINSA | ✅ **sí** — fuente de [`ADR-0018`](../../adr/0018-cumplimiento-nts-139-y-cop.md) y [`ADR-0020`](../../adr/0020-nota-de-consulta.md). Versionada el 2026-10-09 (antes se citaba por fecha de consulta) |
| `rm-265-2018-minsa-modifica-nts-139-ESCANEADA.pdf` | **RM N°265-2018/MINSA**, modifica la NTS N°139 | ⚠️ **escaneada (JBIG2)**: no sale por `pypdf` ni por extracción de imágenes sin `jbig2dec`. Leída por capturas de pantalla el 2026-10-09. 3 páginas: redefine *"Acto de Salud"* y agrega el §7.4 | ✅ **VIGENTE** | ✅ **sí** — [`ADR-0020`](../../adr/0020-nota-de-consulta.md) (no toca las fichas ni la nota) |
| `ds-009-2017-sa-reglamento-ley-30024-renhice.pdf` | **D.S. N°009-2017-SA**, Reglamento de la Ley N°30024 (RENHICE) | ✅ capa de texto, completo | ✅ **VIGENTE** (no se encontró derogatoria; ⚠️ verificar contra fuente externa antes de una auditoría) | ✅ **sí** — arts. **32, 37, 46, 47** en [`ADR-0020`](../../adr/0020-nota-de-consulta.md) |
| `rm-576-2011-minsa-aprueba-directiva-183-SIN-DIRECTIVA.pdf` | **RM N°576-2011/MINSA**, aprueba la Directiva N°183-MINSA/OGEI-V.01 | 🔴 **solo la resolución** (2 páginas de El Peruano), **sin el texto de la Directiva** | ⚠️ sin verificar | 🔴 **NO** para el contenido de la Directiva: lo único citable es que la aprobó |
| `ley-27269-firmas-certificados-digitales-mod-ley-27310.pdf` | **Ley N°27269**, Firmas y Certificados Digitales | ✅ capa de texto, El Peruano 28.05.2000. ⚠️ El nombre de origen dice *"modificada por Ley 27310"*: **no se comprobó** que la modificatoria esté incorporada | ⚠️ vigente según su uso actual, sin verificar | ⚠️ con cautela — falta su reglamento (D.S. N°052-2008-PCM) |
| `codigo-etica-deontologia-cop-2024.pdf` | **Código de Ética y Deontología del COP** (Resolución N°01-02.SOCN.COP.2024, 16.02.2024) | ✅ capa de texto, 104 artículos | ✅ **VIGENTE** | ✅ **sí** — fuente de [`ADR-0018`](../../adr/0018-cumplimiento-nts-139-y-cop.md). Versionado el 2026-10-09 |

## Qué falta

| Norma | Por qué importa | Estado |
|---|---|---|
| **Modificatorias de la Ley N°29733** | El texto versionado es el original de 2011 | ⚠️ sin verificar |
| **D.S. N°052-2008-PCM** — reglamento de la Ley de Firmas Digitales | La ley sola no fija los requisitos técnicos de la firma | ⚠️ pendiente |
| **Directiva N°183-MINSA/OGEI-V.01** (texto) | Aprobada por la RM 576-2011; solo está la resolución | ⚠️ pendiente |
| **RM N°164-2025/MINSA** (Directiva 373, acreditación de SIHCE) | Condiciona el valor legal de la HC electrónica (NTS 139 §5.3.3 l) | ⚠️ pendiente |

## Qué se versiona y qué no

**Desde el 2026-10-09 se versionan todos los textos oficiales que el proyecto cita**, NTS y Código del COP incluidos (decisión del usuario en `update-clinical-history`). La regla anterior —versionar solo leyes y decretos chicos, y citar las NTS por fecha de consulta— ya tenía una excepción (la NTS 188, 9 MB) y dejaba las citas de `ADR-0018` sin forma de re-verificarse desde el repo. El costo en peso (~15 MB en total) es menor que el de otra cita que nadie puede comprobar.

Sigue sin versionarse lo que **no** es texto oficial: resúmenes, análisis derivados, transcripciones.

## Reglas

1. **La tabla de arriba declara las tres propiedades por separado** — auténtico, completo, vigente. Un ✅ en las dos primeras no arrastra la tercera, y confundirlas es lo que casi mete diez decisiones basadas en un reglamento derogado (`ADR-0019`).
2. **Un documento derogado no se borra: se renombra con el prefijo `DEROGADO-`** y se conserva. Es la evidencia de qué se creyó y por qué — y en este caso, de por qué existe la regla.
3. **El nombre del archivo lleva el número de norma y el año del texto**, no el nombre del archivo de origen. Si llega una versión con modificatorias incorporadas, entra como archivo nuevo — **el texto viejo no se sobrescribe**, porque los ADR ya escritos citan contra él.
4. **Un PDF acá no convierte en verificado lo que nadie leyó.** La fila de la matriz se cierra citando el artículo, no la existencia del archivo.
5. **Comprobar el esquema de numeración antes de barrer el texto.** El D.S. 016-2024-JUS numera su Título Preliminar en **romanos** y el resto en arábigos. Buscar solo `Artículo <número>` deja fuera el ámbito de aplicación y las definiciones — pasó en esta sesión, y produjo una cita al artículo equivocado.
6. **Antes de citar, comprobar la vigencia contra una fuente externa.** El documento no la declara: un texto derogado se ve idéntico al día que se publicó. Cuanto más viejo, más obligatoria la comprobación — el reglamento derogado tenía **trece años**.

## Cómo leer un PDF escaneado

Dos de los textos de esta carpeta son **escaneos sin capa de texto**. `pdftotext` devuelve un byte por página y `pypdf.extract_text()` devuelve vacío. Eso **no significa que no se puedan leer**.

Un escaneo lleva cada página como **una imagen incrustada**. Se extrae así:

```python
import io, os, pypdf
r = pypdf.PdfReader('docs/compliance/normas/<archivo>.pdf')
for i, pg in enumerate(r.pages, start=1):
    for j, im in enumerate(pg.images):
        io.open('salida/pag%02d_%d%s' % (i, j, os.path.splitext(im.name)[1]), 'wb').write(im.data)
```

Y las imágenes resultantes **sí** se leen. En NTS 188 el texto sale rotado 90° y aun así es legible.

⚠️ **Antes de declarar ilegible un PDF, probar esto.** La `directiva-seguridad-anpdp-2013` se dio por ilegible en `ADR-0019` sin intentarlo; puede que sí se pueda leer por esta vía.

⚠️ **No hay OCR en este entorno** (no está `tesseract`, y de poppler solo está `pdftotext.exe`, falta `pdftoppm`). Así que el texto **no se puede buscar con `grep`**: hay que localizar el numeral por posición, página por página. En NTS 188 el catálogo va a ~3 entradas por página desde la página 8.

## Por qué existe esta carpeta

`ADR-0018` **D8** decidió **no versionar** los textos oficiales —documentos de terceros, licencia no analizada, peso— y aceptó a cambio una limitación declarada: *"las citas descansan en la lectura de la sesión, no en un artefacto del repo"*.

Esa limitación mostró su costo a los pocos días. Para llegar al texto de la Ley 29733 hubo que **descartar primero un resumen** que corrompía el artículo del consentimiento (*"es mediato"* por *"debe mediar"*) y renumeraba los títulos. Solo el PDF permitió distinguirlos — y sin versionarlo, la próxima sesión repetiría el descarte desde cero.

Los tres argumentos de D8 se sostienen para las NTS de cientos de páginas. **Ninguno se sostiene para una ley de 95 KB de publicación oficial abierta.** De ahí el corte por tamaño y tipo de documento, en vez de una regla única para todo.

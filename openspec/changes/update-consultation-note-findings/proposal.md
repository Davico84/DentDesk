## Why

En la nota de consulta, el odontólogo vuelve a escribir a mano lo que ya está en el odontograma: la pieza, la cara y el
hallazgo que motiva la atención, y después busca el diagnóstico CIE10 desde cero. Lo que se ve en **rojo** en el
odontograma (NTS 188: "por hacer / patológico") es justamente lo que una consulta atiende. Elegirlo en vez de tipearlo
ahorra trabajo, evita errores de pieza y deja registrado **qué hallazgo atendió cada consulta**, que es lo que necesita
el odontograma de evolución (fase 2 B) para decir después "cómo quedó".

## What Changes

- **Hallazgos del odontograma en la nota abierta**: un panel lista los hallazgos del odontograma del paciente. Arriba,
  como **pendientes**, los que se ven rojos (siempre rojos, o según estado en mal estado); debajo, plegados, los demás.
  Cada pendiente muestra si ya se atendió en una nota anterior.
- **Vínculo guardado**: marcar un hallazgo como atendido en la nota lo registra en el servidor junto con un **texto
  propio** (pieza, cara, hallazgo, estado) tomado del odontograma en ese momento, y agrega esa línea al examen de la
  nota, editable. La nota sellada se lee sola y el vínculo queda inmutable con ella. Se puede quitar mientras la nota
  esté abierta.
- **Diagnóstico propuesto desde el hallazgo**: desde un hallazgo atendido se abre "Agregar diagnóstico" con la
  ubicación ya cargada (pieza FDI cuando es de cara o de pieza; general cuando es entre piezas, rango o arco) y, para
  los hallazgos siempre rojos, **códigos CIE10 sugeridos** de una tabla validada por el odontólogo. **Siempre** se
  puede buscar cualquier código del catálogo; el código final lo elige el odontólogo.
- **Una nota con hallazgos atendidos no está vacía**: deja de poder descartarse, como pasa con diagnósticos y
  antecedentes.

## Capabilities

### New Capabilities
<!-- Ninguna. -->

### Modified Capabilities
- `clinical-history`: la nota de consulta gana los hallazgos atendidos (vínculo con el odontograma, inmutable al sellar)
  y el diagnóstico propuesto desde un hallazgo; el descarte de una nota abierta cuenta los hallazgos atendidos como
  contenido. Las reglas de diagnóstico (CIE10 del catálogo, P/D/R, regla del `R`) no cambian.

## Non-goals

- **Odontograma de evolución** (corte 2, `update-dental-chart`): registrar "cómo quedó" un hallazgo tratado y que deje
  de verse rojo. Acá el hallazgo sigue rojo aunque se haya atendido; el panel solo muestra en qué nota se atendió.
- **Iniciar la nota desde el odontograma** ("Atender en la nota de hoy" al tocar un hallazgo): camino inverso, otro
  change si hace falta.
- **Mapeo completo hallazgo → CIE10**: solo los hallazgos patológicos siempre rojos (6 de los 8 tipos siempre rojos)
  tienen sugerencias. La restauración temporal y la corona temporal (tratamientos provisionales) y los que se ven rojos
  por mal estado (un trabajo previo que falló, no una enfermedad) no sugieren código; el buscador sigue disponible.
- Diagnóstico con cara, rango o espacio entre piezas: el diagnóstico sigue admitiendo una sola pieza FDI
  (`clinical-history`, "Ubicación del diagnóstico por pieza FDI").

## Impact

- **Backend**: tabla nueva de hallazgos atendidos por nota (FK compuesta por clínica, RLS como el resto de la nota,
  guarda de inmutabilidad al sellar); endpoints para vincular y desvincular y la lista en la respuesta de la nota;
  endpoint de sugerencias CIE10 por tipo de hallazgo, verificadas contra el catálogo. Ver `docs/backend.md`.
- **Frontend**: panel de hallazgos en `OpenNoteEditor`, hallazgos atendidos en la nota sellada (`SealedNoteCard`),
  `DiagnosisForm` con ubicación y sugerencias precargadas sobre el buscador existente (`Cie10SearchField`).
  Regeneración del cliente (orval). Ver `docs/frontend.md`.
- **Dominio**: la nota de consulta referencia hallazgos del odontograma (`docs/domain.md`, al archivar).
- Sin cambios en `dental-chart`: el odontograma no se modifica, solo se lee.

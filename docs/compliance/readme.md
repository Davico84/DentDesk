# docs/compliance/README.md

## 1. PROPÓSITO DE LA CARPETA COMPLIANCE

El propósito de la carpeta `docs/compliance/` es centralizar, estructurar y salvaguardar la documentación técnica y las evidencias de cumplimiento que alinean el código fuente y las reglas de dominio del Software como Servicio (SaaS) con el marco legal sanitario del Perú.

En el sector salud peruano, la Historia Clínica Electrónica es considerada un documento médico-legal de custodia obligatoria. Este repositorio de gobernanza actúa como el nexo que traduce las normas imperativas —emitidas por el Ministerio de Salud (MINSA), la Superintendencia Nacional de Salud (SUSALUD) y el Colegio Odontológico del Perú (COP)— en aserciones de ingeniería de software verificables. Esta carpeta permite demostrar ante una auditoría regulatoria, forense o de certificación de interoperabilidad que cada decisión de diseño, restricción de base de datos e interfaz gráfica responde directamente a una exigencia de la ley peruana.

---

## 2. RELACIONES DE TRAZABILIDAD Y GOBERNANZA

La carpeta de cumplimiento no funciona como una isla de documentación estática; es el núcleo que orquesta tres pilares metodológicos del ciclo de vida del software (SDLC) para garantizar que el SaaS sea auditable de forma automatizada.

┌─────────────────────────────┐│      docs/compliance/       ││   (Catálogo de Requisitos)  │└──────────────┬──────────────┘│┌───────────────────────┼───────────────────────┐▼                       ▼                       ▼┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐│      ADRs       │     │    OpenSpec     │     │     Testing     ││ (Justificación) │     │ (Contrato API)  │     │  (Verificación) │└─────────────────┘     └─────────────────┘     └─────────────────┘
### 2.1 Relación con ADR (Architectural Decision Records)
Cada vez que una disposición legal restringe el comportamiento técnico de la plataforma (por ejemplo, prohibir las sentencias `DELETE` o exigir el congelamiento atómico de registros a las 23:59:59), el equipo de arquitectura emite un *Architectural Decision Record* (ADR). 
*   **El enlace:** Las especificaciones de cumplimiento en esta carpeta referencian el identificador del ADR correspondiente (ej. `ADR-0012`). El ADR proporciona el contexto de ingeniería, las alternativas evaluadas y el impacto en la infraestructura, mientras que el documento de cumplimiento resguarda el sustento legal y el artículo exacto del diario oficial *El Peruano* que impuso dicha arquitectura.

### 2.2 Relación con OpenSpec (OpenAPI / AsyncAPI Specifications)
Las normas sanitarias peruanas imponen restricciones estrictas en los payloads y contratos de datos, como los campos mínimos obligatorios de filiación según la NTS N° 139-MINSA o la estructura de coordenadas del odontograma bajo la NTS N° 188-MINSA.
*   **El enlace:** Los esquemas de datos definidos en los archivos de especificación de la API (OpenAPI para REST, AsyncAPI para flujos dirigidos por eventos o coordinadores) implementan directamente las reglas y validaciones (`required`, `enum`, `patterns`) dictadas por los requisitos de esta carpeta. Las definiciones de cumplimiento aseguran que los contratos expuestos a terceros o componentes internos no omitan ni degraden la estructura de datos legal.

### 2.3 Relación con Testing (Pruebas Automatizadas de Comportamiento)
Una regla regulatoria que no se prueba automáticamente está destinada a romperse en el próximo despliegue.
*   **El enlace:** Cada requerimiento de cumplimiento se asocia biunívocamente con escenarios de prueba funcionales (escritos en Gherkin/Cucumber o frameworks de pruebas unitarias/integración). Por ejemplo, un requerimiento que exige el bloqueo de recetas por alergia cruzada (`REQ-ANA-02`) se traduce en una suite de tests automatizados que inyectan intencionalmente una orden prohibida para verificar que la API retorne un código de error controlado. El cumplimiento normativo de la plataforma se autoverifica en el pipeline de Integración Continua (CI/CD).

---

## 3. DEFINICIONES DEL MODELO DE DOMINIO DE CUMPLIMIENTO

Para mantener la rigurosidad conceptual en todo el sistema SaaS, el equipo de desarrollo y arquitectura adoptará y respetará las siguientes definiciones formales de dominio:

### 3.1 Requisito Normativo (Requirement Normativo)
Es una disposición legal, técnica o administrativa explícita, vigente e imperativa, emitida por una autoridad sanitaria o legislativa del Perú (MINSA, SUSALUD, Congreso, etc.), que afecta directamente el funcionamiento o el almacenamiento de datos del sistema de salud bucal. Su origen es externo y no es negociable.
*   *Ejemplo:* "El Odontograma debe utilizar el sistema numérico de la Federación Dental Internacional (FDI)" (NTS N° 188-MINSA/2022/DGIESP).

### 3.2 Requisito Derivado (Requirement Derivado)
Es una especificación de software técnica, functional o de infraestructura que el equipo de arquitectura traduce a partir de un Requisito Normativo para poder implementarlo en código ejecutable. Representa el "cómo" se materializa legalmente la norma en un entorno de software.
*   *Ejemplo:* "La base de datos debe validar mediante un *Check Constraint* o regla de validación de modelo que los identificadores de las piezas dentales caigan exclusivamente en los rangos numéricos discretos `[11..18]`, `[21..28]`, `[31..38]`, `[41..48]`, `[51..55]`, `[61..65]`, `[71..75]` y `[81..85]`".

### 3.3 Decisión Arquitectónica (Decision Arquitectónica)
La elección de diseño de software e infraestructura documentada e implementada para satisfacer uno o más Requisitos Derivados. Resuelve las restricciones técnicas que imponen las leyes sanitarias (por ejemplo, el manejo de concurrencia, la inmutabilidad de los datos sensibles y el aislamiento multi-usuario en un entorno SaaS).
*   *Ejemplo:* "Adoptar un modelo de base de datos relacional basado en un Libro de Asientos (*Append-Only Ledger*) para la tabla `evolucion_asientos`, donde se prohíbe el uso de comandos `UPDATE` / `DELETE` y se controla la cronología lineal mediante un índice compuesto por el ID del expediente y el *timestamp* del servidor".

### 3.4 Gap de Cumplimiento (Compliance Gap)
Cualquier discrepancia, omisión o falta de cobertura detectada entre un Requisito Normativo vigente en el Perú y el estado actual de la plataforma en producción. Un Gap de Cumplimiento representa un riesgo legal crítico que debe ser tipificado, priorizado en el *Backlog* técnico de forma inmediata y mitigado antes de cualquier auditoría sectorial.
*   *Ejemplo:* "El sistema en su versión v1.0.4 permite al odontólogo modificar notas de evolución de citas cerradas tres días atrás sin generar una Nota de Enmienda auditada, incumpliendo el numeral 5.4.1 literal i) de la NTS N° 139-MINSA".

---

## 4. FLUJO DE TRAZABILIDAD (LINEALIDAD OPERATIVA)

El desarrollo del SaaS se rige por un flujo de trazabilidad descendente (*top-down*). Ninguna línea de código clínico o restricción de base de datos puede existir en la plataforma sin una herencia regulatoria demostrable. El flujo opera bajo el siguiente pipeline lógico:

Normatividad Peruana (MINSA / SUSALUD / COP / Leyes)│▼Architectural Decision Record (ADR)│▼Specification (Contratos OpenAPI / Esquemas de BD)│▼Scenario (Criterios de Aceptación Gherkin / BDD)│▼Test (Pruebas Unitarias, de Integración y Regresión)│▼Código Fuente (Implementación e Inmutabilidad en Producción)
1.  **Norma:** El punto de partida legal. El analista normativo identifica una regulación oficial (ej. Obligación de conservar las historias clínicas por 15 años bajo la NTS N° 139-MINSA/2018/DGAIN).
2.  **ADR:** El arquitecto de software evalúa la norma y redacta un registro de decisión arquitectónica (ej. Configurar políticas de retención de datos en la capa de persistencia y bloquear sentencias destructivas a nivel de ORM/Base de datos).
3.  **Specification:** El ingeniero traduce el ADR en contratos técnicos verificables. Se crean los esquemas OpenAPI de solo lectura, restricciones de tipo estructurales y definiciones de tablas relacionales de agregación exclusivamente secuencial (`Append-Only`).
4.  **Scenario:** El ingeniero de calidad (QA) o el desarrollador define los criterios de aceptación en lenguaje natural estructurado utilizando patrones de comportamiento (ej. *Given* un expediente clínico, *When* transcurran menos de 15 años desde su última evolución, *Then* la API de administración debe denegar y rechazar cualquier petición de eliminación física).
5.  **Test:** El escenario se automatiza escribiendo código de prueba ejecutable en el ecosistema del framework. El test simula peticiones maliciosas o erróneas para forzar al sistema a demostrar que cumple con las barreras de protección de datos sensibles.
6.  **Código:** La lógica de negocio pura implementada en producción. El software queda blindado normativamente y el pipeline de CI/CD garantiza que la trazabilidad nunca se rompa de versión en versión.

---

## 5. MANTENIMIENTO Y GOBERNANZA DE LA DOCUMENTACIÓN

La documentación de cumplimiento alojada en esta carpeta debe tratarse con el mismo rigor metodológico que el código fuente de la aplicación:

*   **Gobernanza basada en Git:** Todo cambio en las regulaciones peruanas que obligue a modificar un requerimiento o flujo dentro de esta carpeta debe realizarse a través de un *Pull Request* (PR) formal. El PR debe incluir el código fuente modificado, los tests actualizados y la actualización de las referencias normativas en esta carpeta.
*   **Revisiones por Pares:** Ninguna modificación en la lógica de cumplimiento puede mezclarse a la rama principal (`main`) sin la aprobación expresa del Arquitecto Líder y del Especialista Legal/Regulatorio del producto.
*   **Prohibición de Referencias de Terceros:** Toda documentación de cumplimiento debe sustentarse únicamente en los documentos listados en la sección de fuentes permitidas. Queda estrictamente prohibido enlazar blogs informales, manuales de otros softwares comerciales o resúmenes de terceras personas jurídicas. Si un enlace oficial o artículo se encuentra en revisión legislativa por el Congreso o el MINSA, el registro debe marcarse explícitamente como `PENDIENTE DE VERIFICACIÓN` para evitar introducir falsos positivos de cumplimiento en la plataforma.

---

## 6. MAPEO OFICIAL DE FUENTES DE REGULACIÓN PERUANA

Para la construcción de los Requisitos Normativos y Derivados dentro de esta carpeta, el sistema se basa de forma exclusiva en el siguiente cuerpo legal de la República del Perú:

1.  **Norma Técnica de Salud para la Gestión de la Historia Clínica (NTS N° 139-MINSA/2018/DGAIN):** Aprobada mediante la Resolución Ministerial N° 214-2018/MINSA en el Diario Oficial *El Peruano*. Rige las estructuras de filiación, el modelo SOAP de evolución, la inmutabilidad de folios y los periodos obligatorios de conservación de 15 años.
2.  **Norma Técnica de Salud para el Uso del Odontograma (NTS N° 188-MINSA/DGIESP-2022):** Aprobada mediante la Resolución Ministerial N° 559-2022/MINSA en el Diario Oficial *El Peruano*. Rige de forma estricta los símbolos visuales, la numeración FDI, la regla binaria de colores (Azul/Rojo), los acrónimos clínicos estructurados (`IMP`, `MAC`, `MIC`, `DES`) y los campos concomitantes de Índice de Higiene Oral y Especificaciones.
3.  **Ley de Protección de Datos Personales (Ley N° 29733) y su Reglamento (Decreto Supremo N° 003-2013-JUS):** Publicados en el Diario Oficial *El Peruano*. Regulan el tratamiento, cifrado y trazabilidad obligatoria en el almacenamiento de Datos Sensibles (datos de salud y enfermedades del paciente).
4.  **Ley de Firmas y Certificados Digitales (Ley N° 27269):** Regula la validez legal del cierre explícito y el congelamiento criptográfico de documentos digitales por parte de los profesionales de la salud empleando certificados digitales o el DNI electrónico (DNIe).
5.  **Manuales de Registro y Codificación de la Atención en la Consulta Externa (HIS) - Estomatología:** Emitidos por la Oficina General de Tecnologías de la Información del MINSA. Rigen el catálogo estructurado de diagnósticos de la salud bucal (CIE-10 / CIE-11) y las categorizaciones obligatorias Presuntivo (P), Definitivo (D) y Repetitivo (R).

*Nota metodológica: Cualquier disposición legal que sea modificada o derogada en el futuro por el diario oficial El Peruano disparará automáticamente una revisión técnica del ciclo de vida del software registrado en este directorio.*

---
**Última revisión técnica de cumplimiento:** 3 de agosto de 2026.
**Estado de la documentación:** Validada contra las normas MINSA vigentes al 2026.
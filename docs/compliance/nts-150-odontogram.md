# Especificación de Cumplimiento: NTS N° 188-MINSA/DGIESP-2022 (Odontograma)

## 1. INTRODUCCIÓN Y CONTEXTO NORMATIVO

El presente documento constituye la especificación de ingeniería y el análisis de cumplimiento para el módulo del Odontograma, derivado de la Norma Técnica de Salud para el Uso del Odontograma (NTS N° 188-MINSA/DGIESP-2022), aprobada mediante la Resolución Ministerial N° 559-2022/MINSA y publicada en el Diario Oficial El Peruano.

> [!NOTE]
> **Nota de control de cambios y vigencia:** Este documento técnico reemplaza toda especificación basada en la antigua e inválida NTS N° 150-MINSA/2019/DGIESP (la cual fue expresamente derogada por la R.M. N° 559-2022/MINSA). Se adopta la denominación del archivo como `nts-150-odontogram.md` por consistencia con los mapas de rutas previos del repositorio del sistema de información, pero su contenido técnico traduce de forma estricta la norma legal vigente al año 2026 (NTS N° 188).

---

## 2. ANÁLISIS EXHAUSTIVO Y CATÁLOGO DE REQUISITOS DE SOFTWARE

### OD-001: Arquitectura de Dentición y Nomenclatura Estándar (Sistema FDI)
*   **Referencia Normativa:** NTS N° 188-MINSA/DGIESP-2022, Numeral 6.1 ("Estructura básica del Odontograma").
*   **Obligación:** El odontograma debe registrar y mostrar en una sola vista la totalidad de las piezas dentarias organizadas en cuatro cuadrantes para la dentición permanente (adultos) y cuatro cuadrantes para la dentición decidua (niños), utilizando el sistema numérico de la Federación Dental Internacional (FDI).
*   **Interpretación Clínica:** El dentista debe evaluar 32 piezas en el adulto (numeradas del 11 al 48) y 20 piezas en el paciente pediátrico (numeradas del 51 al 85), ordenadas en sentido horario desde el cuadrante superior derecho del paciente.
*   **Interpretación para Software:** El sistema debe inicializar el gráfico del odontograma renderizando dinámicamente un juego de componentes vectoriales (SVG) indexados de forma fija por los códigos numéricos de la FDI. No se permiten numeraciones secuenciales libres (1-32) ni sistemas anglosajones.
*   **Aggregate Afectado:** `DentalChartAggregate`
*   **Value Objects Afectados:** `ToothNumber` (Enum restrictivo), `Quadrant` (Rango 1-8).
*   **Invariantes Derivadas:** `ToothNumber` debe pertenecer única y exclusivamente a los rangos discretos enteros: `[11..18]`, `[21..28]`, `[31..38]`, `[41..48]` para adultos, o `[51..55]`, `[61..65]`, `[71..75]`, `[81..85]` para niños.
*   **Reglas de Validación:** Si el payload de entrada de la API intenta persistir un hallazgo en la pieza 29 o 92, la validación de dominio del Agregado debe rechazar la operación lanzando un error de inicialización anatómica.
*   **Impacto en API:** La ruta `POST /api/v1/dental-charts` valida que los IDs de los nodos hijos que representen piezas correspondan estrictamente a la lista de enteros FDI permitida.
*   **Impacto en Persistencia:** La base de datos almacena el número de diente en una columna de tipo entero pequeño (`SMALLINT`) o cadena corta con una restricción de verificación (`CHECK CONSTRAINT`) que valida los rangos FDI.
*   **Impacto en OpenSpec:** El archivo OpenAPI define el tipo de dato `ToothNumber` como un integer con la propiedad `enum` poblando los 52 códigos anatómicos reales.
*   **Escenarios de Prueba:**
    *   **Escenario 1 (Exitoso):** Dado un paciente adulto, cuando el sistema renderiza el odontograma inicial, entonces se exponen exactamente 32 estructuras dentales operativas.
    *   **Escenario 2 (Fallido):** Dado un payload con el identificador de diente 49, cuando la API procesa la petición de guardado, entonces devuelve un código de estado `400 Bad Request`.

### OD-002: Descomposición Geométrica de Caras de la Pieza Dental
*   **Referencia Normativa:** NTS N° 188-MINSA/DGIESP-2022, Numeral 6.1 y especificaciones gráficas de los anexos.
*   **Obligación:** Cada pieza dental representada en el esquema debe dividirse geométricamente en cinco superficies o caras independientes para el registro preciso de afecciones o tratamientos topográficos.
*   **Interpretación Clínica:** El odontólogo debe poder aislar y registrar si el daño o la resina se ubica en la cara de afuera (Vestibular), la de adentro (Palatina/Lingual), la que mira hacia la línea media (Mesial), la trasera (Distal) o la superficie de mordida (Oclusal/Incisal).
*   **Interpretación para Software:** Cada componente SVG de la pieza dental debe estar compuesto exactamente por 5 polígonos independientes (`<path>` o `<polygon>`), con selectores de ID diferenciados que escuchen eventos de clic y almacenen de forma aislada los estados de cada cara en la base de datos.
*   **Aggregate Afectado:** `DentalChartAggregate`
*   **Value Objects Afectados:** `ToothSurface` (Enum: `V`, `P`, `L`, `M`, `D`, `O`, `I`).
*   **Invariantes Derivadas:** Las caras `O` (Oclusal) e `I` (Incisal) son mutuamente excluyentes dependiendo del grupo de dientes: piezas posteriores (molares/premolares) admiten `O`, piezas anteriores (caninos/incisivos) admiten `I`.
*   **Reglas de Validación:** El backend rechaza la persistencia si se intenta enviar un hallazgo en la cara `O` (Oclusal) para la pieza 11 (Incisivo central superior), sustituyéndola obligatoriamente por la cara `I` o rechazando el payload por incompatibilidad anatómica.
*   **Impacto en API:** El payload acepta un array de superficies por pieza: `{"tooth": 46, "surfaces": ["O", "M"]}`.
*   **Impacto en Persistencia:** El estado de las caras se almacena en una estructura relacional normalizada de relación uno a muchos (`tooth_surfaces_states`) o mediante un mapa de bits/JSON estructurado inmutable dentro de la fila de la pieza.
*   **Impacto en OpenSpec:** Definición en OpenAPI del enumerado `SurfaceEnum` limitado a `['V','P','L','M','D','O','I']`.
*   **Escenarios de Prueba:**
    *   **Escenario 1:** Intentar registrar una lesión de caries en la cara `O` del diente 31 (Incisivo); el sistema debe rechazar la transacción lanzando un error de tipo `IncompatibleSurfaceException`.

### OD-003: Restricción del Código de Color Binario Estricto
*   **Referencia Normativa:** NTS N° 188-MINSA/DGIESP-2022, Numeral 6.2 ("Color y Registro de Datos").
*   **Obligación:** El registro y representación gráfica de los hallazgos en el odontograma debe realizarse utilizando de forma exclusiva dos colores: Rojo para patologías actuales o tratamientos en mal estado, y Azul para tratamientos existentes en buen estado.
*   **Interpretación Clínica:** Una caries activa o una corona rota se pintan de rojo; una resina bien adaptada o una endodoncia terminada con éxito se pintan de azul. No se permite el uso de amarillos, verdes ni violetas por preferencias estéticas del software.
*   **Interpretación para Software:** El CSS de la interfaz de usuario y las funciones de relleno estático de los polígonos vectoriales del odontograma quedan restringidos a dos variables de color globales e inmutables: `COLOR_PATHOLOGY = '#FF0000'` (Rojo) y `COLOR_RESTORED = '#0000FF'` (Azul).
*   **Aggregate Afectado:** `DentalChartAggregate`
*   **Value Objects Afectados:** `ClinicalColor` (Enum: `RED`, `BLUE`).
*   **Invariantes Derivadas:** Todo estado visual asociado a una patología activa (Caries, Fractura, Resto Radicular) se mapea con la propiedad de color `RED`. Todo estado asociado a un tratamiento finalizado óptimo (Resina_Ok, Prótesis_Ok) se mapea con la propiedad `BLUE`.
*   **Reglas de Validación:** La lógica del backend intercepta el guardado del estado y auto-asigna el color basándose en la naturaleza del `TipoCondicion`. Si el cliente envía un código de color arbitrario (ej. `#00FF00`), el backend lo ignora y sobreescribe según el catálogo maestro.
*   **Impacto en API:** La respuesta JSON de la API no devuelve códigos Hex de color; devuelve el estado semántico clínico de la afección (ej. `{"condition": "CARIES"}`), y la UI se encarga de aplicar el color normado.
*   **Impacto en Persistencia:** No se guardan cadenas de color en la base de datos. Se guarda el ID de la condición clínica que ya tiene una relación fija e invariable de color en el backend.
*   **Impacto en OpenSpec:** El esquema de respuesta del odontograma no incluye propiedades de color hexadecimales, forzando la separación de la lógica de presentación y de negocio.
*   **Escenarios de Prueba:**
    *   **Escenario 1:** Consultar el odontograma de un paciente con una caries registrada en la pieza 16; la aserción de la prueba automatizada de UI debe comprobar que el atributo `fill` del nodo SVG del polígonos corresponda exactamente a `#FF0000`.

### OD-004: Registro Homologado de Acrónimos Clínicos Estructurados
*   **Referencia Normativa:** NTS N° 188-MINSA/DGIESP-2022, Anexo N° 01 ("Símbolos y Especificaciones para el registro del Odontograma").
*   **Obligación:** Ciertas condiciones de diagnóstico y aparatología no se representan pintando las caras del diente; se registran obligatoriamente mediante acrónimos de tres letras escritos en miyúscula en los recuadros de texto de cada pieza dental.
*   **Interpretación Clínica:** Un implante se registra como `IMP` (Azul); una macrodoncia como `MAC` (Azul); una microdoncia como `MIC` (Azul); y una superficie desgastada como `DES` (Rojo).
*   **Interpretación para Software:** El esquema de la tabla de la pieza dental debe contener un campo dedicado a albergar etiquetas o acrónimos a nivel de recuadro (`box_condition`). La interfaz gráfica debe renderizar un elemento `<text>` centrado sobre la pieza o en su recuadro inferior con el string oficial en mayúsculas.
*   **Aggregate Afectado:** `ToothRecord` (Entidad hija de `DentalChartAggregate`).
*   **Value Objects Afectados:** `BoxAcronym` (Enum: `IMP`, `MAC`, `MIC`, `DES`, `E`, `S`).
*   **Invariantes Derivadas:** El campo `box_condition` solo acepta los strings definidos por la norma del 2022. No se permiten textos personalizados ni variaciones en minúsculas (ej. `imp` o `Implante` son inválidos).
*   **Reglas de Validación:** Validación por expresiones regulares (Regex: `^(IMP|MAC|MIC|DES|E|S)$`) en el setter del objeto de valor antes de persistir en la base de datos.
*   **Impacto en API:** El endpoint acepta la propiedad `box_acronym` dentro del payload de la pieza dental.
*   **Impacto en Persistencia:** Columna `box_acronym VARCHAR(3) NULL` con restricciones de verificación a nivel de motor SQL.
*   **Impacto en OpenSpec:** El objeto `ToothModification` documenta la propiedad `box_acronym` con un enum de strings de longitud exacta de 3 caracteres.
*   **Escenarios de Prueba:**
    *   **Escenario 1 (Fallido):** Enviar una solicitud HTTP con `box_acronym: "MACRO"`; el pipeline de validación de la API debe interceptar la petición y retornar un error `422 Unprocessable Entity` detallando el fallo de formato.

### OD-005: Precedencia y Exclusión por Pérdida de Estructura Dental (Pieza Ausente)
*   **Referencia Normativa:** NTS N° 188-MINSA/DGIESP-2022, Anexo N° 01, Símbolo de "Pieza Ausente" y "Pieza por Extraer".
*   **Obligación:** Las piezas que ya no existen en la cavidad oral o que están indicadas para su extracción se marcan con una equis (X) grande sobre toda la corona. Azul si ya no está; Rojo si se debe extraer.
*   **Interpretación Clínica:** Si un diente fue extraído en el pasado, el espacio está vacío (X Azul). Si el diente está destruido por infección, aún está en la boca pero debe sacarse (X Roja). En ninguno de los dos casos es físicamente posible realizar tratamientos superficiales sobre la corona.
*   **Interpretación para Software:** Cuando una pieza adopta el estado lógico de `AUSENTE` o `POR_EXTRAER`, el sistema debe inhabilitar de forma automática la captura de clics sobre las 5 caras individuales de esa pieza y purgar/bloquear cualquier diagnóstico de caries concurrente en la superficie de la corona.
*   **Aggregate Afectado:** `DentalChartAggregate`
*   **Value Objects Afectados:** `ToothVitalityState` (Enum: `PRESENTE`, `AUSENTE`, `POR_EXTRAER`).
*   **Invariantes Derivadas:** Si `ToothVitalityState == AUSENTE`, entonces el array de estados de las caras (`surfaces_states`) de esa pieza debe estar estrictamente vacío o nulo. No puede coexistir un diente extirpado con una caries activa.
*   **Reglas de Validación:** El método `registerCondition()` del Agregado evalúa la invariante de vitalidad. Si se intenta agregar una condición de cara sobre una pieza previamente marcada como ausente, se aborta la transacción arrojando un error de regla de negocio.
*   **Impacto en API:** Retorno de código de error de negocio estructurado en el JSON de respuesta ante violaciones de precedencia anatómica.
*   **Impacto en Persistencia:** Disparadores SQL (Triggers) de base de datos que validan la integridad de la fila de la pieza impidiendo la inserción de registros en la tabla de superficies si la columna de vitalidad indica ausencia.
*   **Impacto en OpenSpec:** Documentación de las excepciones de negocio de precedencia dentro de las respuestas de error de la especificación OpenAPI.
*   **Escenarios de Prueba:**
    *   **Escenario 1:** Marcar la pieza 46 como `AUSENTE` (X Azul). Intentar posteriormente enviar un evento de adición de caries en la cara mesial de la misma pieza 46. El sistema debe lanzar un `AnatomicalImpossibilityException`.

### OD-006: Registro de Movilidad Estructurada con Escala Arábiga
*   **Referencia Normativa:** NTS N° 188-MINSA/DGIESP-2022, Anexo N° 01, Símbolo de "Movilidad Dentaria".
*   **Obligación:** La movilidad dental se debe registrar empleando de manera obligatoria la letra `M` en color azul, acompañada del grado numérico de la afección expresado en números arábigos (1, 2 o 3).
*   **Interpretación Clínica:** El odontólogo evalúa el desplazamiento horizontal o vertical del diente debido a pérdida ósea periodontal, clasificándolo del 1 al 3. La norma del 2022 sustituye el uso antiguo de los números romanos por números arábigos.
*   **Interpretación para Software:** El backend debe rechazar registros de movilidad expresados como `M I`, `M II` o `M III`. Solo se validarán los patrones de texto `M1`, `M2` o `M3`, y la interfaz los pintará en color azul en la sección superior de la pieza.
*   **Aggregate Afectado:** `ToothRecord`
*   **Value Objects Afectados:** `MobilityGrade` (Enum: `M1`, `M2`, `M3`).
*   **Invariantes Derivadas:** La presencia de un valor en `MobilityGrade` obliga al sistema a requerir información complementaria en el campo de texto libre especificaciones para sustentar la clasificación clínica empleada.
*   **Reglas de Validación:** Validación estricta mediante enumerado en el código de backend. Se rechaza cualquier carácter que no coincida exactamente con los tres tokens permitidos.
*   **Impacto en API:** Campo `mobility: null | "M1" | "M2" | "M3"` dentro del objeto de estado de la pieza en la API.
*   **Impacto en Persistencia:** Mapeo de la columna en la base de datos como un tipo varchar de longitud de 2 caracteres (`VARCHAR(2)`).
*   **Impacto en OpenSpec:** El esquema OpenAPI declara la propiedad `mobility` bajo restricciones de tipo string con un patrón estricto de coincidencia.
*   **Escenarios de Prueba:**
    *   **Escenario 1 (Exitoso):** El odontólogo selecciona movilidad grado 2 para la pieza 31; la API procesa y persiste exitosamente el token "M2".

---

## 3. COMPORTAMIENTO GRÁFICO SINTETIZADO (MATRIZ DE TRADUCCIÓN)

Para la implementación directa en el Frontend de la interfaz del odontograma, el motor de renderizado debe seguir la siguiente lógica de estados basada en la NTS N° 188:

| ID Condición | Tipo Condición | Destino Geométrico | Representación Visual CSS / SVG | Color Normado |
| :--- | :--- | :--- | :--- | :--- |
| **COND_01** | Caries dental | Cara individual (V,P,L,M,D,O,I) | Relleno sólido del polígono de la cara afectada | Hex `#FF0000` (Rojo) |
| **COND_02** | Obturación (Resina/Amalgama) | Cara individual (V,P,L,M,D,O,I) | Relleno sólido del polígono de la cara afectada | Hex `#0000FF` (Azul) |
| **COND_03** | Pieza Ausente | Toda la pieza | Una equis (X) que tacha la totalidad del diente | Hex `#0000FF` (Azul) |
| **COND_04** | Pieza por Extraer | Toda la pieza | Una equis (X) que tacha la totalidad del diente | Hex `#FF0000` (Rojo) |
| **COND_05** | Implante Dental | Recuadro de la pieza | Texto centrado con el acrónimo IMP | Hex `#0000FF` (Azul) |
| **COND_06** | Superficie Desgastada | Cara + Recuadro | Relleno de cara + Texto acrónimo DES | Hex `#FF0000` (Rojo) |
| **COND_07** | Diastema | Espacio interdental | Dos líneas paralelas verticales (\|\|) | Hex `#0000FF` (Azul) |
| **COND_08** | Giroversión (Sana) | Raíz / Corona | Una flecha curva indicando el sentido de rotación | Hex `#0000FF` (Azul) |
| **COND_09** | Giroversión (Con patología) | Raíz / Corona | Una flecha curva indicando el sentido de rotación | Hex `#FF0000` (Rojo) |

---

## 4. DUDAS ABIERTAS (VACÍOS DE LA NORMA TÉCNICA)

Tras un análisis exhaustivo de la NTS N° 188-MINSA/DGIESP-2022, el equipo de arquitectura de software ha detectado los siguientes vacíos de especificación o ambigüedades normativas que no cuentan con una regla de diseño explícita en el documento legal peruano. Para cada caso, se documenta la **Decisión de Ingeniería Provisional** adoptada para el desarrollo de la versión v1 del SaaS:

### 4.1 Ambigüedad en la Superposición de Símbolos sobre Prótesis Fijas
*   **El Vacío:** La norma indica que una prótesis fija (puente dental) se dibuja uniendo las coronas de los dientes pilares con una línea recta horizontal (Azul si está bien; Roja si está mal). Sin embargo, no define el comportamiento visual si uno de los dientes pilares de ese puente desarrolla una caries en una de sus caras libres (ej. cara vestibular). ¿Se debe pintar la cara de rojo superponiéndose a la línea azul del puente, o esto anula el gráfico del puente?
*   **Decisión de Ingeniería Provisional (v1):** En la base de datos se maneja un modelo de capas por pieza. La prótesis fija se guarda como un atributo estructural del arco dental (Capa de Conectores), mientras que las caries se guardan en la tabla de superficies (Capa de Caras). Al renderizar el SVG, la línea del puente se dibuja por debajo del polígono de la pieza, permitiendo que la cara se pinte de rojo de forma superpuesta sin borrar la existencia del puente, garantizando que coexistan ambos hallazgos clínicos.

### 4.2 Falta de Restricción Digital para Piezas Supernumerarias Infinitas
*   **El Vacío:** La norma señala que un diente supernumerario (un diente extra no contemplado en los 52 de la anatomía estándar) se registra con la letra S en un círculo, localizado entre las piezas donde erupciona. Al pasar esto a un software, la norma no limita cuántos supernumerarios puede tener un paciente ni define un identificador único para ellos, rompiendo la estructura de bases de datos indexadas por códigos FDI fijos.
*   **Decisión de Ingeniería Provisional (v1):** El sistema implementará una tabla independiente llamada `supernumerary_records`. En lugar de tener un ID de diente fijo, se les asigna un UUIDv7 y un identificador lógico compuesto por las dos piezas vecinas (ej. `supernumerary_between_11_12`). Gráficamente se habilita un botón interdental en la UI para desplegar el componente flotante del supernumerario. El sistema limita por software la creación a un máximo de dos supernumerarios por espacio interdental para mitigar abusos o desbordamientos en la interfaz visual.

### 4.3 Tratamientos Multicara Parciales en el Tiempo
*   **El Vacío:** La norma técnica no detalla cómo se gestiona en el Odontograma de Evolución el escenario en el cual un diente posee un diagnóstico de Caries en dos caras (Mesial y Oclusal, de color Rojo) y el odontólogo, debido al tiempo o presupuesto de la cita de hoy, solo cura la cara Oclusal dejando la cara Mesial enferma. ¿Se debe permitir un estado "Mixto" en el dibujo de la misma pieza dental?
*   **Decisión de Ingeniería Provisional (v1):** El software opera la inmutabilidad y los cambios de estado estrictamente cara por cara, no por pieza completa. El backend procesará la actualización de evolución de forma aislada para la cara enviada como realizada, cambiando el polígono de Oclusal a Azul (Restaurado) y manteniendo el polígono de Mesial en Rojo (Patología), reflejando la realidad clínica exacta de la boca del paciente al finalizar la consulta diaria.
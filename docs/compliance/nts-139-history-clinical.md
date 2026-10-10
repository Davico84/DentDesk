# Especificación de Cumplimiento: NTS N° 139-MINSA/2018/DGAIN (Historia Clínica)

> # 🔴 ADVERTENCIA — ESTE DOCUMENTO NO ES FUENTE NORMATIVA
>
> **Es un análisis derivado, escrito sin el texto oficial de la norma delante.** No se puede citar en un ADR, en un spec ni en una fila de [`regulatory-matrix.md`](regulatory-matrix.md). Degradado por [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D8**.
>
> **Qué se comprobó.** Al leer el texto oficial se encontró que **ninguno de los numerales que este documento cita existe en la norma**: no hay `6.6`, no hay `5.4`, no hay `6.1.1`, no hay `6.1.4`. Además, el plazo de conservación aquí implícito (**15 años**) es **más corto que el legal** (son **20**, § 5.3.1 (6) e), y el documento **omite** la Ficha Odonto-Estomatológica del § 5.2.2 numeral 12, que es una obligación real y hoy incumplida. El § real de cada requisito está en `ADR-0018` **D3**; las exigencias omitidas, en **D2** y **D7**.
>
> **Por qué sigue en el repo y no se borró.** Su traducción a impacto técnico —qué toca en dominio, base de datos, API, contrato y testing— es trabajo real y reutilizable, y en NTS 188 el espíritu de la norma estaba bien traducido: falló el detalle. Borrarlo perdería eso y, peor, borraría **la evidencia de qué creíamos y por qué**. Un error documentado enseña; un error desaparecido se repite.
>
> **Para qué sirve, entonces:** como **inventario de hipótesis a verificar** y como impacto técnico ya razonado. Leelo como *"esto es lo que habría que comprobar"*, nunca como *"esto es lo que dice la norma"*.
>
> ⚠️ **Todo § y todo artículo citado en las páginas siguientes debe considerarse NO VERIFICADO** salvo que aparezca en `ADR-0017` (NTS 188) o `ADR-0018` (NTS 139, COP), que sí se escribieron con el texto oficial.

---

## 1. INTRODUCCIÓN Y CONTEXTO NORMATIVO

El presente documento constituye la especificación de ingeniería y el análisis exhaustivo de cumplimiento para el módulo de Historia Clínica, derivado de la Norma Técnica de Salud para la Gestión de la Historia Clínica (NTS N° 139-MINSA/2018/DGAIN), aprobada mediante la Resolución Ministerial N° 214-2018/MINSA y publicada en el Diario Oficial El Peruano.

Este análisis traduce de forma directa e imperativa las obligaciones legales de la norma en restricciones lógicas, contratos de servicios, esquemas de bases de datos y aserciones de prueba automatizadas para una arquitectura SaaS multi-tenant.

---

## 2. ANÁLISIS EXHAUSTIVO DE OBLIGACIONES DE SOFTWARE

### 2.1 Identificación Única y Datos Obligatorios de Filiación
*   **Referencia Normativa:** Numeral 6.1.1 ("Estructura de la Historia Clínica"), literal a) "Filiación".
*   **Texto Interpretado:** Toda historia clínica debe contar de forma mandatoria con un conjunto de datos demográficos y de identificación del paciente, validados por el sistema de salud. El número de documento nacional de identidad (DNI) o carné de extranjería (CE) constituye el identificador único del expediente.
*   **Impacto Funcional:** El formulario de registro del paciente debe obligar al usuario a completar la totalidad de los campos demográficos antes de permitir la apertura de la historia clínica. No se permiten registros parciales o anónimos.
*   **Impacto en Dominio:** La entidad de dominio `Patient` o `Filiacion` debe poseer invariantes de agregación que verifiquen la presencia de: nombres, apellidos, `tipo_documento`, `numero_documento`, sexo, `fecha_nacimiento`, y las cadenas de Ubigeo para nacimiento y procedencia.
*   **Impacto en Base de Datos:** Restricciones de no nulidad (NOT NULL) en los campos indicados. Índice único compuesto (UNIQUE INDEX) para mitigar colisiones en un entorno multi-tenant: `(tenant_id, tipo_documento, numero_documento)`.
*   **Impacto en API:** El endpoint `POST /api/v1/patients` debe retornar un código de estado `422 Unprocessable Entity` si falta algún campo obligatorio o si la estructura del DNI no cumple con una expresión regular de 8 dígitos exactos.
*   **Impacto en Seguridad:** El acceso a los datos de filiación debe restringirse mediante control de acceso basado en roles (RBAC). Los datos demográficos deben cifrarse en tránsito (TLS 1.3) y en reposo (AES-256).
*   **Impacto en Auditoría:** Cada consulta de lectura (SELECT) de los datos de filiación debe registrar una entrada en el log de accesos (`access_log`), identificando el usuario clínico y el ID del paciente consultado.
*   **Impacto en OpenSpec:** El archivo `openapi.yaml` debe definir el objeto `PatientSchema` con la propiedad `required` sobre los campos mínimos y un patrón de validación (`pattern`) para el número de documento.
*   **Impacto en Testing:** Pruebas unitarias que aseguren el rechazo del guardado cuando un paciente menor de edad no incluya el nombre y DNI de un apoderado o acompañante adulto responsable.

### 2.2 Registro Secuencial, Cronológico e Inmutabilidad de Asientos (Append-Only)
*   **Referencia Normativa:** Numeral 5.4.1 literal f) "Garantizar el registro secuencial y cronológico de la atención" y literal i) "prohibiéndose las enmiendas, raspaduras o destrucción de folios".
*   **Texto Interpretado:** Queda estrictamente prohibida la alteración, modificación destructiva o eliminación física de cualquier nota clínica o evolución registrada en el pasado. El sistema debe funcionar bajo un modelo aditivo continuo.
*   **Impacto Funcional:** El odontólogo no dispone de botones para "Editar" o "Eliminar" notas de evolución guardadas en citas previas. La interfaz de usuario solo permite la inserción de nuevos bloques clínicos.
*   **Impacto en Dominio:** La entidad `EvolutionSeat` es de naturaleza inmutable. Su ciclo de vida en el dominio no contempla métodos de mutación como `updateText()` o `delete()`. Solo responde al método de fábrica `createNewSeat()`.
*   **Impacto en Base de Datos:** La tabla `evolution_seats` no admite sentencias UPDATE ni DELETE. Se aplican permisos a nivel de usuario de base de datos para restringir estos comandos. Los registros se insertan secuencialmente controlados por un índice de ordenamiento basado en timestamp.
*   **Impacto en API:** Los métodos HTTP PUT, PATCH y DELETE sobre el recurso `/api/v1/patients/{id}/evolutions/*` deben estar deshabilitados por completo a nivel de enrutador, devolviendo `405 Method Not Allowed`.
*   **Impacto en Seguridad:** Las llaves primarias de las notas de evolución se generan mediante identificadores únicos universales (UUIDv7) para garantizar la inmutabilidad y mitigar ataques de enumeración de recursos (IDOR).
*   **Impacto en Auditoría:** Los intentos fallidos o solicitudes directas a la base de datos de manipulación de registros históricos deben disparar una alerta de seguridad de nivel crítico en el log del administrador del sistema.
*   **Impacto en OpenSpec:** La especificación de OpenAPI no debe declarar rutas ni operaciones de modificación o borrado para el recurso de evolución clínica.
*   **Impacto en Testing:** Automatización de una prueba de integración que ejecute directamente un comando SQL de tipo UPDATE contra la tabla `evolution_seats` y verifique que la base de datos lance una excepción de violación de privilegios o disparador de bloqueo (Trigger Exception).

### 2.3 Estructuración Obligatoria de la Evolución Clínica (Modelo SOAP)
*   **Referencia Normativa:** Numeral 6.1.4 ("Evolución").
*   **Texto Interpretado:** El registro de la evolución de la atención diaria debe estructurarse obligatoriamente bajo los componentes clínicos internacionales de recolección de información médica: Subjetivo, Objetivo, Análisis y Plan.
*   **Impacto Funcional:** La pantalla de evolución diaria del paciente debe presentar de forma mandatoria cuatro cajas de texto independientes y claramente etiquetadas: S (Datos Subjetivos), O (Datos Objetivos), A (Análisis/Diagnóstico), y P (Plan de Tratamiento).
*   **Impacto en Dominio:** El Objeto de Valor (Value Object) `SoapContent` encapsula las cuatro propiedades como cadenas de texto no vacías. La entidad `EvolutionSeat` no puede instanciarse si el objeto `SoapContent` es inválido o parcial.
*   **Impacto en Base de Datos:** Mapeo de la estructura SOAP en la tabla `evolution_seats` dividida en cuatro columnas discretas: `soap_subjetivo TEXT`, `soap_objetivo TEXT`, `soap_analisis TEXT`, y `soap_plan TEXT`. Todas configuradas como `NOT NULL`.
*   **Impacto en API:** El payload de la petición `POST /api/v1/evolutions` debe validar la estructura anidada del objeto soap:
    ```json
    { 
      "soap": { 
        "s": "...", 
        "o": "...", 
        "a": "...", 
        "p": "..." 
      } 
    }
    ```
*   **Impacto en Seguridad:** Sanitización estricta de las entradas de texto en las cuatro columnas SOAP para mitigar ataques de inyección de código de sitios cruzados (XSS) y sanitización contra inyecciones SQL antes de la persistencia.
*   **Impacto en Auditoría:** El log de auditoría debe validar que el texto original ingresado en cada sección del SOAP coincida exactamente con lo almacenado, sin sufrir procesos de truncado automático por desbordamiento de caracteres.
*   **Impacto en OpenSpec:** Definición exacta en el archivo OpenAPI del objeto `SoapComponent` detallando las restricciones de longitud mínima y máxima para cada una de las variables s, o, a, y p.
*   **Impacto en Testing:** Pruebas de comportamiento (BDD) que verifiquen que el sistema rechace el guardado si el usuario intenta enviar la evolución dejando el campo o (Objetivo) vacío, simulando que el dentista no realizó un examen físico.

### 2.4 Mecanismo de Enmiendas y Rectificaciones (Post-Cierre)
*   **Referencia Normativa:** Numeral 5.4.1 literal i) y principios generales de la documentación médico-legal.
*   **Texto Interpretado:** En caso de error material o equivocación en el registro de una nota clínica ya guardada y cerrada en una fecha anterior, el profesional no puede alterar el texto original. Debe realizar un asiento de enmienda o nota de rectificación en la fecha actual, vinculada al asiento erróneo.
*   **Impacto Funcional:** Si el odontólogo identifica un error en una nota del mes pasado, la interfaz le proporciona una opción de "Agregar Enmienda". Esto abre un formulario en el día de hoy que se guardará en la sesión actual, pero visualmente se mostrará enlazado a la nota corregida con una marca de advertencia.
*   **Impacto en Dominio:** Introducción de la entidad `ClinicalAmendment`. Posee una relación de agregación hacia el `EvolutionSeat` original (`original_seat_id`). Al recuperar el historial, el agregado junta el asiento original y sus enmiendas cronológicas.
*   **Impacto en Base de Datos:** Creación de la tabla `clinical_amendments` con las columnas: `id UUID`, `original_seat_id UUID FOREIGN KEY`, `fecha_enmienda TIMESTAMP`, `user_id UUID`, y `texto_rectificacion TEXT NOT NULL`.
*   **Impacto en API:** Endpoint dedicado `POST /api/v1/evolutions/{id}/amendments` que recibe el payload con la justificación y el texto rectificatorio de la enmienda.
*   **Impacto en Seguridad:** La capacidad de emitir enmiendas debe ser restringida mediante políticas avanzadas de autorización (ABAC), permitiendo únicamente al autor original de la nota o al director médico de la clínica ejecutar el caso de uso.
*   **Impacto en Auditoría:** El registro de la enmienda debe rastrearse con máxima prioridad. El Log de Auditoría guardará el estado anterior de la visualización y asociará el identificador único de la nota de rectificación.
*   **Impacto en OpenSpec:** Documentación del recurso `/amendments` como un sub-recurso dependiente de la nota de evolución, especificando códigos de respuesta `201 Created` y errores `404 Not Found` si el ID del asiento original no existe.
*   **Impacto en Testing:** Diseñar una prueba automatizada que valide que, al consultar el historial clínico del paciente mediante la API, las notas que posean enmiendas devuelvan un flag `has_amendments: true` junto con la lista cronológica de las rectificaciones asociadas.

### 2.5 Reglas de Retención de Datos y Archivo Pasivo
*   **Referencia Normativa:** Numeral 6.6.1 ("Archivo de la Historia Clínica"), literal a) "Tiempo de conservación de la Historia Clínica".
*   **Texto Interpretado:** La historia clínica debe ser conservada de forma íntegra por un periodo mínimo de quince (15) años en el archivo de la clínica, computados a partir de la fecha de la última atención del paciente. Cumplido un tramo intermedio de inactividad, puede pasar a un archivo pasivo.
*   **Impacto Funcional:** El sistema debe restringir y bloquear cualquier opción de eliminación de cuentas de pacientes o expedientes dentales que registren actividad clínica dentro de la ventana de 15 años.
*   **Impacto en Dominio:** El agregado `ClinicalHistory` expone la propiedad lógica `canBePurged()`, la cual evalúa si la diferencia entre la fecha actual del servidor y la `fecha_ultima_atencion` es mayor a 15 años.
*   **Impacto en Base de Datos:** Columna `status` en la tabla `patient_records` que maneja los estados mediante un Enum: `('Activo', 'Pasivo', 'Archivado')`. No existen políticas de borrado en cascada (`ON DELETE CASCADE`) vinculadas al ID del paciente.
*   **Impacto en API:** El endpoint administrativo o de facturación de limpieza de espacio `DELETE /api/v1/tenants/purge-records` debe evaluar la fecha de última atención de cada expediente y retornar una lista de exclusión para aquellos protegidos por la ventana legal.
*   **Impacto en Seguridad:** Los expedientes en estado 'Pasivo' o con más de 5 años de inactividad deben ser trasladados lógicamente a una capa de almacenamiento en frío cifrada con llaves de acceso restringidas, reduciendo la superficie de ataque ante filtraciones de datos activos.
*   **Impacto en Auditoría:** Los procesos automáticos de revisión o cambio de estado de archivo activo a pasivo deben autoevaluarse y dejar constancia en los logs del sistema con la firma del proceso del sistema (System Worker ID).
*   **Impacto en OpenSpec:** El contrato de la API debe reflejar el campo `status` del expediente clínico dentro del esquema de respuesta del paciente.
*   **Impacto en Testing:** Crear una prueba de integración que inserte un paciente ficticio con una última cita simulada en el año 2005 y otro en el año 2020. Al ejecutar el servicio de purga, se debe verificar mediante aserciones que el registro del 2005 sea procesable mientras que el del 2020 mantenga su persistencia intacta.

---

## 3. REQUISITOS DERIVADOS

Para la implementación del sistema informático, se establecen los siguientes requisitos de ingeniería verificables:

*   **HC-001 (Validación de Identidad Obligatoria):** El sistema debe rechazar la creación de cualquier expediente clínico si el campo `numero_documento` no se encuentra acompañado de la selección explícita del tipo de documento y no pasa los filtros de formato de longitud y caracteres alfanuméricos.
*   **HC-002 (Inmutabilidad del Identificador):** Una vez persistido el identificador único del paciente (`tipo_documento + numero_documento`), la API del sistema debe bloquear de forma absoluta cualquier operación de modificación de estos campos, requiriendo un flujo excepcional de auditoría gerencial en caso de error de digitación.
*   **HC-003 (Persistencia de Filiación de Menores):** El backend debe activar una regla de validación de modelo que obligue a poblar los objetos de relación `Acompanante` y `Responsable Legal` cuando el cálculo dinámico de la edad del paciente devuelva un valor estrictamente menor a 18 años.
*   **HC-004 (Estructura de Base de Datos Append-Only):** Las tablas críticas de la historia clínica (`evolution_seats`, `diagnostics_records`) deben carecer de permisos de ejecución para comandos `DELETE` o `UPDATE` a nivel de la capa de persistencia del ORM o del usuario de conexión de la aplicación en la base de datos.
*   **HC-005 (Fuerza de Cierre SOAP Completo):** La API del sistema debe validar que ninguna solicitud de creación de evolución clínica guarde valores nulos, cadenas vacías o espacios en blanco en cualquiera de las cuatro propiedades que componen la estructura SOAP (`s`, `o`, `a`, `p`).
*   **HC-006 (Bloqueo de Expediente por Cita de Ayer Abierta):** El backend debe denegar el acceso a la inserción de nuevos registros clínicos en el expediente de un paciente si este posee una nota de evolución previa en estado abierto (`status == 'open'`) con una fecha de registro que pertenezca a un día calendario anterior al actual.
*   **HC-007 (Trazabilidad Transaccional del Cierre):** El caso de uso coordinador encargado de la acción "Cerrar Cita" debe asegurar, bajo una transacción ACID atómica, el cambio de estado simultáneo a solo lectura tanto de la nota de evolución como del odontograma de evolución del día.
*   **HC-008 (Vinculación de Notas de Enmienda):** El sistema debe persistir las notas de rectificación como entidades independientes con fecha del día presente, prohibiendo que la enmienda reemplace, altere o modifique los bytes de la cadena de texto original del asiento histórico indexado.
*   **HC-009 (Protección de Conservación de los 15 Años):** El software debe impedir la ejecución de cualquier rutina de limpieza de datos o borrado lógico si el expediente del paciente registra al menos un asiento de atención clínica cuya antigüedad sea menor o igual a 15 años solares.
*   **HC-010 (Cifrado de Datos de Salud en Reposo):** Todos los campos de texto libre que almacenen datos diagnósticos, antecedentes o notas clínicas deben ser transformados mediante algoritmos de cifrado simétrico reversible antes de ser volcados a los discos de almacenamiento de la base de datos.

---

## 4. GAPS ENCONTRADOS (BRECHAS COMUNES EN SISTEMAS COMERCIALES)

En el análisis de soluciones de software odontológico existentes en el mercado latinoamericano y peruano, se han identificado los siguientes Compliance Gaps críticos que los sistemas tradicionales suelen omitir, incurriendo en contingencias legales graves ante las inspecciones de SUSALUD o procesos judiciales por mala praxis:

1.  **Uso Indiscriminado de Comandos Update:** La gran mayoría de sistemas comerciales tratan a la evolución clínica como un formulario web estático. Permiten al odontólogo presionar un botón de "Editar" y modificar el texto de lo que sucedió en una cita semanas atrás para corregir ortografía o agregar datos. Esto destruye la validez legal de la historia clínica según el principio de inmutabilidad de folios de la NTS N° 139-MINSA.
2.  **Inexistencia del Modelo SOAP:** Muchas plataformas reducen la sección de evolución a una sola caja de texto libre llamada "Notas de la cita" o "Observaciones". Al no forzar la división explícita de los datos subjetivos y objetivos, el expediente carece de la estructura técnica exigida por las auditorías del MINSA.
3.  **Permisividad de Borrado Físico (Hard Delete):** Para ahorrar espacio en disco o simplificar el desarrollo, los sistemas tradicionales implementan funciones de "Eliminar Paciente" o "Eliminar Cita por Error". Al ejecutar la eliminación física de las filas, se borra el historial clínico del ciudadano, violando la ley de retención obligatoria de 15 años y exponiendo al consultorio a sanciones penales por destrucción de documentos médico-legales.
4.  **Cuentas de Agenda Abiertas Indefinidamente:** Es común que los softwares permitan que una cita del año pasado quede con el estado de "Atendiendo" o "Abierta". El sistema no aplica controles de bloqueo por expediente que fuercen al odontólogo a cerrar los cabos sueltos del pasado antes de escribir en el presente, rompiendo la secuencia cronológica ininterrumpida.
5.  **Falta de Encriptación en Capa de Datos Sensibles:** Los sistemas almacenan las notas de los pacientes en columnas de tipo texto plano (VARCHAR o TEXT). Cualquier atacante con acceso a un respaldo de la base de datos o mediante una inyección SQL simple puede leer masivamente las enfermedades, alergias sistémicas e intimidades médicas de los pacientes, violando flagrantemente la Ley N° 29733 (Protección de Datos Personales).
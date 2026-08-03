# Política de Retención Legal y Ciclo de Vida del Expediente

## 1. INTRODUCCIÓN Y CONTEXTO LEGAL PERUANO

El presente documento establece las especificaciones técnicas de infraestructura, persistencia y ciclo de vida de los datos para una plataforma SaaS multi-tenant, derivadas del marco normativo que rige la conservación, digitalización y seguridad de las Historias Clínicas en el Perú.

La custodia de la información médica en territorio peruano está estrictamente regulada por el Ministerio de Salud (MINSA) y el Congreso de la República a través de un cuerpo de leyes y Normas Técnicas de Salud (NTS). El incumplimiento de los plazos de retención, la alteración de los registros o la destrucción no autorizada de expedientes clínicos acarrea responsabilidades civiles, administrativas y penales frente a SUSALUD, el MINSA y el Poder Judicial.

---

## 2. ANÁLISIS EXHAUSTIVO DE OBLIGACIONES Y TRADUCCIÓN A SOFTWARE

### 2.1 Tiempo Mínimo de Conservación
*   **Referencia Normativa:** NTS N° 139-MINSA/2018/DGAIN (RM N° 214-2018/MINSA), Numeral 6.6.1, literal a).
*   **Obligación:** Las Historias Clínicas deben ser conservadas de forma íntegra por un periodo mínimo de quince (15) años, computados a partir de la fecha de la última atención recibida por el paciente en el establecimiento de salud.
*   **Regla de Software (Backend):**
    *   La base de datos debe implementar un bloqueo lógico absoluto que impida sentencias `DELETE` o rutinas de purga automática sobre la tabla `patient_records` y sus agregados dependientes (`evolution_seats`, `dental_charts`) si la diferencia entre la fecha actual del sistema y la columna `fecha_ultima_atencion` es menor o igual a 15 años solares.
    *   Fórmula de validación: `if (CurrentDate - fecha_ultima_atencion) <= 15_YEARS -> RejectPurgeException`.

### 2.2 Archivo Activo y Archivo Pasivo
*   **Referencia Normativa:** NTS N° 139-MINSA/2018/DGAIN, Numerales 6.6.1 y 6.6.2.
*   **Obligación:** El expediente clínico debe dividirse lógicamente según su frecuencia de uso:
    *   **Archivo Activo:** Aloja las historias de pacientes que han recibido atención en los últimos cinco (5) años. Permanecen disponibles de forma inmediata.
    *   **Archivo Pasivo:** Aloja las historias que han permanecido inactivas por más de cinco (5) años. Deben conservarse por los 10 años restantes del ciclo legal.
*   **Regla de Software (Arquitectura):**
    *   El sistema debe ejecutar una tarea programada (Cron Job / Worker) al finalizar cada mes que evalúe el tiempo de inactividad de los expedientes. Si `(CurrentDate - fecha_ultima_atencion) > 5_YEARS`, el flag de estado cambia automáticamente de `status = 'ACTIVO'` a `status = 'PASIVO'`.
    *   **Optimización de Persistencia:** Los registros en estado `PASIVO` son trasladados lógicamente (o particionados en la base de datos) a tablas o almacenes de lectura en frío cifrados, disminuyendo el costo operativo en la capa de datos calientes del SaaS, pero manteniendo su indexación de solo lectura ante solicitudes de emergencia o auditoría.

### 2.3 Destrucción Controlada de Expedientes
*   **Referencia Normativa:** NTS N° 139-MINSA/2018/DGAIN, Numeral 6.6.1, literal e).
*   **Obligación:** Cumplidos los 15 años de retención obligatoria, las historias clínicas que no posean valor legal, científico o histórico pueden ser eliminadas previa evaluación del Comité de Historias Clínicas y la Comisión del Archivo Central de la IPRESS.
*   **Regla de Software (Seguridad):**
    *   El software SaaS prohíbe de manera estructural la eliminación directa de expedientes por parte de cualquier odontólogo o administrador ordinario de un tenant.
    *   Se debe implementar un módulo exclusivo de "Solicitud de Eliminación por Caducidad Legal". Al activarse (solo disponible si la inactividad supera los 15 años), el sistema exige la carga digital firmada del Acta de Aprobación de la Comisión Técnica de la clínica y la firma digital por huella o clave token del director médico. Solo tras esta doble verificación, el sistema ejecuta un Soft Delete auditado con un hash del acta adjunto en la fila.

### 2.4 Respaldo de Datos (Backups) e Infraestructura Cloud
*   **Referencia Normativa:** Decreto Supremo N° 003-2013-JUS (Reglamento de la Ley N° 29733 - Ley de Protección de Datos Personales), Sección de Medidas de Seguridad Técnicas y Organizativas.
*   **Obligación:** Al catalogarse como datos sensibles, se deben implementar mecanismos que garanticen la disponibilidad, confidencialidad, restauración y copias de seguridad periódicas de los datos de salud ante desastres informáticos.
*   **Regla de Software (DevOps / Infraestructura):**
    *   El backend del SaaS debe desplegarse sobre infraestructura en la nube con replicación multi-zona activa-pasiva en tiempo real.
    *   Automatización de respaldos diarios en caliente (Point-in-Time Recovery - PITR) con almacenamiento inmutable en un bucket con políticas WORM (Write Once, Read Many), impidiendo que un ataque de ransomware o un borrado accidental en la base de datos elimine los respaldos históricos. Los backups se guardan cifrados con llaves gestionadas por un servicio KMS (AES-256).

### 2.5 Digitalización de Documentos Físicos de Terceros
*   **Referencia Normativa:** Decreto Legislativo N° 681 y sus modificatorias (Normas que regulan el uso de tecnologías de avanzada en materia de archivos); en concordancia con la NTS N° 139-MINSA.
*   **Obligación:** Los documentos físicos adjuntos por el paciente (como radiografías impresas de laboratorios externos o certificados médicos manuales) pueden ser incorporados a la historia clínica electrónica mediante procesos de digitalización digital que mantengan la fidelidad absoluta del original.
*   **Regla de Software (Multimedia / API):**
    *   El sistema proveerá un endpoint `POST /api/v1/patients/{id}/attachments` especializado en recibir archivos multimedia (PDF, JPEG, DICOM).
    *   **Validación en Software:** La API aplicará un pipeline de procesamiento de imágenes para verificar la legibilidad del archivo, calculará un hash criptográfico (SHA-256) del documento adjunto y lo inyectará en la base de datos vinculándolo de forma inalterable a la firma del odontólogo que valida el hallazgo, garantizando el no repudio del archivo cargado.

### 2.6 Firma Digital y Certificados de No Repudio
*   **Referencia Normativa:** Ley N° 27269 (Ley de Firmas y Certificados Digitales) y su Reglamento (D.S. N° 052-2008-PCM).
*   **Obligación:** Para que la Historia Clínica Electrónica sustituya por completo al papel con pleno valor probatorio judicial en el Perú, cada cierre de evolución u odontograma debe estar firmado digitalmente empleando un certificado emitido por una Entidad de Certificación acreditada ante INDECOPI.
*   **Regla de Software (Criptografía):**
    *   La API del SaaS implementará un módulo de firma PKCS#7 / XAdES-BES. Al presionar "Cerrar Cita", la plataforma genera un payload JSON/XML estructurado de la sesión SOAP, el sistema interactúa con el componente de firma digital del usuario (DNIe o Certificado Digital de Software en la nube) y concatena la firma digital directamente en el registro de la base de datos (`signature_block`).
    *   Cualquier intento de mutar un solo carácter de la nota clínica romperá la correspondencia del hash, marcando la historia como inválida y corrupta ante una auditoría forense del sistema.

---

## 3. TABLA DE DECISIONES DE ARQUITECTURA DE CUMPLIMIENTO

Esta matriz de ingeniería consolida las reglas que gobiernan el ciclo de vida del expediente odontológico en la plataforma SaaS ante eventos del usuario o de la temporalidad:

| Evento del Sistema / Estado del Tiempo | Condición de Control Clínico | Acción del Software (Regla de Negocio) | Código de Estado API / Impacto en Persistencia |
| :--- | :--- | :--- | :--- |
| **Inactividad &le; 5 años** | Paciente con última cita en el día presente o rango de 5 años. | El expediente permanece en la capa caliente. Búsquedas indexadas directas a nivel de interfaz de agenda y perfiles globales. | `status = 'ACTIVO'`. Lectura/Escritura full habilitadas. |
| **Inactividad > 5 años** | Paciente sin registros de evolución ni citas en los últimos 5 años solares. | Un proceso en segundo plano particiona y migra la data médica. Se desactiva de las consultas masivas de la agenda pero es recuperable en 1 segundo por ID de paciente. | `status = 'PASIVO'`. Escritura bloqueada; requiere crear un evento de "Reingreso" para reactivarse. |
| **Intento de DELETE de paciente** | Antigüedad de última cita menor o igual a 15 años solares. | Hard Block técnico. El backend intercepta la solicitud del ORM y aborta la transacción inmediatamente. Se deniega el borrado de datos médicos. | Código HTTP `403 Forbidden` / Mensaje: "Expediente bajo periodo de conservación legal obligatorio". |
| **Inactividad > 15 años** | Paciente sin atenciones por más de 15 años solares y sin alertas judiciales activas. | Se desbloquea la función excepcional de "Solicitud de Depuración". Requiere la carga obligatoria del PDF del Acta del Comité Clínico firmada digitalmente. | `status = 'ARCHIVADO_DELEGADO'`. Soft Delete lógico en base de datos. Se conserva el log de auditoría del proceso. |
| **Cancelación de suscripción SaaS** | El dueño de la clínica dental da de baja su cuenta mensual y exige borrar la base de datos. | Bloqueo Multi-tenant. Se congela el tenant completo para entrada de datos (POST/PUT/PATCH). Las historias clínicas NO SE BORRAN, se mantienen accesibles en modo GET de descarga para cumplir con la custodia legal del dentista. | Código HTTP `402 Payment Required` o `403` para mutaciones. Acceso `GET /api/v1/compliance/export-all` habilitado. |
| **Carga de Radiografía Externa** | El odontólogo sube un archivo adjunto del paciente. | La API recibe el binario, calcula el SHA-256, lo almacena en Amazon S3 inmutable y guarda el hash en una columna indexada y firmada digitalmente por el doctor. | Código HTTP `201 Created`. Inserción en la tabla `patient_attachments` de forma inmutable. |

---

## 4. EVIDENCIAS Y REQUISITOS DE AUDITORÍA REGLAMENTARIA (SUSALUD)

Para superar de forma exitosa las auditorías presenciales o electrónicas de la Superintendencia Nacional de Salud (SUSALUD) y demostrar la cadena de custodia de la información bucodental, la base de datos del SaaS debe ser capaz de compilar y exportar una sábana de evidencias inalterable con la siguiente estructura técnica:

1.  **El Log de No Repudio (`signature_validation_log`):** Una vista del sistema que asocie: `[ID_Expediente] + [Fecha_Cita] + [Hash_SOAP] + [Código_COP_Firmante] + [Firma_Digital_Certificada]`. Esto demuestra ante un juez o auditor que la nota de la cita de esa fecha fue redactada y cerrada exclusivamente por el dentista acreditado y que no ha sufrido manipulación alguna en los servidores de la nube.
2.  **El Registro de Trazabilidad del Archivo Pasivo:** El sistema debe registrar las transiciones lógicas automáticas de estado (ACTIVO &rarr; PASIVO), sirviendo como evidencia de que la base de datos realiza la preservación y separación de datos de acuerdo con los plazos exactos exigidos por el MINSA.
3.  **Logs de Control de Acceso a Datos Sensibles (`access_audit_trail`):** Toda exportación masiva de datos clínicos o descarga de PDFs de historias clínicas por parte del personal de la clínica dental debe registrar una evidencia no borrable en los discos de auditoría del sistema que capture la dirección IP de origen, la fecha y hora exacta del servidor, el identificador único del usuario que ejecutó la acción y el listado de pacientes expuestos, cumpliendo estrictamente con la rendición de cuentas de la Ley N° 29733.

---

## 5. SUSTENTO NORMATIVO OFICIAL

Este documento de retención y ciclo de vida de datos de salud bucal se sustenta estrictamente en las siguientes fuentes oficiales del Estado Peruano:

*   **Resolución Ministerial N° 214-2018/MINSA (Aprobación de la NTS N° 139-MINSA/2018/DGAIN):** Norma técnica de salud que dicta de forma imperativa los plazos mínimos de conservación de 15 años, la estructura y organización del archivo clínico activo y pasivo, y el manejo de los comités de auditoría de historias clínicas en el Perú.
*   **Ley N° 29733 (Ley de Protección de Datos Personales) y su Reglamento (D.S. N° 003-2013-JUS):** Marco legal que penaliza la fuga de datos médicos sin cifrado y exige el despliegue de mecanismos de respaldo y trazabilidad inmutable sobre bancos de datos catalogados como sensibles.
*   **Ley N° 27269 (Ley de Firmas y Certificados Digitales):** Norma que otorga plena validez legal e idoneidad probatoria a las historias clínicas electrónicas que utilicen infraestructura de firma digital con certificados validados por la IOFE (Infraestructura Oficial de Firma Electrónica) en el territorio peruano.

---

*   **Última revisión técnica de retención legal:** 3 de agosto de 2026.
*   **Estado de la especificación:** Aprobada y congelada para el diseño de la arquitectura de persistencia e infraestructura cloud de la v1.
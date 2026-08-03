# Código de Ética y Deontología del COP - Especificaciones de Cumplimiento

## 1. INTRODUCCIÓN Y CONTEXTO ÉTICO-LEGAL

El presente documento establece las especificaciones de ingeniería y los requisitos de software derivados del Código de Ética y Deontología del Colegio Odontológico del Perú (COP).

En el territorio peruano, el ejercicio profesional del Cirujano Dentista está regulado por la Ley N° 27878 (Ley del Trabajo del Cirujano Dentista), su Reglamento (D.S. N° 016-2005-SA) y los estatutos internos del COP. Las disposiciones deontológicas de este código poseen fuerza legal vinculante y someten al profesional a procesos disciplinarios o sancionatorios en caso de vulneración.

Para un software de naturaleza SaaS multi-tenant que custodia Historias Clínicas Odontológicas, el Código de Ética no representa lineamientos filosóficos abstractos, sino un conjunto de restricciones de seguridad, autoría y confidencialidad que deben ser codificadas e implementadas de forma mandatoria en el backend y la infraestructura de la aplicación.

---

## 2. ANÁLISIS DE OBLIGACIONES DE SOFTWARE (TRADUCCIÓN DEONTOLÓGICA)

### 2.1 Principio de Confidencialidad y Secreto Profesional

*   **Fundamento:** Código de Ética y Deontología del COP, Sección Segunda ("De los Deberes Generales"), Capítulo V ("Del Secreto Profesional"), Artículos del 31 al 36.
*   **Impacto Funcional:**
    *   Los datos médicos, odontogramas, evolución y diagnósticos de un paciente pertenecen al ámbito del secreto profesional del Cirujano Dentista tratante.
    *   Ningún usuario administrativo del SaaS (ej. secretarias, personal de facturación o recepción) debe tener acceso visual al texto libre de la evolución SOAP (`soap_text`), a los hallazgos patológicos del odontograma o al catálogo de diagnósticos CIE-10, a menos que el odontólogo otorgue un permiso de visualización explícito y temporal.
    *   La interfaz de usuario (UI) debe enmascarar o bloquear las pestañas de contenido clínico para roles que no posean una colegiatura COP activa y validada.
*   **Impacto Técnico:**
    *   Implementación de Control de Acceso Basado en Roles y Atributos (RBAC / ABAC).
    *   Las APIs de consulta del expediente (ej. `GET /api/v1/patients/{id}/clinical-history`) deben interceptar el token de autenticación (JWT) y evaluar el rol del usuario solicitante. Si el rol es `ADMIN_FACTURACION` o `RECEPCIONIST`, el backend filtrará el payload de respuesta, devolviendo únicamente metadatos demográficos y de citas, omitiendo los nodos de datos de salud (`evolution_seats`, `dental_chart_states`).
    *   Cifrado a nivel de campo (Field-Level Encryption) utilizando AES-256 para las columnas de texto clínico en la base de datos, empleando llaves de cifrado derivadas por tenant.

### 2.2 Autoría Estricta e Individualidad del Registro

*   **Fundamento:** Código de Ética y Deontología del COP, Sección Tercera ("De los Deberes en el Ejercicio de la Profesión"), Capítulo I ("De la Atención Profesional"), Artículo 42.
*   **Impacto Funcional:**
    *   Toda anotación, diagnóstico, receta emitida o modificación del odontograma en el sistema debe estar amarrada de manera unívoca a un solo Cirujano Dentista, identificado por su número de colegiatura nacional (COP).
    *   Se prohibe el uso de "Cuentas Genéricas" o compartidas en el consultorio (ej. un usuario llamado `Clinica_Dental_Centro` usado por tres dentistas a la vez). El sistema debe formar sesiones individuales y personalizadas.
*   **Impacto Técnico:**
    *   El modelo de datos de la tabla `evolution_seats` y `dental_charts` debe exigir una clave foránea obligatoria (NOT NULL FOREIGN KEY) vinculada a la tabla de profesionales (`dentist_profiles`), la cual almacena el número de COP del usuario que inició la sesión.
    *   El sistema debe implementar tokens de sesión de corta duración con políticas de expiración por inactividad física (máximo 15 minutos). Al expirar, se destruye el contexto de seguridad en el frontend y se exige re-autenticación para evitar que un profesional firme un asiento clínico en la terminal abierta de otro colega.

### 2.3 Firma Digital, Cierre y Congelamiento Clínico

*   **Fundamento:** Código de Ética y Deontología del COP, Sección Tercera, Capítulo II ("De la Historia Clínica"), Artículos 52 y 53; en concordancia con la Ley N° 27269 (Ley de Firmas y Certificados Digitales).
*   **Impacto Funcional:**
    *   El Cirujano Dentista tiene la obligación ética y legal de firmar y cerrar cada una de las atenciones que realiza.
    *   La aplicación proveerá un mecanismo para que el dentista selle el bloque del día. Al ejecutar el cierre, el sistema solicita las credenciales del DNI electrónico (DNIe) o el PIN del certificado digital del profesional para generar la firma electrónica/digital del documento PDF o del JSON del asiento de evolución.
*   **Impacto Técnico:**
    *   Integración en el backend con proveedores de certificación digital acreditados ante el INDECOPI o soporte nativo para la infraestructura de firma digital del RENIEC.
    *   Al accionar el servicio `SignEvolutionSeat`, el backend calcula el hash criptográfico (SHA-256) del bloque SOAP del día y del estado del odontograma, lo firma utilizando la llave privada del certificado digital del odontólogo y almacena el resultado en una columna `digital_signature VARCHAR(512) NOT NULL`. Una vez generada la firma, el registro pasa automáticamente a un estado inmutable binario (`is_locked = true`).

### 2.4 Integridad y Trazabilidad Absoluta de Modificaciones (Log de Auditoría)

*   **Fundamento:** Código de Ética y Deontología del COP, Sección Tercera, Capítulo II, Artículo 54 (Prohibición de alterar, falsear o destruir registros clínicos).
*   **Impacto Funcional:**
    *   Queda estrictamente prohibido alterar o falsear los datos del expediente para encubrir iatrogenias, complicaciones clínicas o errores de tratamiento.
    *   Si el odontólogo comete un error material y requiere rectificar una anotación de una cita pasada (que ya fue firmada y cerrada), el sistema no le permite borrar el texto. El software expone una función de "Nota de Enmienda" que se anexa cronológicamente en la sesión de hoy, pero que deja en evidencia el historial completo del error y la corrección.
*   **Impacto Técnico:**
    *   Implementación de una arquitectura de base de datos Append-Only para el historial clínico. Las operaciones de mutación se gestionan exclusivamente mediante comandos `INSERT`.
    *   Activación de una tabla global de auditoría (`audit_trail_logs`) gobernada por disparadores (Triggers) directos a nivel del motor de base de datos (independientes del ORM del backend). Cada transacción que involucre datos clínicos debe poblar de forma automática e imborrable los campos: `log_id UUID`, `timestamp TIMESTAMP`, `action_type ENUM('SELECT', 'INSERT', 'AMENDMENT')`, `user_id UUID`, `patient_id UUID`, `payload_snapshot JSON`. Esta tabla carece de permisos de eliminación (DELETE) incluso para el rol de súper administrador de la base de datos.

### 2.5 Acceso Legítimo a los Datos por el Paciente

*   **Fundamento:** Código de Ética y Deontología del COP, Sección Tercera, Capítulo II, Artículo 55 (Derecho del paciente a solicitar copia de su Historia Clínica).
*   **Impacto Funcional:**
    *   El paciente es el titular de los datos de salud y tiene derecho a exigir una copia impresa o digital completa de su historia clínica, odontogramas y evoluciones.
    *   El sistema debe proporcionar una función de "Exportación de Expediente" ejecutable en un solo clic por los usuarios autorizados de la clínica. Esta función genera un reporte formal y estructurado en formato PDF estandarizado que incluye la filiación, el odontograma inicial (NTS N° 188), la línea de tiempo de la evolución SOAP y las firmas de los profesionales.
*   **Impacto Técnico:**
    *   Diseño de un microservicio dedicado a la renderización asíncrona de reportes médicos (`ReportGenerationService`).
    *   El servicio consume los datos de los asientos clínicos del paciente, genera un documento PDF compatible con el estándar PDF/A (conservación a largo plazo) e inyecta en el pie de página un código QR dinámico que contiene el hash criptográfico del documento. Esto permite verificar la autenticidad del expediente impreso contrastándolo contra la firma digital registrada en los servidores del SaaS.

### 2.6 Conservación Obligatoria y Ciclo de Vida del Expediente

*   **Fundamento:** Código de Ética y Deontología del COP, Sección Tercera, Capítulo II, Artículo 52.
*   **Impacto Funcional:**
    *   El odontólogo y la clínica son custodios de la historia clínica y deben garantizar su conservación y almacenamiento seguro en el tiempo.
    *   El SaaS debe garantizar la persistencia de la información clínica y prohibir la purga o eliminación de datos de pacientes, incluso si la clínica dental decide cancelar su suscripción mensual al SaaS. En caso de baja del servicio, el sistema debe bloquear la escritura pero mantener habilitada la lectura y la exportación masiva de los expedientes para salvaguardar la custodia legal del dentista.
*   **Impacto Técnico:**
    *   Implementación de políticas lógicas de exclusión de borrado en la base de datos multi-tenant. La baja de un tenant (clínica dental) cambia el flag de su cuenta a `subscription_status = 'suspended'`.
    *   El middleware de enrutamiento de la API interceptará las solicitudes de los tenants suspendidos: si la petición es un método de escritura (POST, PUT, PATCH), la API devuelve un código `402 Payment Required` o `403 Forbidden`; si la petición es un método de lectura (GET) enfocado en la exportación de historias clínicas, la API permite la descarga del flujo de datos de forma gratuita para cumplir con la ley de conservación sanitaria.

---

## 3. RESUMEN DE REQUISITOS DERIVADOS DE CUMPLIMIENTO ÉTICO

Para verificar el cumplimiento del Código de Ética del COP en los contratos de la API y las suites de pruebas automatizadas, se establecen los siguientes identificadores y criterios de aceptación:

*   **COP-REQ-001 (Validación de Colegiatura Activa):** El sistema debe verificar en el flujo de registro u onboarding de un odontólogo que el número de COP ingresado sea válido y corresponda a un profesional habilitado.
*   **COP-REQ-002 (Aislamiento de Texto Clínico por Rol):** Los endpoints que devuelvan el contenido clínico detallado de la historia clínica deben validar que el usuario que realiza la petición posea un rol con credenciales médicas habilitadas, rechazando solicitudes de personal administrativo.
*   **COP-REQ-003 (Inmutabilidad Post-Firma):** Una vez que un asiento de evolución o un odontograma es firmado electrónicamente, el backend debe rechazar cualquier payload que intente alterar los bytes de dicho registro, forzando el uso exclusivo del endpoint de enmiendas.
*   **COP-REQ-004 (Trazabilidad Total de Lecturas):** Toda consulta que exponga datos sensibles de salud bucal de un paciente debe gatillar de forma síncrona una inserción inalterable en el log de auditoría del sistema, documentando quién accedió a la información.

---

## 4. SUSTENTO REGLAMENTARIO PERUANO

Este documento de especificaciones técnicas se fundamenta estrictamente en las siguientes fuentes oficiales del Estado Peruano y del Colegio Odontológico del Perú:

*   **Código de Ética y Deontología del Colegio Odontológico del Perú:** Edición oficial ratificada por los órganos de gobierno del COP, regulando el ejercicio legal de la profesión en todo el territorio nacional.
*   **Ley N° 27878 (Ley del Trabajo del Cirujano Dentista):** Publicada en el Diario Oficial El Peruano, que establece la responsabilidad civil y penal del odontólogo sobre la veracidad del diagnóstico y la custodia de la Historia Clínica como documento oficial.
*   **Ley N° 27269 (Ley de Firmas y Certificados Digitales):** Norma que otorga validez legal a las firmas electrónicas generadas en software sobre documentos e historias clínicas electrónicas en el Perú, equiparándolas con la firma manuscrita bajo condiciones de seguridad criptográfica.
# Reglas de Codificación Derivadas de Regulaciones

> # 🔴 ADVERTENCIA — LOS "FUNDAMENTOS NORMATIVOS" DE ESTE DOCUMENTO NO SON VERIFICABLES
>
> Cada regla de este manual declara un **Fundamento Normativo** con numeral. **Los de NTS 139 no existen en la norma**: `5.4.1 f`, `5.4.1 i` y `6.1.4` se citan cinco veces entre todas las reglas y ninguno corresponde a la estructura real del texto oficial. Los § reales están en [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D3**.
>
> **Las reglas de codificación en sí son, en general, buenas** — append-only, sellado server-side, prohibición de `UPDATE`/`DELETE`, tenant en cada tabla. Varias ya están construidas y testeadas. Lo que no se sostiene es **la línea que dice de qué artículo salen**.
>
> **Consecuencia práctica:** usá este documento como **catálogo de reglas técnicas propuestas**, nunca como prueba de cumplimiento. Para responder *"¿qué norma obliga a esto?"* la fuente es [`regulatory-matrix.md`](regulatory-matrix.md), y solo sus filas con § verificado.

---

## 1. INTRODUCCIÓN Y PROPÓSITO DEL MANUAL DE CODIFICACIÓN

El presente documento consolida y traduce el marco normativo sanitario del Perú —regulado por el Ministerio de Salud (MINSA), la Superintendencia Nacional de Salud (SUSALUD) y el Colegio Odontológico del Perú (COP)— en un conjunto de Reglas de Codificación Obligatorias para el equipo de desarrollo.

Estas directrices de ingeniería de software garantizan que cada línea de código, migración de base de datos, endpoint de la API y componente de interfaz en nuestra plataforma SaaS multi-tenant (SIHCO) actúe en estricto cumplimiento legal. El incumplimiento de estas reglas por parte de un desarrollador invalida la legalidad del expediente médico-dentario, exponiendo a la empresa y a las clínicas clientes a severas sanciones penales, civiles y administrativas.

---

## 2. CATEGORÍA: REGLAS DE DOMINIO

### CR-DOM-001: Inmutabilidad Intrínseca del Asiento Clínico
*   **Descripción:** Las entidades que representen notas de evolución, diagnósticos o registros de odontograma cerrados no deben exponer métodos públicos mutadores (setters) bajo ninguna circunstancia.
*   **Fundamento Normativo:** NTS N° 139-MINSA/2018/DGAIN, Numeral 5.4.1 literal i) (Prohibición estricta de enmiendas, raspaduras o destrucción de folios).
*   **Razón:** Previene de forma estructural (a nivel de compilación o diseño de software) que cualquier desarrollador invoque inadvertidamente un método de actualización sobre un registro clínico inalterable en el tiempo.
*   **Ejemplo Correcto:**
    ```typescript
    export class EvolutionSeat {
      private constructor(
        public readonly id: string,
        public readonly patientId: string,
        public readonly soap: SoapContent,
        public readonly status: 'open' | 'sealed'
      ) {}

      // El estado cambia únicamente creando un agregado nuevo o por métodos de transición controlada sin setters libres
      public seal(): EvolutionSeat {
        return new EvolutionSeat(this.id, this.patientId, this.soap, 'sealed');
      }
    }
    ```
*   **Ejemplo Incorrecto:**
    ```typescript
    export class EvolutionSeat {
      public id: string;
      public soapText: string; // Setter implícito público: PERMITE MUTACIÓN DIRECTA
      public status: string;
    }
    ```
*   **Impacto:** Bloqueo absoluto de modificaciones en memoria en el ciclo de vida del objeto de dominio.

### CR-DOM-002: Fuerza de Formato Estricto FDI para Dientes
*   **Descripción:** El Objeto de Valor (Value Object) encargado de albergar el número de la pieza dentaria debe validar de forma interna el rango anatómico internacional FDI aceptado en el Perú.
*   **Fundamento Normativo:** NTS N° 188-MINSA/DGIESP-2022, Numeral 6.1 (Adopción obligatoria del sistema numérico de la Federación Dental Internacional).
*   **Razón:** Evita la persistencia de inconsistencias clínicas imposibles (como registrar una caries en un diente inexistente número 49 o 92).
*   **Ejemplo Correcto:**
    ```typescript
    export class ToothNumber {
      private static VALID_FDI_NUMBERS = [
        11,12,13,14,15,16,17,18, 21,22,23,24,25,26,27,28,
        31,32,33,34,35,36,37,38, 41,42,43,44,45,46,47,48,
        51,52,53,54,55, 61,62,63,64,65, 71,72,73,74,75, 81,82,83,84,85
      ];

      constructor(public readonly value: number) {
        if (!ToothNumber.VALID_FDI_NUMBERS.includes(value)) {
          throw new InvalidToothNumberException(`Código FDI ${value} inválido anatónicamente.`);
        }
      }
    }
    ```
*   **Ejemplo Incorrecto:**
    ```typescript
    export class Tooth {
      public toothNumber: number; // Acepta cualquier entero: 0, -5, 99, etc.
    }
    ```
*   **Impacto:** Integridad anatómica garantizada en el núcleo de la lógica de negocio.

---

## 3. CATEGORÍA: REGLAS DE PERSISTENCIA

### CR-PER-001: Restricción Absoluta de Consultas UPDATE/DELETE Clínicas
*   **Descripción:** Queda prohibida la existencia de consultas o sentencias SQL de tipo UPDATE o DELETE dirigidas a las tablas de evolución médica, diagnósticos o registros vectoriales del odontograma. La base de datos opera bajo un modelo puro Append-Only.
*   **Fundamento Normativo:** NTS N° 139-MINSA/2018/DGAIN, Numeral 5.4.1 literal f) y Ley N° 29733.
*   **Razón:** Garantiza que ni por errores del ORM ni por comandos manuales de soporte técnico se purguen o alteren los bytes de datos de salud bucal protegidos por ley.
*   **Ejemplo Correcto:**
    ```sql
    -- Estructura de tabla inmutable
    CREATE TABLE evolution_seats (
        id UUID PRIMARY KEY,
        patient_id UUID NOT NULL,
        soap_subjetivo TEXT NOT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );
    -- La base de datos rechaza comandos destructivos mediante triggers o reglas del motor SQL
    CREATE RULE no_update_evolution AS ON UPDATE TO evolution_seats DO INSTEAD NOTHING;
    CREATE RULE no_delete_evolution AS ON DELETE TO evolution_seats DO INSTEAD NOTHING;
    ```
*   **Ejemplo Incorrecto:**
    ```typescript
    // Uso del método update del ORM en el repositorio
    async updateEvolutionText(id: string, newText: string): Promise<void> {
        await this.ormRepository.update(id, { soapSubjetivo: newText }); // VIOLACIÓN CRÍTICA DE LA NORMA
    }
    ```
*   **Impacto:** Seguridad e inmutabilidad de la persistencia a nivel de base de datos relacional.

### CR-PER-002: Almacenamiento Estricto de Acrónimos de la NTS 188
*   **Descripción:** Las columnas destinadas a registrar afecciones generales de la pieza dentaria en la base de datos deben estar limitadas mediante restricciones de verificación (CHECK) a los acrónimos de 3 letras en mayúsculas de la norma 2022.
*   **Fundamento Normativo:** NTS N° 188-MINSA/DGIESP-2022, Anexo N° 01 (Catálogo oficial de símbolos).
*   **Razón:** Impide la dispersión de datos libres en los recuadros de texto del odontograma, unificando las búsquedas epidemiológicas del MINSA.
*   **Ejemplo Correcto:**
    ```sql
    ALTER TABLE tooth_records 
    ADD COLUMN box_acronym VARCHAR(3) NULL,
    ADD CONSTRAINT chk_nts_188_acronyms 
    CHECK (box_acronym IN ('IMP', 'MAC', 'MIC', 'DES', 'E', 'S'));
    ```
*   **Ejemplo Incorrecto:**
    ```sql
    ALTER TABLE tooth_records ADD COLUMN box_acronym TEXT; -- Permite ingresar "Caries", "implante", "Falta", etc.
    ```
*   **Impacto:** Estandarización y normalización absoluta de los reportes de auditoría dental.

---

## 4. CATEGORÍA: REGLAS DE API

### CR-API-001: Bloqueo de Métodos HTTP Mutadores sobre Recursos Clínicos
*   **Descripción:** Las rutas y controladores encargados de exponer los recursos de la historia clínica (evolutions, diagnostics, dental-charts) no deben declarar ni implementar manejadores para los métodos HTTP PUT, PATCH ni DELETE.
*   **Fundamento Normativo:** NTS N° 139-MINSA/2018/DGAIN, Numeral 5.4.1 literal i).
*   **Razón:** Blindar la API REST frente a ataques externos o peticiones maliciosas que intenten forzar la alteración de folios sanitarios mediante verbos HTTP comunes de modificación.
*   **Ejemplo Correcto:**
    ```typescript
    @Controller('api/v1/patients/:patientId/evolutions')
    export class EvolutionController {
      @Post() // Único método de mutación permitido para añadir registros (Append-Only)
      async create(@Param('patientId') patientId: string, @Body() dto: CreateEvolutionDto) {
        return this.createEvolutionUseCase.execute(patientId, dto);
      }

      @Get() // Permitido para lectura histórica
      async findAll(@Param('patientId') patientId: string) {
        return this.findEvolutionsUseCase.execute(patientId);
      }
    }
    ```
*   **Ejemplo Incorrecto:**
    ```typescript
    @Patch(':id') // CONTROLADOR PROHIBIDO POR LEY
    async update(@Param('id') id: string, @Body() dto: UpdateEvolutionDto) {
      return this.updateEvolutionUseCase.execute(id, dto);
    }
    ```
*   **Impacto:** Reducción drástica de la superficie de ataque y protección del contrato de la API.

### CR-API-002: Validación Estricta de la Estructura SOAP Obligatoria
*   **Descripción:** El Data Transfer Object (DTO) de entrada para el endpoint de evolución debe validar obligatoriamente que las cuatro variables del modelo SOAP existan, contengan texto y cumplan con una longitud mínima.
*   **Fundamento Normativo:** NTS N° 139-MINSA/2018/DGAIN, Numeral 6.1.4 ("Evolución").
*   **Razón:** Evita registros vacíos o incompletos por negligencia del usuario, forzando la recolección de los datos subjetivos y objetivos de la atención.
*   **Ejemplo Correcto:**
    ```typescript
    export class CreateEvolutionDto {
      @IsNotEmpty() @IsString() @MinLength(5) readonly subjetivo: string;
      @IsNotEmpty() @IsString() @MinLength(5) readonly objetivo: string;
      @IsNotEmpty() @IsString() @MinLength(5) readonly analisis: string;
      @IsNotEmpty() @IsString() @MinLength(5) readonly plan: string;
    }
    ```
*   **Ejemplo Incorrecto:**
    ```typescript
    export class CreateEvolutionDto {
      readonly notasCita: string; // Caja única de texto libre: Incumple la norma SOAP
    }
    ```
*   **Impacto:** Robustez en la entrada de datos y estricto cumplimiento técnico-clínico.

---

## 5. CATEGORÍA: REGLAS DE FRONTEND

### CR-FRONT-001: Enmascaramiento Dinámico de Pestañas Clínicas por Rol (Secreto Profesional)
*   **Descripción:** El componente de visualización del perfil del paciente en el frontend debe validar los claims del token de sesión. Si el rol del usuario pertenece al ámbito administrativo, se debe bloquear el renderizado de los componentes del Odontograma y las notas SOAP de evolución.
*   **Fundamento Normativo:** Código de Ética y Deontología del COP, Artículos del 31 al 36 (Secreto Profesional).
*   **Razón:** Asegura que el personal de recepción o facturación no visualice el historial de enfermedades bucales ni datos íntimos de salud del ciudadano, limitando su acceso exclusivo a agendas y cobros.
*   **Ejemplo Correcto:**
    ```jsx
    const PatientProfile = ({ userRole, patientData }) => {
      const isClinicalRole = ['DENTIST', 'DIRECTOR_MEDICO'].includes(userRole);

      return (
        <div>
          <DemographicSummary data={patientData.filiacion} />
          <BillingModule data={patientData.payments} />

          {isClinicalRole ? (
            <DentalChartModule data={patientData.chart} />
          ) : (
            <div className="alert-banner">Contenido Clínico Restringido por Secreto Profesional</div>
          )}
        </div>
      );
    };
    ```
*   **Ejemplo Incorrecto:**
    ```jsx
    // Mostrar todo el objeto del paciente y ocultarlo solo ocultando el botón con CSS (vulnerable)
    const PatientProfile = ({ patientData }) => {
      return (
        <div>
          <DentalChartModule data={patientData.chart} style={{ display: 'block' }} /> 
        </div>
      );
    };
    ```
*   **Impacto:** Seguridad visual en las estaciones de trabajo del consultorio.

### CR-FRONT-002: Inmutabilidad Absoluta del Código de Colores CSS
*   **Descripción:** Queda prohibido inyectar códigos de color arbitrarios o paletas personalizables en los componentes SVG del odontograma. Los estilos de relleno de los polígonos deben responder exclusivamente a las variables estáticas normadas del sistema.
*   **Fundamento Normativo:** NTS N° 188-MINSA/DGIESP-2022, Numeral 6.2 (Código binario Rojo/Azul).
*   **Razón:** Mantiene la fidelidad gráfica legal del odontograma para procesos de auditoría forense o peritajes judiciales.
*   **Ejemplo Correcto:**
    ```css
    :root {
      --MINSA-RED-PATHOLOGY: #FF0000;
      --MINSA-BLUE-RESTORED: #0000FF;
    }
    .face-caries { fill: var(--MINSA-RED-PATHOLOGY); }
    .face-restored { fill: var(--MINSA-BLUE-RESTORED); }
    ```
*   **Ejemplo Incorrecto:**
    ```css
    .face-caries { fill: #ff6b6b; } /* Un color pastel o coral rompe la uniformidad legal de la NTS 188 */
    ```
*   **Impacto:** Idoneidad gráfica y conformidad legal en auditorías presenciales.

---

## 6. CATEGORÍA: REGLAS DE TESTING

### CR-TST-001: Test de Inyección Intencional Destructiva (Falso Positivo de Borrado)
*   **Descripción:** Es obligatorio que el módulo de evolución clínica cuente con una prueba de integración automatizada que intente ejecutar sentencias de mutación o borrado directo, verificando que el sistema responda con un lanzamiento de error controlado.
*   **Fundamento Normativo:** NTS N° 139-MINSA/2018/DGAIN, Numeral 5.4.1 literal i).
*   **Razón:** Asegura que los mecanismos de protección Append-Only de la capa de datos sigan operativos tras actualizaciones del framework o del ORM.
*   **Ejemplo Correcto:**
    ```typescript
    it('debería denegar y lanzar una excepción si se intenta ejecutar un UPDATE en la evolución', async () => {
      const openSeat = await repository.save(mockEvolutionData());

      await expect(
        repository.nativeQuery(`UPDATE evolution_seats SET soap_subjetivo = 'Alterado' WHERE id = '${openSeat.id}'`)
      ).rejects.toThrow(); // La aserción espera con éxito el rechazo de la base de datos
    });
    ```
*   **Ejemplo Incorrecto:**
    ```typescript
    it('debería actualizar la evolución', async () => {
      // Probar que el update funciona de forma ordinaria sobre datos de salud destruye el cumplimiento normativo
    });
    ```
*   **Impacto:** Garantía continua de inmutabilidad incorporada en el pipeline de Integración Continua (CI/CD).

---

## 7. CATEGORÍA: REGLAS DE SEGURIDAD

### CR-SEG-001: Cifrado Reversible a Nivel de Campo para Datos de Salud (Field-Level Encryption)
*   **Descripción:** Los campos de la base de datos que alberguen datos de texto clínico libre (como los componentes SOAP o el texto de antecedentes médicos) deben guardarse cifrados utilizando el algoritmo AES-256-GCM.
*   **Fundamento Normativo:** Ley N° 29733 (Ley de Protección de Datos Personales) y D.S. N° 003-2013-JUS (Medidas de seguridad para Datos Sensibles).
*   **Razón:** Mitiga el riesgo de filtración masiva de información confidencial en texto plano ante un compromiso directo del motor de base de datos o descargas maliciosas de backups.
*   **Ejemplo Correcto:**
    ```typescript
    export class CryptoTransformer {
      public to(value: string): string {
        return encryptWithAES256GCM(value, process.env.DB_ENCRYPTION_KEY);
      }
      public from(value: string): string {
        return decryptWithAES256GCM(value, process.env.DB_ENCRYPTION_KEY);
      }
    }
    // En la entidad ORM se decora la columna con el transformador criptográfico
    @Column({ type: 'text', transformer: new CryptoTransformer() })
    soap_subjetivo: string;
    ```
*   **Ejemplo Incorrecto:**
    ```typescript
    @Column({ type: 'text' })
    soap_subjetivo: string; // Persiste los datos íntimos de la enfermedad en texto plano legible
    ```
*   **Impacto:** Blindaje total de la capa de persistencia ante accesos no autorizados a la infraestructura.

---

## 8. CATEGORÍA: REGLAS DE AUDITORÍA

### CR-AUD-001: Intercepción Mandatoria de Logs de Lectura (Read Auditing)
*   **Descripción:** Toda consulta de lectura (SELECT) dirigida a recuperar el historial clínico de un paciente debe gatillar una inserción síncrona en el log de auditoría del sistema de forma atómica.
*   **Fundamento Normativo:** Ley N° 29733 y regulaciones de fiscalización de SUSALUD.
*   **Razón:** Permite responder con precisión científica y forense a la pregunta: ¿Quién, cuándo y desde dónde se visualizó el expediente médico de un ciudadano? ante denuncias por violación de la intimidad o secreto profesional.
*   **Ejemplo Correcto:**
    ```typescript
    async getClinicalHistoryById(patientId: string, contextUser: UserContext): Promise<ClinicalHistory> {
      const history = await this.historyRepository.findOne(patientId);

      await this.auditLogger.log({
        action: 'READ_CLINICAL_HISTORY',
        userId: contextUser.id,
        patientId: patientId,
        ipAddress: contextUser.ip,
        timestamp: new Date()
      }); // Registro inalterable del acceso

      return history;
    }
    ```
*   **Ejemplo Incorrecto:**
    ```typescript
    async getClinicalHistoryById(patientId: string): Promise<ClinicalHistory> {
      return this.historyRepository.findOne(patientId); // Entrega la data sensible sin dejar rastro de auditoría
    }
    ```
*   **Impacto:** Trazabilidad total de accesos y estricta rendición de cuentas operativa.

---

## 9. RESUMEN DE COMPROMISOS TÉCNICOS PARA EL SPRINT

A partir de este manual, el equipo de desarrollo asume las siguientes directrices operativas inmediatas:

1.  **Revisión de Pull Requests (PRs):** Ningún PR que involucre las tablas o clases de los módulos de la historia clínica o del odontograma será aprobado si implementa métodos mutadores o consultas destructivas (UPDATE/DELETE).
2.  **Verificación de Arquitectura con ArchUnit:** Se incorporarán reglas automatizadas de testing de arquitectura en la suite de pruebas para escanear y fallar el build si se detecta un método HTTP PUT/PATCH/DELETE mapeado en los controladores del paquete de historias clínicas.

---

*   **Última actualización del manual de ingeniería:** 3 de agosto de 2026.
*   **Vigencia:** Inmediata y obligatoria para todos los desarrolladores del ecosistema SIHCO.
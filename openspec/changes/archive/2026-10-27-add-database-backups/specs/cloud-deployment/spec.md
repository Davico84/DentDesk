## ADDED Requirements

### Requirement: Copia diaria cifrada de la base fuera del proveedor
El repositorio SHALL incluir un workflow que una vez al día, y a pedido, haga un volcado completo de la base de producción, lo cifre con la clave pública configurada y lo guarde fuera del proveedor de la base como artifact con la fecha en el nombre y 90 días de retención. Si falta el secreto de conexión o la clave pública, el workflow SHALL omitir la copia y terminar sin error.

#### Scenario: Copia diaria
- **WHEN** se cumple la hora programada y el secreto de conexión y la clave pública están configurados
- **THEN** el workflow guarda un artifact con el volcado cifrado y la fecha en el nombre, retenido 90 días

#### Scenario: Copia a pedido
- **WHEN** el responsable ejecuta el workflow manualmente
- **THEN** se genera una copia igual que la programada

#### Scenario: Proyecto sin configurar
- **WHEN** el workflow corre sin el secreto de conexión o sin la clave pública
- **THEN** omite la copia, lo indica en el resumen del job y termina sin error

#### Scenario: Falla de la copia
- **WHEN** la base no responde o el volcado falla
- **THEN** el workflow falla y no sube ningún artifact

### Requirement: Nada legible de la copia sin la clave privada
Ningún volcado sin cifrar ni dato de la base SHALL subirse ni aparecer en los logs del workflow, que tampoco SHALL mostrar la cadena de conexión. El volcado sin cifrar SHALL borrarse del runner al terminar el job, también cuando falla. La clave privada SHALL quedar fuera del repositorio y de GitHub.

#### Scenario: Artifact sin la clave privada
- **WHEN** alguien descarga el artifact de la copia sin la clave privada
- **THEN** solo obtiene un archivo cifrado con `age`

#### Scenario: Logs sin secretos ni datos
- **WHEN** se lee el log de una copia exitosa o fallida
- **THEN** no aparecen la cadena de conexión, el host, el usuario ni datos de la base; una falla de conexión solo dice de qué tipo fue

#### Scenario: Limpieza tras una falla
- **WHEN** el job falla después de escribir el volcado
- **THEN** el volcado sin cifrar se borra del runner

### Requirement: Copia completa de todas las clínicas con una credencial de solo lectura
La copia SHALL hacerse con una credencial que no puede modificar la base y que lee todas las filas de todas las clínicas pese a Row-Level Security. Si alguna tabla no puede leerse completa, la copia SHALL fallar en lugar de salir incompleta. Esa credencial SHALL usarse solo para la copia, nunca por la aplicación.

#### Scenario: Credencial que no escribe
- **WHEN** se usa la credencial de la copia para insertar, modificar o borrar en la base
- **THEN** la base lo rechaza

#### Scenario: Filas de todas las clínicas
- **WHEN** hay dos clínicas con pacientes y se restaura la copia
- **THEN** la base restaurada tiene los pacientes de las dos

#### Scenario: Credencial que RLS filtraría
- **WHEN** la credencial de la copia no puede saltar Row-Level Security en alguna tabla
- **THEN** el volcado falla y no se sube ningún artifact

### Requirement: Restauración de la copia verificada
Antes de subirse, cada copia SHALL restaurarse en una PostgreSQL vacía de la misma versión mayor, y el workflow SHALL fallar si la restauración no termina, si la mayor migración aplicada con éxito (comparada como número) no es la última del repositorio o si falta alguna tabla de la copia. Si no puede determinar lo esperado, SHALL fallar en lugar de dar la copia por buena.

#### Scenario: Copia que se restaura
- **WHEN** el volcado se restaura en la base vacía del job
- **THEN** la mayor versión aplicada con éxito es la de la última migración del repositorio en orden numérico (p. ej. `V29` y no `V9`)
- **AND** cada tabla incluida en la copia existe en la base restaurada
- **AND** recién entonces se sube el artifact

#### Scenario: Copia que no se restaura
- **WHEN** la restauración falla, la base copiada está atrasada respecto de las migraciones del repositorio o falta alguna de sus tablas
- **THEN** el workflow falla y no sube el artifact

#### Scenario: Sin referencia para verificar
- **WHEN** el directorio de migraciones no existe o no tiene migraciones versionadas, la copia no tiene tabla de migraciones o no incluye ninguna tabla
- **THEN** el workflow falla y no sube el artifact

### Requirement: Restauración documentada sin romper el aislamiento entre clínicas
El repositorio SHALL documentar cómo descifrar una copia y restaurarla en una base nueva sin tocar la de producción. Si la base restaurada va a reemplazar a la de producción, la guía SHALL exigir devolver los permisos al rol de la aplicación y repetir la verificación de aislamiento entre clínicas antes de abrir la app a usuarios.

#### Scenario: Restauración en una base nueva
- **WHEN** el responsable sigue la guía con un artifact y su clave privada
- **THEN** obtiene una base con los datos de la copia, sin tocar la de producción

#### Scenario: Reemplazo de producción
- **WHEN** la app se apunta a una base restaurada
- **THEN** antes de abrirla a usuarios el rol de la aplicación tiene sus permisos y no salta Row-Level Security, y dos clínicas no se ven entre sí

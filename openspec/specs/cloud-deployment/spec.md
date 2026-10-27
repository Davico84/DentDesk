# cloud-deployment Specification

## Purpose
Cómo se empaqueta y publica Dentdesk en planes gratuitos: la imagen de contenedor del backend, el blueprint de Render, el frontend en Vercel con la API servida bajo su mismo origen, el aislamiento entre clínicas en la base desplegada (Neon) y la verificación de todo eso contra la URL pública. Es una capacidad técnica: no agrega comportamiento de negocio, decide dónde y cómo corre el que ya existe.

## Requirements

### Requirement: API bajo una base común
La API del backend SHALL servirse bajo la base `/api` (endpoints, Actuator y documentación), en local y en despliegue, de modo que ninguna ruta de la API coincida con una pantalla de la app. La cookie de refresh SHALL acotarse a `/api/auth`.

#### Scenario: Endpoints bajo /api
- **WHEN** se llama a `POST /api/auth/login` o a `GET /api/actuator/health/liveness`
- **THEN** responde el backend
- **AND** `GET /patients/1` sin la base no es un endpoint de la API

#### Scenario: Preflight CORS a la API
- **WHEN** el navegador envía `OPTIONS /api/auth/refresh` con un `Origin` permitido, `Access-Control-Request-Method: POST` y credenciales
- **THEN** el backend responde el preflight permitiendo ese origen y credenciales

#### Scenario: Cookie de refresh acotada a la base
- **WHEN** un usuario inicia sesión
- **THEN** la cookie de refresh lleva `Path=/api/auth`
- **AND** viaja en `POST /api/auth/refresh`

### Requirement: Imagen de contenedor del backend
El backend SHALL construirse como imagen desde `modules/backend/Dockerfile`. La imagen final SHALL contener solo la aplicación, sin fuentes ni secretos, ejecutarse como usuario sin privilegios y escuchar en el puerto de `PORT` (8080 si no está). Toda la configuración SHALL llegar por variables de entorno y la JVM SHALL funcionar con 512 MB.

#### Scenario: Arranque en el puerto asignado
- **WHEN** la imagen se ejecuta con `PORT=10000` y las variables de base de datos y JWT
- **THEN** `GET /api/actuator/health/liveness` en el puerto 10000 responde `200` con `status` `UP`

#### Scenario: Imagen sin fuentes ni secretos
- **WHEN** se inspecciona el sistema de archivos de la imagen final
- **THEN** no contiene `secrets.properties`, archivos `.env`, `src/` ni `mvnw`
- **AND** el proceso corre con un usuario distinto de `root`

#### Scenario: Funciona con 512 MB y 0,1 CPU
- **WHEN** el contenedor corre con un límite de 512 MB de memoria y 0,1 CPU contra una base vacía
- **THEN** arranca, migra la base y responde el liveness sin ser terminado por falta de memoria

### Requirement: Blueprint de Render versionado
El repositorio SHALL incluir un `render.yaml` que declare el servicio web del backend (Docker, plan gratuito, rama `main`, despliegue con checks en verde, health check a liveness) con sus variables: los valores no secretos fijos en el archivo y las credenciales sin valor, para cargarlas en el panel de Render.

#### Scenario: Variables del servicio
- **WHEN** se lee `render.yaml`
- **THEN** fija `HEALTH_SHOW_DETAILS=never`, `SWAGGER_ENABLED=false` y `COOKIE_SECURE=true`
- **AND** declara sin valor `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ADMIN_USERNAME`, `DB_ADMIN_PASSWORD`, `JWT_SECRET` y `CORS_ALLOWED_ORIGINS`
- **AND** despliega la rama `main` con el health check en `/api/actuator/health/liveness`

### Requirement: Frontend con la API en el mismo origen
El frontend desplegado SHALL reenviar `/api/*` al backend y servir la app en cualquier otra ruta sin archivo estático. En producción, sin `VITE_API_URL`, el cliente SHALL usar la base relativa `/api`; en desarrollo, `http://localhost:8080/api`. El build SHALL definirse en el repositorio (`vercel.json` en la raíz).

#### Scenario: URL base por entorno
- **WHEN** se resuelve la URL base de la API sin `VITE_API_URL`
- **THEN** es `http://localhost:8080/api` en desarrollo y `/api` en producción
- **AND** con `VITE_API_URL` definida se usa ese valor en ambos

#### Scenario: Reglas de reenvío
- **WHEN** se lee `vercel.json`
- **THEN** `/api/*` va al backend antes del fallback de la app
- **AND** el build instala con el lockfile de la raíz y publica `modules/frontend/dist`

#### Scenario: Recargar una pantalla de la app
- **WHEN** se recarga `/patients/1`, `/auth/login` o `/auth/register` en la URL pública
- **THEN** se muestra la pantalla de la app, no una respuesta de la API

#### Scenario: La sesión sobrevive a recargar en producción
- **WHEN** un usuario inicia sesión en la URL pública y recarga la página (Chrome y Safari)
- **THEN** sigue con la sesión iniciada, porque el refresh recibe la cookie de primera parte

### Requirement: Origen del frontend permitido en el backend
En despliegue, `CORS_ALLOWED_ORIGINS` SHALL contener exactamente el origen público de producción del frontend (sin `/` final ni comodines), porque el proxy reenvía el `Origin` del navegador al backend.

#### Scenario: Refresh a través del proxy
- **WHEN** el navegador hace `POST /api/auth/refresh` en la URL pública con `CORS_ALLOWED_ORIGINS` igual a ese origen
- **THEN** el backend responde `200` y renueva la sesión, no `403`

### Requirement: Aislamiento entre clínicas en la base desplegada
En la base desplegada, la aplicación SHALL conectarse con un rol sin `SUPERUSER` ni `BYPASSRLS` (ni heredado), distinto del rol dueño de las tablas que usa Flyway, para que Row-Level Security aísle a cada clínica. El despliegue SHALL verificarlo antes de abrir la app a usuarios.

#### Scenario: Rol de la app sin privilegios que salten RLS
- **WHEN** se consulta `pg_roles` en la base desplegada
- **THEN** el rol de la aplicación tiene `rolsuper` y `rolbypassrls` en `false` y no hereda `neon_superuser`

#### Scenario: Dos clínicas no se ven entre sí
- **WHEN** dos clínicas registradas en la URL pública dan de alta un paciente cada una
- **THEN** cada una ve solo su paciente en su listado
- **AND** pedir por URL el paciente de la otra responde "no encontrado"

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

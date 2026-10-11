# Spec Delta — cloud-deployment

## Purpose

Cómo se empaqueta y publica Dentdesk en planes gratuitos: la imagen de contenedor del backend, el blueprint de Render, el frontend en Vercel con la API servida bajo su mismo origen, el aislamiento entre clínicas en la base desplegada (Neon) y la verificación de todo eso contra la URL pública. Es una capacidad técnica: no agrega comportamiento de negocio, decide dónde y cómo corre el que ya existe.

## ADDED Requirements

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

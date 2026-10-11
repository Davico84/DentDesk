# project-foundation Specification

## Purpose

El esqueleto ejecutable y validable del monorepo: un backend Spring Boot que levanta y aplica migraciones, un frontend React + Vite que compila/sirve y pasa `validate`, una estructura arquitectónica establecida y la validación de mensajes de commit. Provee una base estable (sin dominios de negocio) sobre la que nacen las capacidades futuras.

## Requirements

### Requirement: Backend base ejecutable
El backend SHALL arrancar como una aplicación Spring Boot y exponer un endpoint de salud que confirme que el servicio está operativo, sin depender de ninguna capacidad de negocio.

#### Scenario: Arranque de la aplicación
- **WHEN** se ejecuta `./mvnw spring-boot:run` con la configuración provista
- **THEN** la aplicación inicia sin errores
- **AND** una petición GET al endpoint de salud responde `200 OK`

#### Scenario: Configuración única y externalizada
- **WHEN** se inspecciona la configuración del backend
- **THEN** existe una **única** configuración (sin perfiles `dev`/`qa`/`pro`)
- **AND** todos los valores configurables (datasource, credenciales, secretos) se externalizan como variables de entorno/secretos
- **AND** ningún valor sensible está versionado (solo se versiona un archivo de ejemplo)

### Requirement: Migraciones de base de datos gestionadas por Flyway
El backend SHALL gestionar el esquema de base de datos exclusivamente con Flyway, con `spring.jpa.hibernate.ddl-auto=none`.

#### Scenario: Migración inicial en base limpia
- **WHEN** la aplicación arranca contra una base de datos PostgreSQL limpia
- **THEN** Flyway aplica las migraciones de `db/migration`
- **AND** Hibernate no crea ni altera tablas por su cuenta

### Requirement: Frontend base ejecutable y validable
El frontend SHALL compilar, construir y servir una SPA base con React + Vite, y pasar la validación de calidad completa.

#### Scenario: Build de producción
- **WHEN** se ejecuta `pnpm build`
- **THEN** el proyecto compila con TypeScript y genera el bundle sin errores

#### Scenario: Validación de calidad
- **WHEN** se ejecuta `pnpm validate`
- **THEN** typecheck, lint y tests se ejecutan y pasan

#### Scenario: Prueba base con MSW
- **WHEN** se ejecuta la suite de pruebas
- **THEN** existe al menos una prueba con React Testing Library que renderiza la app
- **AND** MSW intercepta las peticiones de red en el entorno de test

### Requirement: Estructura arquitectónica establecida
El código base SHALL seguir la arquitectura definida en `docs/architecture.md`: en el backend, la **lógica de negocio** vive separada de los **adaptadores técnicos** (`infra` es solo para lo técnico); en el frontend, la organización es por pantallas/módulos.

#### Scenario: Capas del backend
- **WHEN** se inspecciona el árbol de paquetes del backend
- **THEN** la lógica de negocio reside en `service.<dominio>` (no bajo `infra`)
- **AND** `infra` contiene únicamente adaptadores técnicos (p. ej. `config`, `security`, `storage`, `mail`)
- **AND** `presentation` no importa directamente de `persistence`
- **AND** `persistence` no depende de `presentation` ni de `service`
- **AND** `service` no depende de `presentation`

#### Scenario: Estructura del frontend
- **WHEN** se inspecciona `modules/frontend/src`
- **THEN** existen los directorios `layouts`, `screens`, `modules`, `store`, `routes` y `styles`
- **AND** `modules/core` no depende de otros módulos

### Requirement: Validación de mensajes de commit
El repositorio SHALL validar que los mensajes de commit cumplen Conventional Commits mediante un hook `commit-msg`.

#### Scenario: Commit con formato inválido
- **WHEN** se intenta crear un commit cuyo mensaje no cumple Conventional Commits
- **THEN** el hook `commit-msg` rechaza el commit con un mensaje de error explicativo

#### Scenario: Commit con formato válido
- **WHEN** se crea un commit con un mensaje del tipo `feat(scope): descripción`
- **THEN** el hook permite completar el commit

### Requirement: Aviso de backend listo al arrancar
Cuando el backend termina de arrancar y ya acepta peticiones, SHALL escribir en el log un aviso destacado y fácil de localizar con el nombre del producto, la URL base con el puerto real y el estado de la documentación de la API. El aviso SHALL NOT incluir secretos ni otros valores sensibles de configuración.

#### Scenario: Aviso con la URL real
- **WHEN** la aplicación termina de arrancar
- **THEN** el log muestra un bloque delimitado con el nombre del producto (Dentdesk, o `app.name` si se define) y la URL `http://localhost:<puerto>` con el puerto en el que realmente escucha, incluido el context-path si hay uno

#### Scenario: Documentación de la API activa
- **WHEN** la aplicación arranca con la documentación habilitada (`SWAGGER_ENABLED=true`)
- **THEN** el aviso incluye la URL de Swagger UI (`<url base>/swagger-ui.html`)

#### Scenario: Documentación de la API desactivada
- **WHEN** la aplicación arranca sin `SWAGGER_ENABLED`, o con `false` (lo está por defecto)
- **THEN** el aviso indica que Swagger UI está desactivado
- **AND** nombra el interruptor que hay que encender (`SWAGGER_ENABLED`)

#### Scenario: Sin secretos en el aviso
- **WHEN** se escribe el aviso
- **THEN** no contiene el secreto JWT, la contraseña de la base de datos ni ningún otro valor de configuración sensible

#### Scenario: Sin servidor web no hay aviso
- **WHEN** el contexto arranca sin servidor web (por ejemplo, un test sin puerto)
- **THEN** no se escribe el aviso

### Requirement: Tema definido por tokens CSS
El frontend SHALL definir su paleta (colores y radio) como variables CSS en un único archivo, `src/styles/globals.css`: valores claros en `:root` y oscuros en `.dark`. Tailwind SHALL generar las utilidades de color, incluidos los modificadores de opacidad, a partir de esas variables. El modo oscuro SHALL activarse con la clase `dark` en el elemento raíz. El tema de la organización SHALL seguir aplicándose en runtime sobre esas mismas variables.

#### Scenario: Utilidades generadas desde los tokens
- **WHEN** se compila `globals.css` con Tailwind pidiendo utilidades de tokens (`bg-primary`, `text-muted-foreground`, `bg-primary/10`, `bg-brand-cta`)
- **THEN** el CSS resultante resuelve cada utilidad a partir de la variable correspondiente (`--primary`, `--muted-foreground`, `--brand-cta`)

#### Scenario: Valores claros y oscuros de la paleta
- **WHEN** se inspecciona `globals.css`
- **THEN** cada token de color está definido en `:root` y en `.dark`

#### Scenario: Modo oscuro por clase
- **WHEN** se compila una utilidad con la variante `dark:` (p. ej. `dark:hidden`)
- **THEN** el selector generado depende de un ancestro con la clase `dark`, no de `prefers-color-scheme`

#### Scenario: El tema de la organización emite tokens válidos
- **WHEN** se deriva el tema de una organización a partir de su color de marca
- **THEN** cada token derivado es un color completo, en el mismo formato que los de `globals.css`
- **AND** los tokens de marca del producto (`--brand-*`) no forman parte del tema derivado

### Requirement: Sin colores literales en componentes
Los componentes y hooks del frontend MUST NOT contener colores literales (hex, `rgb()`, `hsl()`): cambiar un color SHALL requerir editar solo `globals.css`. Solo se exceptúan, en una lista explícita y justificada, los colores normativos o deliberadamente fijos del odontograma, los colores de marca de los presets (datos) y el generador del tema de cada clínica, que produce los tokens.

#### Scenario: Sin colores literales fuera de las excepciones
- **WHEN** se inspeccionan los archivos `.ts`/`.tsx` de `src/`, excepto el cliente generado, los tests y la lista de excepciones
- **THEN** ninguno contiene colores literales (hex, `rgb()`/`rgba()`, `hsl()`/`hsla()`)

#### Scenario: La lista de excepciones no esconde archivos que ya no las necesitan
- **WHEN** un archivo de la lista de excepciones deja de contener colores literales
- **THEN** la verificación falla y pide sacarlo de la lista

#### Scenario: Los colores del login no cambian con el tema de la clínica
- **WHEN** una organización aplica su color de marca y se muestra la pantalla de inicio de sesión
- **THEN** el botón de ingreso y los acentos del login usan los tokens de marca del producto, con los mismos valores que antes de la migración en modo claro y en modo oscuro

### Requirement: Endpoint de ping para mantener el backend despierto
El backend SHALL exponer `GET /actuator/health/liveness` sin autenticación. La respuesta SHALL ser `200` con `status` `UP` mientras la aplicación esté viva, y SHALL NOT incluir el indicador de la base de datos, de modo que el ping no consulte la base.

#### Scenario: Ping sin sesión
- **WHEN** un servicio externo llama a `GET /actuator/health/liveness` sin credenciales
- **THEN** recibe `200`
- **AND** el JSON contiene `status` `UP`
- **AND** la respuesta no contiene el indicador `db`

#### Scenario: Liveness independiente de la base
- **WHEN** el indicador de salud de la base está `DOWN`
- **THEN** `GET /actuator/health/liveness` sigue respondiendo `200` con `status` `UP`

### Requirement: Superficie pública de Actuator mínima
El detalle del health SHALL configurarse con la variable `HEALTH_SHOW_DETAILS` (por defecto `always`). Las únicas rutas de Actuator sin autenticación SHALL ser las de health; `/actuator/info` SHALL requerir autenticación.

#### Scenario: Detalle oculto en despliegue
- **WHEN** la aplicación arranca con `HEALTH_SHOW_DETAILS=never` y se llama a `GET /actuator/health`
- **THEN** la respuesta solo trae `status`, sin `components` ni detalles

#### Scenario: Detalle visible en local
- **WHEN** la aplicación arranca sin `HEALTH_SHOW_DETAILS` y se llama a `GET /actuator/health`
- **THEN** la respuesta trae los `components`, como hasta ahora

#### Scenario: Info no es pública
- **WHEN** se llama a `GET /actuator/info` sin credenciales
- **THEN** recibe `401`

### Requirement: Pool de conexiones que permite suspender la base
El pool de conexiones SHALL poder retirar todas las conexiones ociosas tras el tiempo configurado, sin mantener un mínimo ni ejecutar keepalive, para que una base que se suspende por inactividad (Neon) pueda dormir. `minimum-idle`, `idle-timeout` y `maximum-pool-size` SHALL configurarse por variables (`DB_POOL_MIN_IDLE`, `DB_POOL_IDLE_TIMEOUT_MS`, `DB_POOL_MAX_SIZE`).

#### Scenario: Configuración por defecto del pool
- **WHEN** la aplicación arranca sin esas variables
- **THEN** el pool tiene `minimumIdle` 0, `keepaliveTime` 0 (desactivado), `idleTimeout` de 60 s y `maximumPoolSize` 5

#### Scenario: Retiro de conexiones ociosas
- **WHEN** el pool abrió conexiones, quedan ociosas y pasa el `idleTimeout` (más la tolerancia de la revisión periódica del pool)
- **THEN** el pool queda sin conexiones

#### Scenario: La base responde tras quedar sin conexiones
- **WHEN** el pool no tiene conexiones y llega una petición que consulta la base
- **THEN** el pool abre una conexión nueva y la petición responde con normalidad

### Requirement: Pantalla de arranque en frío al cargar la app
Mientras la app espera el intento de restaurar la sesión al cargar, SHALL mostrar una pantalla de espera con la marca del producto según el tiempo y la conexión: 0–4 s, "Cargando…"; 4–90 s, "Preparando tu consultorio digital" con una barra de progreso estimada que no llega al 100 %; más de 90 s, "Está tardando más de lo normal" con un botón "Reintentar"; sin conexión, "Sin conexión a internet" en cualquier momento.

#### Scenario: Respuesta rápida
- **WHEN** el intento de restaurar la sesión termina antes de 4 segundos
- **THEN** solo se ve "Cargando…" y luego la app, sin la pantalla de arranque en frío

#### Scenario: Servidor despertando
- **WHEN** el intento de restaurar la sesión sigue pendiente a los 4 segundos
- **THEN** se muestra "Preparando tu consultorio digital" en una región `status`
- **AND** una barra de progreso (`progressbar`) con un valor menor que 100

#### Scenario: Espera demasiado larga
- **WHEN** el intento sigue pendiente a los 90 segundos
- **THEN** se muestra "Está tardando más de lo normal" con el botón "Reintentar", que recibe el foco
- **AND** al pulsarlo la app se recarga

#### Scenario: Sin conexión a internet
- **WHEN** el navegador no tiene conexión mientras el intento está pendiente
- **THEN** se muestra "Sin conexión a internet" en lugar de los estados de arranque
- **AND** al volver la conexión la app se recarga

### Requirement: Fin de la espera de arranque
La pantalla de arranque en frío SHALL desaparecer en cuanto termine el intento de restaurar la sesión, con éxito o con error, y la app SHALL seguir como sin ella. Cada cambio de estado SHALL anunciarse a lectores de pantalla una sola vez, y la pantalla SHALL NOT disparar peticiones propias ni dejar timers pendientes.

#### Scenario: Restauración exitosa tras la espera
- **WHEN** la sesión se restaura después de mostrarse la pantalla de arranque
- **THEN** la pantalla desaparece y la app continúa con la sesión

#### Scenario: Restauración fallida tras la espera
- **WHEN** el intento sigue pendiente más de 4 segundos y termina con error
- **THEN** la pantalla desaparece
- **AND** la sesión queda limpia y se muestra el login

#### Scenario: Llega la respuesta durante la espera larga
- **WHEN** el intento termina después de los 90 segundos, sin que el usuario pulse "Reintentar"
- **THEN** la pantalla desaparece y la app continúa

#### Scenario: Montaje doble en StrictMode
- **WHEN** el layout se monta, se desmonta y se vuelve a montar bajo React StrictMode
- **THEN** no se dispara una petición adicional de restauración
- **AND** la pantalla de arranque aparece una sola vez a los 4 segundos
- **AND** al terminar no quedan timers pendientes

### Requirement: Contacto de soporte en las pantallas de acceso
El login y el registro SHALL mostrar bajo el formulario el contacto de soporte de Dentdesk en una fila compacta: la pregunta "¿Problemas para entrar?" y dos botones de solo ícono, WhatsApp (en otra pestaña, con un mensaje inicial que nombra el producto) y correo, cada uno con nombre accesible y tooltip. La espera larga del arranque en frío SHALL mostrar esa fila en vez de remitir al administrador.

#### Scenario: Contacto en el login y el registro
- **WHEN** se abre el login o el registro, en escritorio o en celular
- **THEN** bajo el formulario se ve "¿Problemas para entrar?" con los botones de WhatsApp y de correo

#### Scenario: Botones del contacto
- **WHEN** la fila de soporte está a la vista
- **THEN** el botón de WhatsApp enlaza a `https://wa.me/51959396384` con el mensaje inicial, se abre en otra pestaña con `rel="noopener noreferrer"` y lo avisa en su nombre accesible
- **AND** el de correo es un enlace `mailto:` al correo de soporte, con la dirección en su nombre accesible
- **AND** ninguno usa el atributo `title`

#### Scenario: Tooltip de los botones
- **WHEN** se pasa el ratón sobre un botón de la fila o se lo enfoca con el teclado
- **THEN** un tooltip muestra a qué lleva (WhatsApp, o el correo)

#### Scenario: Contacto en la espera larga
- **WHEN** la pantalla de arranque llega a "Está tardando más de lo normal"
- **THEN** muestra la fila de soporte bajo "Reintentar", con "¿Sigue sin entrar?"
- **AND** ya no remite al administrador

#### Scenario: Sin red no se ofrece el contacto
- **WHEN** la pantalla de arranque muestra "Sin conexión a internet"
- **THEN** no muestra la fila de soporte, porque sin red no se pueden abrir sus enlaces

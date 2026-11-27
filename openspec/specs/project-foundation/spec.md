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

### Requirement: Aviso de espera durante el uso
Con la app ya iniciada, si alguna petición a la API sigue sin responder a los 4 segundos, la app SHALL mostrar un aviso "Despertando el servidor…" que desaparece cuando no queda ninguna petición lenta en curso. Pasados 90 segundos SHALL ofrecer "Reintentar" y el contacto de soporte; sin conexión, SHALL decir "Sin conexión a internet". Mientras la app se inicia, la pantalla de arranque en frío SHALL ser el único aviso.

#### Scenario: Petición rápida sin aviso
- **WHEN** una petición a la API responde antes de 4 segundos
- **THEN** no aparece ningún aviso

#### Scenario: Petición lenta durante el uso
- **WHEN** con la app iniciada una petición sigue pendiente a los 4 segundos
- **THEN** aparece "Despertando el servidor…" en una región `status`
- **AND** al responder la petición, el aviso desaparece y la pantalla continúa con los datos

#### Scenario: Varias peticiones lentas a la vez
- **WHEN** dos peticiones siguen pendientes a los 4 segundos y una responde antes que la otra
- **THEN** el aviso sigue visible hasta que responde la última

#### Scenario: Petición lenta que termina con error
- **WHEN** una petición lenta termina con un error
- **THEN** el aviso desaparece y la pantalla maneja el error como siempre

#### Scenario: Espera demasiado larga
- **WHEN** una petición sigue pendiente a los 90 segundos
- **THEN** el aviso ofrece "Reintentar", que recarga la app, y los botones de soporte

#### Scenario: Sin conexión durante una espera
- **WHEN** hay una petición lenta y el navegador está sin conexión
- **THEN** el aviso dice "Sin conexión a internet" en lugar de "Despertando el servidor…"

#### Scenario: Durante el arranque no se duplica el aviso
- **WHEN** la app se está iniciando y la restauración de la sesión tarda más de 4 segundos
- **THEN** se ve la pantalla de arranque en frío y no el aviso de espera

### Requirement: Envío de correo seguro por defecto
Con el correo habilitado, el backend SHALL enviar por SMTP con STARTTLS obligatorio y autenticación, salvo que el modo local esté encendido explícitamente. Si el servidor no ofrece STARTTLS, el envío MUST fallar antes de transmitir el mensaje, en vez de seguir sin cifrar.

#### Scenario: Configuración por defecto
- **WHEN** el backend arranca con el correo habilitado y sin el modo local
- **THEN** el conector SMTP tiene STARTTLS habilitado y obligatorio, y autenticación habilitada

#### Scenario: Servidor sin STARTTLS en modo normal
- **WHEN** el modo local está apagado y el servidor SMTP no anuncia STARTTLS
- **THEN** el envío de la invitación falla con el mismo error de entrega que cualquier fallo SMTP
- **AND** la conversación SMTP termina antes de `DATA`: el mensaje no se transmite

### Requirement: Modo de correo local acotado a la máquina
Un modo local, apagado por defecto, SHALL permitir enviar a un servidor SMTP de prueba sin STARTTLS ni autenticación. Con el correo habilitado y el modo local encendido, el backend MUST negarse a arrancar si el host SMTP no es exactamente `localhost`, `127.0.0.1` o `::1`, sin mostrar credenciales en el error.

#### Scenario: Invitación en modo local
- **WHEN** el modo local está encendido, el host SMTP es `localhost` y se emite una invitación
- **THEN** el backend arranca
- **AND** la invitación se entrega al servidor de prueba sin `STARTTLS` ni `AUTH`

#### Scenario: Recuperación de contraseña en modo local
- **WHEN** el modo local está encendido con host `localhost` y se pide la recuperación de una cuenta activa
- **THEN** el enlace de recuperación se entrega al servidor de prueba sin `STARTTLS` ni `AUTH`

#### Scenario: Hosts locales aceptados
- **WHEN** el modo local está encendido y el host SMTP es `localhost`, `LOCALHOST`, `127.0.0.1`, `::1` o alguno de ellos con espacios alrededor
- **THEN** el backend arranca

#### Scenario: Host remoto o ambiguo rechazado
- **WHEN** el modo local está encendido y el host SMTP es `smtp-relay.brevo.com`, `localhost.`, `127.0.0.2`, `[::1]`, `0.0.0.0` o está vacío
- **THEN** el backend no arranca

#### Scenario: Error del arranque rechazado sin secretos
- **WHEN** el backend no arranca por un host no local en modo local
- **THEN** el error nombra `MAIL_LOCAL_SMTP` y, si es un nombre o una dirección, el host rechazado
- **AND** no contiene el usuario ni la contraseña SMTP

#### Scenario: Host con credenciales o caracteres de control
- **WHEN** el modo local está encendido y el host trae usuario y clave (`usuario:clave@host`), saltos de línea u otros caracteres de control
- **THEN** el backend no arranca
- **AND** el error nombra `MAIL_LOCAL_SMTP` pero no muestra ese valor

#### Scenario: Modo local con el correo deshabilitado
- **WHEN** el modo local está encendido pero el correo está deshabilitado, con cualquier host
- **THEN** el backend arranca igual que sin correo: la invitación se rechaza y los correos de la cuenta se descartan

### Requirement: CI completa en cada pull request
Todo pull request hacia `main` o `dev` SHALL ejecutar los seis checks de CI (backend, los cuatro de frontend y OpenSpec), cualesquiera sean los archivos que cambia. Los pushes a `main` y `dev` MAY seguir filtrando por área: un push que no toca el área de un workflow no lo ejecuta.

#### Scenario: PR solo de documentación
- **WHEN** se abre un PR hacia `dev` que solo cambia archivos de `docs/`
- **THEN** se ejecutan los seis checks

#### Scenario: PR solo de backend
- **WHEN** se abre un PR hacia `dev` que solo cambia `modules/backend/`
- **THEN** se ejecutan también los cuatro checks de frontend y el de OpenSpec

#### Scenario: Release hacia main
- **WHEN** se abre el PR `dev` → `main`
- **THEN** se ejecutan los seis checks, aunque el release no toque alguna de las áreas

#### Scenario: Push que no toca el área
- **WHEN** se mergea a `dev` un cambio que solo toca `docs/`
- **THEN** el push resultante no ejecuta los workflows de backend, frontend ni OpenSpec

### Requirement: Nombres únicos de los checks de CI
Cada uno de los seis checks de CI SHALL publicarse con un nombre único en el repositorio que identifique su área: `backend-verify`, `frontend-lint`, `frontend-codegen-drift`, `frontend-test`, `frontend-e2e-smoke` y `openspec-validate`. Ningún otro job de un workflow del repositorio MUST publicar un check con alguno de esos nombres.

#### Scenario: Nombres publicados
- **WHEN** termina la CI de un PR
- **THEN** los check runs del commit se llaman exactamente como los seis nombres, publicados por la app GitHub Actions

#### Scenario: Nombre repetido en otro workflow
- **WHEN** un PR agrega un workflow con un job cuyo check se llamaría `frontend-test`
- **THEN** el check `openspec-validate` de ese PR falla nombrando el check repetido

#### Scenario: Nombre exigido que desaparece
- **WHEN** un PR renombra o quita uno de los seis jobs
- **THEN** el check `openspec-validate` falla nombrando el check que falta

### Requirement: Protección de main con checks obligatorios
La rama `main` SHALL aceptar cambios solo por pull request, con los seis checks en verde publicados por la app GitHub Actions; sin aprobaciones exigidas. `main` MUST rechazar el push directo, el force push y el borrado. No SHALL exigirse que la rama del PR esté al día con `main`.

#### Scenario: Release con los checks en verde
- **WHEN** el PR `dev` → `main` tiene los seis checks en verde
- **THEN** se puede mergear sin aprobaciones y sin traer `main` a `dev`

#### Scenario: Un check en rojo
- **WHEN** alguno de los seis checks del PR hacia `main` falla
- **THEN** GitHub no permite el merge

#### Scenario: Un check que no llega
- **WHEN** uno de los seis checks no se publicó en el PR hacia `main`
- **THEN** el merge queda bloqueado como pendiente

#### Scenario: Check con el mismo nombre de otra app
- **WHEN** una app distinta de GitHub Actions publica un check `backend-verify` en verde
- **THEN** no cuenta para el Ruleset

#### Scenario: Push directo o force push
- **WHEN** alguien intenta un push directo, un force push o borrar `main`
- **THEN** GitHub lo rechaza

### Requirement: Ruleset de main versionado
La configuración del Ruleset de `main` SHALL vivir en el repositorio como un archivo versionado, y lo aplicado en GitHub MUST coincidir con ese archivo. Cambiar la protección de `main` SHALL hacerse editando ese archivo en un PR y volviéndolo a aplicar, no solo en la interfaz de GitHub.

#### Scenario: Comparar con lo aplicado
- **WHEN** se compara el Ruleset activo en GitHub con el archivo del repositorio
- **THEN** coinciden la rama objetivo, los checks con su app, las reglas y el modo de aplicación

#### Scenario: Cambio hecho solo en la interfaz
- **WHEN** alguien cambia el Ruleset en la interfaz sin tocar el archivo
- **THEN** la comparación repetible lo detecta como diferencia y termina con error

#### Scenario: Mismo contenido en otro orden
- **WHEN** GitHub devuelve las reglas o los checks en otro orden que el archivo
- **THEN** la comparación no reporta diferencia

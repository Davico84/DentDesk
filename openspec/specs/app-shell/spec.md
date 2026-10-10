# app-shell Specification

## Purpose

Marco de la zona privada de Dentdesk: layout con barra lateral y cabecera responsive, navegación declarada por configuración con roles, identidad del usuario y de la clínica, cierre de sesión y una pantalla de Inicio con atajos. Capacidad técnica/plataforma, solo frontend: las métricas del Inicio pertenecen a la futura capacidad `dashboard`.

## Requirements

### Requirement: Layout privado común
El sistema SHALL renderizar todas las rutas autenticadas dentro de un layout común con una barra lateral de navegación, una cabecera y un área de contenido principal (un único `<main>`). Las rutas de invitado (login, registro) MUST NOT mostrar este layout.

#### Scenario: Ruta privada dentro del shell
- **WHEN** un usuario autenticado navega a una ruta privada
- **THEN** el sistema muestra la barra lateral, la cabecera y el contenido de la ruta en el área principal
- **AND** la página contiene un solo elemento `<main>`

#### Scenario: Rutas de invitado sin shell
- **WHEN** un usuario sin sesión abre la pantalla de login o de registro
- **THEN** el sistema muestra la pantalla de autenticación sin barra lateral ni cabecera del shell

### Requirement: Navegación declarada por configuración
El sistema SHALL construir los ítems de la barra lateral y el árbol de rutas privadas desde una única configuración de secciones (ruta, roles opcionales, etiqueta e ícono). Una sección sin roles SHALL verla todo usuario autenticado; con roles, solo esos. El ítem de la sección activa, incluidas sus subrutas, SHALL marcarse con `aria-current="page"`.

#### Scenario: Ítem activo en una subruta
- **WHEN** el usuario está en la ficha de un paciente o en su Historia Clínica
- **THEN** el ítem "Pacientes" se marca como activo (`aria-current="page"`) y ningún otro lo está

#### Scenario: Ítem sin roles visible para todos
- **WHEN** una sección no declara roles
- **THEN** su ítem lo ven todos los usuarios autenticados

#### Scenario: Ítem restringido oculto para otros roles
- **WHEN** un usuario `USER` abre el shell (barra lateral o cajón)
- **THEN** el ítem de Configuración › Marca no aparece
- **AND** un usuario `ADMIN` sí lo ve

#### Scenario: Menú y rutas derivados de la misma configuración
- **WHEN** una sección declara roles en la configuración
- **THEN** su ítem de menú y su subárbol de rutas aplican exactamente esos roles, sin declararlos en otro sitio

#### Scenario: Ruta privada inexistente
- **WHEN** un usuario autenticado navega a una ruta que no existe
- **THEN** el sistema muestra la página de no encontrado existente

### Requirement: Acceso denegado dentro del shell
Cuando un usuario sin el rol requerido llega a una sección restringida o a una de sus subrutas, el sistema SHALL mostrar el estado de acceso denegado dentro del contenido principal del shell (sin anidar otro `<main>`), darle el foco y ofrecer volver al inicio, sin renderizar la sección ni cargar sus datos.

#### Scenario: Llegar por URL a una sección sin permiso
- **WHEN** un usuario `USER` navega directamente a la ruta de Configuración › Marca
- **THEN** ve el estado de acceso denegado dentro del shell, con el foco en su título y un enlace para volver al inicio
- **AND** no se pide la marca de la organización para editarla

### Requirement: Identidad del usuario y de la clínica
La cabecera SHALL mostrar el nombre completo del usuario (o su correo si no tiene nombre), su rol, el nombre de la clínica activa y un conmutador de tema claro/oscuro. La barra lateral SHALL mostrar el logo de la organización (o el del producto si no tiene) y, al pie, la acción de cerrar sesión, que reutiliza el logout de `authentication`.

#### Scenario: Identidad con nombre
- **WHEN** el usuario autenticado tiene nombre completo
- **THEN** la cabecera muestra su nombre completo, su rol y el nombre de la clínica activa

#### Scenario: Identidad sin nombre
- **WHEN** el usuario autenticado no tiene nombre completo
- **THEN** la cabecera muestra su correo electrónico, su rol y la clínica activa

#### Scenario: Cerrar sesión desde la barra lateral
- **WHEN** el usuario pulsa "Cerrar sesión" al pie de la barra lateral
- **THEN** el sistema ejecuta el logout y el botón queda deshabilitado mientras la petición está en curso

### Requirement: Navegación responsive
El sistema SHALL adaptar la barra lateral al ancho de pantalla: expandida o contraída según la preferencia del usuario en escritorio (≥ 1024 px); solo íconos en tablet, cada uno con su nombre accesible y un tooltip al pasar el ratón o enfocarlo; y en celular, oculta tras un botón de menú como cajón que se cierra al navegar, al pulsar fuera o con `Escape`.

#### Scenario: Abrir el cajón en celular
- **WHEN** en celular el usuario pulsa el botón de menú
- **THEN** el cajón de navegación se abre y el botón refleja el estado con `aria-expanded="true"`

#### Scenario: Cerrar el cajón al navegar
- **WHEN** con el cajón abierto el usuario selecciona un ítem de navegación
- **THEN** el sistema navega a la ruta y cierra el cajón

#### Scenario: Cerrar el cajón con Escape o clic fuera
- **WHEN** con el cajón abierto el usuario pulsa `Escape` o hace clic en el fondo
- **THEN** el cajón se cierra sin navegar

#### Scenario: Tooltip en la barra de íconos
- **WHEN** en tablet el usuario pasa el ratón sobre un ítem o el cierre de sesión, o lo enfoca con el teclado
- **THEN** aparece un tooltip con su nombre; el ítem conserva su nombre accesible y no tiene atributo `title`

#### Scenario: Sin desplazamiento horizontal
- **WHEN** se abre cualquier pantalla privada a 375, 768 o 1280 px de ancho
- **THEN** la página no se desplaza de costado y la navegación queda alcanzable

### Requirement: Barra lateral contraíble en escritorio
En pantallas de 1024 px o más, el sistema SHALL permitir contraer la barra lateral a solo íconos y volver a expandirla con un botón que refleje su estado con `aria-expanded`. La preferencia SHALL ser del usuario, igual en todas las pantallas y recordada en el navegador; el sistema SHALL NOT contraerla ni expandirla por su cuenta.

#### Scenario: Contraer la barra lateral
- **WHEN** en escritorio el usuario pulsa "Contraer barra lateral"
- **THEN** la barra muestra solo los íconos, con tooltip y nombre accesible, y el contenido ocupa el ancho liberado
- **AND** el botón pasa a "Expandir barra lateral" con `aria-expanded="false"`

#### Scenario: La preferencia se recuerda
- **WHEN** el usuario contrae la barra y luego recarga la página o va a otra sección
- **THEN** la barra sigue contraída

#### Scenario: Sin almacenamiento disponible
- **WHEN** el navegador no permite guardar la preferencia
- **THEN** la barra se puede contraer y expandir igual durante la visita

#### Scenario: Tablet y celular no cambian
- **WHEN** la app se abre en una pantalla de menos de 1024 px
- **THEN** el botón para contraer no se muestra

### Requirement: Ancho máximo del marco de la app
En pantallas anchas, el marco de las pantallas privadas (barra lateral, cabecera y contenido) SHALL medir como máximo 1920 px y quedar centrado, con la barra lateral junto al contenido. El contenido de cada pantalla SHALL conservar su ancho uniforme, igual en todas las secciones.

#### Scenario: App centrada en una pantalla muy ancha
- **WHEN** el usuario abre una pantalla privada en un monitor de 2560 px
- **THEN** la app mide como máximo 1920 px y queda centrada, con la barra lateral junto al contenido y no pegada al borde de la pantalla

#### Scenario: Mismo ancho de contenido en todas las secciones
- **WHEN** el usuario pasa del listado de pacientes a la ficha, la Historia Clínica o el odontograma
- **THEN** el contenido conserva el mismo ancho máximo

### Requirement: Inicio con bienvenida y atajos
La ruta de inicio SHALL mostrar, dentro del shell, un saludo con el nombre del usuario y la clínica activa, y atajos a las acciones disponibles: registrar un paciente y buscar pacientes para todos, y personalizar la marca solo para `ADMIN`. NO SHALL mostrar métricas ni datos de ejemplo.

#### Scenario: Inicio de un usuario
- **WHEN** un usuario `USER` abre el inicio
- **THEN** ve el saludo con su nombre y la clínica, y los atajos a nuevo paciente y a buscar pacientes
- **AND** no ve el atajo a la marca

#### Scenario: Inicio de un administrador
- **WHEN** un usuario `ADMIN` abre el inicio
- **THEN** ve además el atajo para personalizar la marca

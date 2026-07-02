# Patrones de UI, Animación y Responsividad

> Parte de la documentación de OdontoRisas. Complementa a [docs/architecture.md](./architecture.md) y [docs/testing.md](./testing.md).

Este documento recopila las decisiones de diseño estético, reglas de accesibilidad, responsividad y lineamientos de animación con **Framer Motion** y **shadcn/ui**.

---

## 1. shadcn/ui e Integración de UI

### Regla de Reuso Obligatorio
> [!IMPORTANT]
> **Antes de crear o añadir un nuevo componente visual o biblioteca de terceros**, debes revisar exhaustivamente si ya existe una solución o componente atómico en `frontend/src/modules/core/ui/`.
>
> Reutilizar los componentes existentes garantiza que se respeten automáticamente:
> 1. El soporte de temas (Modo claro/oscuro).
> 2. Sobrescrituras de marca (como el `auth-theme` usado en los formularios de autenticación).
> 3. Estilos consistentes de bordes, sombras, transiciones y accesibilidad.
> 4. Prevención de código duplicado e incremento innecesario en el tamaño del bundle.

### Tooltip antes que `title`
> [!IMPORTANT]
> **Nunca uses el atributo nativo `title` en un botón (u otro elemento interactivo) para describir su acción.** En su lugar usa el componente `Tooltip`, ubicado por convención en `frontend/src/modules/core/ui/tooltip.tsx`.
>
> El componente `Button` de core/ui ya acepta una prop `tooltip` que lo envuelve automáticamente en un `Tooltip`; úsala. El `title` nativo es inconsistente con el tema, no es estilizable y tiene un retardo fijo del navegador.
>
> Mantén siempre el `aria-label` para accesibilidad (el tooltip no lo sustituye).

### Ubicación del Sistema de Diseño:
* Todos los componentes básicos de presentación (botones, modales, menús flotantes, tablas, inputs) se ubican bajo `frontend/src/modules/core/ui/`.
* **Componentes clave reutilizados**:
  * `Dialog`, `Sheet`, `Popover`: Para diálogos contextuales y paneles deslizantes.
  * `DropdownMenu`, `Select`: Para listas de selección e interfaces dinámicas.
  * `Tooltip`: Para descripciones flotantes rápidas (vital en vistas colapsadas).
  * `Table`: Estructuración avanzada de listas de datos.
  * `Badge`: Visualización compacta de estados y contadores numéricos.

---

## 2. Lineamientos de Animación (Framer Motion)

Utilizamos **Framer Motion** para enriquecer la experiencia visual del usuario (micro-interacciones, transiciones de layout fluidas y apariciones graduales) sin comprometer el rendimiento general.

### Reglas de Uso:
1. **Presencia y Transiciones de DOM**:
   * Utiliza `<AnimatePresence>` para componentes que entran o salen del árbol de React (ej. diálogos emergentes, menús colapsables móviles).
   ```tsx
   <AnimatePresence mode="wait">
     {(!collapsed || isMobile) && (
       <motion.div
         initial={{ opacity: 0, x: -10 }}
         animate={{ opacity: 1, x: 0 }}
         exit={{ opacity: 0, x: -10 }}
       >
         {label}
       </motion.div>
     )}
   </AnimatePresence>
   ```
2. **Animaciones de Layout**:
   * Usa las propiedades `layout` o `layoutId` para transiciones fluidas de elementos que cambian de tamaño, posición o que se desplazan de un contenedor a otro (ej. el píldora/fondo indicador de pestaña activa en menús deslizantes).
3. **Micro-interacciones**:
   * Keep it fast: Las animaciones deben ser sutiles y rápidas (usualmente entre `0.2s` y `0.3s`).
   * Prefiere físicas de tipo resorte (`spring`) con rigidez (`stiffness`) y amortiguación (`damping`) adecuados antes que curvas lineales, para dar una sensación natural y elástica.
4. **Respeto del Estado Colapsado**:
   * Las animaciones deben calcularse y adaptarse limpiamente cuando un panel se colapsa. Asegúrate de ocultar el ancho (`width: 0` o `opacity: 0`) o utilizar transiciones de CSS condicionales para evitar desbordamientos de texto.

---

## 3. Responsividad (Responsive Design)

La aplicación sigue estrictamente una filosofía **Mobile-First**.

### Breakpoints Estándar:
* **Móvil**: Resoluciones inferiores a `480px`.
* **Tablet / Pantalla Mediana**: Entre `481px` y `1024px`.
* **Escritorio**: Superiores a `1024px`.

### Reglas de Diseño Responsivo:
* **Prueba de Cambios**: Cualquier modificación en componentes de navegación, Sidebar u hojas de estilo principales debe evaluarse visualmente en móviles pequeños, tabletas y pantallas anchas.
* **Componentes Adaptables**:
  * La Sidebar se oculta por completo en móviles (`max-[480px]`) convirtiéndose en un cajón flotante lateral con backdrop activado, mientras que en pantallas medianas se reduce a una barra estrecha de iconos y en escritorio se expande por completo de forma estática.

---

## 4. Accesibilidad (Accessibility - WCAG)

La accesibilidad no es un requerimiento secundario y no debe sacrificarse por decisiones estéticas:

* **HTML Semántico**: Usa etiquetas HTML5 adecuadas (`<aside>`, `<nav>`, `<main>`, `<header>`, `<article>`) en lugar de abusar de `<div>`.
* **Navegación por Teclado**: Todo botón o enlace interactivo debe ser accesible mediante la tecla `Tab` y activable con `Enter` o `Espacio`.
* **Asociación de Formularios**: Todos los `input` deben contar con su respectivo `label` enlazado explícitamente mediante la propiedad `htmlFor` e `id`.
* **Etiquetas aria**: Utiliza `aria-label` para dar contexto a botones que solo contienen iconos (ej. botón de cerrar sesión o cambiar tema) y `aria-describedby` para asociar textos informativos o errores de validación.

---

## 5. Paleta de Colores y Variables de Estilo (Tailwind vs CSS Variables)

El proyecto utiliza TailwindCSS (v3) configurado para resolver los colores del tema dinámicamente mediante variables CSS definidas por convención en `frontend/src/styles/globals.css`.

### Compatibilidad de Formato (Importante)
> [!WARNING]
> Nuestro archivo de configuración `frontend/tailwind.config.cjs` declara los colores mapeados con la función HSL de la siguiente manera:
>
> `background: "hsl(var(--background))"`
>
> Esto exige que las variables CSS contengan **únicamente los números y porcentajes separados por espacios**, sin envoltorio (ej. `--background: 220 25% 98%`).
>
> Si copias o importas variables CSS de diseños externos basados en Tailwind v4 o modernos (como `crm-dashboard-design/app/globals.css`), ten en cuenta que estos suelen utilizar la notación OKLCH directamente en el CSS (ej. `--background: oklch(0.98 0 0)`).
>
> Si intentas inyectar estas variables sin convertirlas, la salida final compilará erróneamente a `hsl(oklch(0.98 0 0))`, rompiendo los estilos. Por lo tanto:
> 1. Convierte las variables a valores HSL puros (ej. usando convertidores en línea de color).
> 2. Pega únicamente los 3 canales separados por espacios (Hue, Saturation, Lightness) en nuestro `frontend/src/styles/globals.css`.

---

## 6. Composición de Componentes: Patrón "Slots" / "Children"

Cuando un componente impone una **estructura** (layout, espaciado, bordes, animación) pero el **contenido** lo decide quien lo consume, prefiere componer ese contenido como `children` o como *slots* nombrados (props de tipo `ReactNode`) en lugar de pasarlo como datos dentro de props.

### Las tres formas de pasar contenido (de mayor a menor a preferir)

1. **`children` (slot por defecto)** — el caso más común y el más legible. El padre define el "marco" y el hijo va dentro:
   ```tsx
   // Componente reutilizable: solo dueño de la estructura.
   function Panel({ children }: { children: ReactNode }) {
     return <section className="rounded-lg border bg-card p-4">{children}</section>;
   }

   // Uso: el contenido es JSX natural, no un dato.
   <Panel>
     <ChatManagement />
   </Panel>
   ```

2. **Slots nombrados (props `ReactNode`)** — cuando hay varias "zonas" estructurales (cabecera, acciones, cuerpo, pie). Cada zona es una prop opcional; el componente decide dónde y cómo se coloca:
   ```tsx
   interface SectionShellProps {
     title: ReactNode;
     actions?: ReactNode;   // slot opcional (botones, filtros)
     children: ReactNode;   // slot principal
   }

   function SectionShell({ title, actions, children }: SectionShellProps) {
     return (
       <div className="flex h-full flex-col gap-4">
         <header className="flex items-center justify-between border-b border-border pb-4">
           <h2 className="text-lg font-semibold">{title}</h2>
           {actions}
         </header>
         <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
       </div>
     );
   }
   ```

3. **Componentes compuestos (compound components)** — para APIs declarativas con varias partes relacionadas que comparten contexto implícito. Úsalo solo cuando aporta legibilidad real frente a slots:
   ```tsx
   <Tabs activeKey={active} onChange={setActive}>
     <Tabs.Tab tabKey="chat" label={t("admin.chat.viewLabel")}>
       <ChatManagement />
     </Tabs.Tab>
   </Tabs>
   ```

### Cuándo SÍ usar slots/children

- El componente es **reutilizable** y solo aporta estructura/estilo/animación (layouts, *cards*, modales, *page shells*, paneles).
- El contenido lo conoce quien escribe el JSX (es **estático**, no derivado de datos).
- Quieres que el contenido sea JSX natural y legible, sin envolverlo en objetos de configuración.

### Cuándo NO usar slots/children (preferir config/array o *render props*)

> [!IMPORTANT]
> Si el componente debe **decidir cuál de varios elementos renderizar** a partir de **datos dinámicos**, o necesita metadatos por elemento (clave, etiqueta, icono, permisos), un API de **lista de configuración** (`items: Option[]`) o de *render prop* es más apropiado que los slots.
>
> Convención en este proyecto: un contenedor multivista (por ejemplo, un `MultiViewContainer` en `frontend/src/modules/core/components/`) debe recibir sus vistas como `views: ViewOption[]` cuando la pantalla que lo consume genera esas vistas dinámicamente desde una configuración de tabla con un `.map()` (por ejemplo, una `TablePage` en `frontend/src/screens/admin/`). Forzar `children` en ese caso complicaría el escenario dinámico sin beneficio.

### Reglas

1. **Estructura en el padre, contenido en el consumidor.** Un componente que solo "envuelve" debe exponer `children`, no recibir el contenido como un objeto de datos.
2. **No envuelvas JSX en props de datos sin necesidad.** Pasar `component: <X />` dentro de un array solo se justifica si el contenedor maneja una **colección dinámica** de elementos (ver el caso de un contenedor multivista alimentado por una `TablePage` dinámica). Para un único contenido fijo, usa `children`.
3. **Respeta la regla de abstracción** (ver los estándares de codificación del equipo): no introduzcas *compound components* hasta tener 2–3 casos reales que lo pidan.
4. **Accesibilidad:** al usar slots para *tabs*, *dialogs* o paneles, mantén los roles y atributos ARIA (`role="tabpanel"`, `aria-controls`, etc.) en el componente estructural, no en el contenido inyectado.

### Aplicación a las páginas de `/screens`

> [!NOTE]
> Las **pantallas** (`frontend/src/screens/**`) son hojas de composición: montan una feature dentro de un layout. **No** son el lugar para introducir slots/children — el patrón rinde en componentes **reutilizables** de `core`, no en las páginas.
>
> Señal de mal uso: páginas que envuelven una **única** vista en un contenedor multivista con `onViewChange={() => {}}` (no-op). Con una sola vista el conmutador no se renderiza, así que el contenedor solo aporta el panel animado + *scroll*. Si una página tiene una sola vista, considera renderizar la feature directamente o, si se mantiene el contenedor por consistencia visual, acepta que el `onViewChange` no-op es deuda menor —no la resuelvas con slots.

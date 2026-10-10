# Spec Delta — project-foundation

## ADDED Requirements

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

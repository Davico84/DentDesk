# Reporte de Análisis y Propuesta de Mejoras: Personalización de Marca (Organization Branding)

Este documento presenta un análisis exhaustivo de los archivos de especificación ([spec.md](file:///d:/proyectos/drivenSpec/OdontoRisas/Odontorisas/openspec/changes/add-organization-branding/specs/organization-branding/spec.md)), diseño ([design.md](file:///d:/proyectos/drivenSpec/OdontoRisas/Odontorisas/openspec/changes/add-organization-branding/design.md)) y tareas ([tasks.md](file:///d:/proyectos/drivenSpec/OdontoRisas/Odontorisas/openspec/changes/add-organization-branding/tasks.md)) para la implementación de la personalización visual por organización.

---

## 1. Inconsistencia Crítica: El Favicon

Existe una contradicción directa entre los requerimientos declarados, el diseño técnico y las tareas de ejecución con respecto al **Favicon**:

*   **En la especificación ([spec.md](file:///d:/proyectos/drivenSpec/OdontoRisas/Odontorisas/openspec/changes/add-organization-branding/specs/organization-branding/spec.md)):**
    *   *Escenario "El administrador actualiza la marca":* Menciona que el administrador guarda un color, un logo y un **favicon** válidos.
    *   *Escenario "El favicon es el del producto siempre":* Afirma explícitamente que la pestaña del navegador muestra siempre el favicon del producto y que **ninguna organización puede cambiarlo**.
*   **En el diseño ([design.md](file:///d:/proyectos/drivenSpec/OdontoRisas/Odontorisas/openspec/changes/add-organization-branding/design.md)):**
    *   *Decisión D1:* Indica de forma categórica que **el favicon NO se personaliza** debido al agresivo almacenamiento en caché de los navegadores y para preservar la identidad del producto base. El modelo de base de datos no incluye `favicon_url`.
*   **En las tareas ([tasks.md](file:///d:/proyectos/drivenSpec/OdontoRisas/Odontorisas/openspec/changes/add-organization-branding/tasks.md)):**
    *   Las tareas `1.1`, `2.3`, `5.1`, `5.4`, `6.1` y `8.3` incluyen la creación del campo en base de datos (`favicon_url`), validación en DTOs, soporte en el `ThemeManager` del frontend, campo de entrada en el editor y resolución de dudas sobre su formato.

### 💡 Propuesta de Mejora:
Alinear todos los documentos bajo la decisión técnica de **NO personalizar el favicon**. Mantenerlo como no-personalizable evita problemas de caché de DNS/navegador (donde los favicons quedan "pegados" en la barra de marcadores del usuario) y simplifica el desarrollo.
*   **Acción:** Eliminar las tareas asociadas al favicon en `tasks.md` (campos en base de datos, DTOs, frontend y UI del editor).
*   **Acción:** Corregir el escenario en `spec.md` para eliminar la mención al favicon en la edición.

---

## 2. Resolución de Preguntas Abiertas (Open Questions)

### A. Espacio de Color: HSL vs. OKLCH (D3)
*   **Contexto:** El proyecto ya usa triadas HSL sin envoltura en sus CSS variables (por ejemplo, `--primary: 292 72% 36%`), lo cual permite a Tailwind aplicar modificadores de opacidad (como `bg-primary/50`). OKLCH es visualmente más uniforme para el contraste percibido, pero requiere librerías externas complejas para la conversión en JS o soporte avanzado del navegador en runtime.
*   **Decisión propuesta:** **Usar HSL para el MVP** por paridad directa con el sistema existente y simplicidad matemática en el frontend.
*   **Mejora de Algoritmo:** Para evitar que colores elegidos por el usuario se vean ilegibles en modo oscuro, proponemos aplicar una regla de **lightness clamping** en el frontend:
    *   *Modo Claro:* Si la luminosidad ($L$) del color de marca elegido es $> 85\%$, se reduce a un máximo de $80\%$ para elementos interactivos principales (asegurando legibilidad en bordes y focos).
    *   *Modo Oscuro:* Si la luminosidad ($L$) es $< 50\%$, se escala proporcionalmente a un rango de $55\% - 65\%$ al inyectar `--primary` en la clase `.dark`. De esta forma, un color morado muy oscuro seguirá siendo morado pero brillará lo suficiente sobre el fondo oscuro de la aplicación (`#170d1a`).

### B. ¿Uno o Dos Colores de Marca? (D3)
*   **Contexto:** Un solo color de marca (monocromático) es más fácil de contrastar, pero limita la identidad visual de la clínica (por ejemplo, OdontoRisas usa por defecto púrpura + teal como acento).
*   **Decisión propuesta:** **Soportar color primario y un color secundario opcional**.
    *   *Si la clínica define color secundario:* Convertimos este hex a HSL y mapeamos `--secondary` y `--accent` basándonos en su Hue ($H$).
    *   *Si la clínica NO define color secundario (es `null`):* Derivamos automáticamente un color de acento/secundario utilizando un tono complementario o una versión desaturada y aclarada del color primario (por ejemplo, $H_{primary}$, $S_{primary} \times 0.5$, $L_{primary} \times 1.8$ para modo claro). Esto garantiza una estética premium sin obligar a la clínica a configurar más de un color.

---

## 3. Mejoras en la Gestión de Caché y Seguridad de Sesión

### A. Prevención de Parpadeo de Marca en Estaciones Compartidas (D8)
En clínicas dentales es común que múltiples doctores y recepcionistas utilicen la misma computadora e intercambien sesiones rápidamente. Si la caché local (`localStorage`) solo asocia los colores y la URL del logo de forma global, al cambiar de usuario/organización se mostrará brevemente la marca visual de la sesión anterior durante el bootstrap de `/auth/refresh`.

*   **Mejora propuesta:**
    1.  Estructurar la caché de `localStorage` indexada por ID de organización:
        ```json
        {
          "activeOrgId": "123",
          "branding": {
            "123": { "brandPrimary": "#7f1aa6", "logoUrl": "..." }
          }
        }
        ```
    2.  Durante el bootstrap (antes de montar React), si el token de sesión almacenado (p. ej., en una cookie legible por JS o en storage) expone un `orgId` diferente al `activeOrgId` de la caché, el `ThemeManager` debe invocar `reset()` **inmediatamente** y borrar el tema para mostrar los valores por defecto del producto base. Así se previene por completo el "leak" de marca visual entre tenants en entornos compartidos.

### B. Validación de Seguridad de URLs de Logos Externos (D7)
Dado que el administrador ingresa una URL cruda para el logo (`logo_url`) que se renderiza directamente en el cliente, existe el riesgo de ataques XSS si se ingresa una URL con el protocolo `javascript:` o contenido malicioso.

*   **Mejora propuesta:**
    *   En la validación del backend (DTO de actualización) y en el frontend, forzar que la URL cumpla con una expresión regular estricta que solo permita protocolos seguros (`https://` únicamente, evitando `http://` para prevenir advertencias de contenido mixto en el navegador).
    *   Asegurar que el renderizado en React use únicamente la etiqueta `<img src={branding.logoUrl} />` y que nunca se inyecte de manera peligrosa en el DOM (como en directivas `dangerouslySetInnerHTML`).

---

## 4. Plan de Acción y Tareas Depuradas

Basándonos en este análisis, el archivo [tasks.md](file:///d:/proyectos/drivenSpec/OdontoRisas/Odontorisas/openspec/changes/add-organization-branding/tasks.md) debería modificarse para:
1.  **Eliminar** todas las referencias al favicon (`favicon_url` en DB, validaciones en DTO, inputs en el editor, y métodos en `ThemeManager`).
2.  **Confirmar** el flujo de derivación HSL con reglas de *clamping* y derivación monocromática si no hay color secundario.
3.  **Actualizar** la lógica de caché para incluir la validación de `organizationId` y evitar fugas visuales entre organizaciones.

¿Te gustaría que apliquemos estos ajustes para limpiar las especificaciones, el diseño y las tareas?

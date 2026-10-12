# Design — update-slow-request-notice

## Context

- `add-cold-start-warmup` (D4/D5): `RootLayout` mide la espera del refresh de sesión y muestra `ServerWarmupScreen` mientras el `status` es `idle`/`loading`. Estados y umbrales en `core/utils/serverWarmup.ts` (`WARMING_AFTER_S = 4`, `STUCK_AFTER_S = 90`); hooks genéricos `useElapsedWhile` y `useOnlineStatus`; `SupportRow` para el contacto.
- `core/config/httpClient.ts`: una instancia axios con interceptor de petición (token) y de respuesta (refresh ante 401, single-flight). Todas las peticiones de la app pasan por ahí (el cliente de orval usa `customInstance`).
- Caso real (prueba en producción, 2026-10-11): con la app abierta en Pacientes y el backend dormido, la lista tardó ~1 min sin ningún aviso visible.

## Goals / Non-Goals

**Goals:** que cualquier espera larga durante el uso se explique igual que al abrir la app, sin tocar cada pantalla.

**Non-Goals:** los del proposal.

## Decisions

### D1. Detectar la lentitud en el cliente HTTP, una vez

- Interceptor de petición: a cada petición le asigna un id y arranca un `setTimeout` de 4 s que, si vence, la marca **lenta** en un store. Interceptor de respuesta (éxito **y** error): cancela el timer y quita el id del store.
- Store de Zustand `useSlowRequestStore` (`store/`): conjunto de ids lentos y el instante en que empezó la espera lenta más antigua (`slowSince`). `waiting = ids.size > 0`. Al vaciarse, `slowSince` vuelve a `null`.
- Se cuenta **por petición**, no un booleano: con varias en vuelo, el aviso sigue hasta que responde la última lenta (scenario "Varias peticiones lentas a la vez").
- El reintento tras un 401 (refresh + repetir la petición) es otra petición: tiene su propio id y su propio timer.
- La petición no se cancela ni se reintenta (non-goal): si responde, la pantalla sigue sola.

*Descartado:* un aviso por pantalla (en cada `useQuery`). Habría que tocar todas, y las mutaciones (guardar, sellar) también pueden esperar.

### D2. El aviso: `SlowRequestNotice`

`core/components/SlowRequestNotice`, genérico: lee `useSlowRequestStore`, `useOnlineStatus` y los segundos desde `slowSince`.

- **Forma**: una píldora fija arriba al centro (`fixed`, sobre el contenido, `z-50`), compacta, con el ícono `Sunrise` en `primary`, "Despertando el servidor…" y "Esto puede tomar un minuto. No cierres esta pestaña.". Es un aviso, no un bloqueo: no tapa la pantalla ni impide usarla.
- **Desde 90 s** (`STUCK_AFTER_S`): el ícono pasa a reloj (`warning`), "Está tardando más de lo normal", botón **Reintentar** (recarga) y `SupportRow` con "¿Sigue sin responder?".
- **Sin red**: "Sin conexión a internet" (ícono `WifiOff`).
- Umbrales y textos reutilizan `core/utils/serverWarmup.ts` (mismos 4 s y 90 s que la pantalla de arranque).
- **Accesibilidad**: región `role="status"` / `aria-live="polite"` que cambia una vez por estado; el foco **no** se mueve (el usuario puede estar escribiendo en un formulario); con "reducir movimiento", sin animación de entrada.
- Superficie nueva y pequeña de UI: pasada visual con capturas (claro/oscuro, 375 y 1280 px).

### D3. Dónde se monta y cómo no choca con la pantalla de arranque

`RootLayout` lo renderiza **solo cuando ya terminó el bootstrap** (`status` distinto de `idle`/`loading`), junto al `Toaster`. Mientras la app se inicia, la petición de refresh también podría marcarse lenta, pero el aviso no se pinta: `ServerWarmupScreen` está a cargo (scenario "Durante el arranque no se duplica el aviso"). Así sirve en las pantallas privadas y en las de acceso (un login que espera también lo muestra).

## Risks / Trade-offs

- **Falsos avisos con red lenta**: una petición legítimamente lenta (> 4 s) por mala conexión también lo dispara. El texto habla del servidor; aceptable, y sin red el texto cambia.
- **Timers por petición**: uno por petición en vuelo, siempre cancelado al terminar; los tests verifican que no quedan timers ni ids colgados.
- **Descargas o peticiones largas a propósito** (no existen hoy): si aparecen, podrán excluirse con una marca en la config de axios.

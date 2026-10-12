# Proposal — update-slow-request-notice

## Why

Con el despliegue en Render free (`add-cloud-deployment`) y el monitor de lunes a sábado de 9:00 a 21:00 (`docs/deployment.md` §3), el backend duerme de noche y los domingos. Despertarlo tarda ~1 minuto (medido en Render: 64 s).

`add-cold-start-warmup` explica esa espera **solo al abrir o recargar la app** ("Preparando tu consultorio digital"), porque la primera petición de toda carga es la de restaurar la sesión. Dejó fuera, a propósito, el aviso **a mitad de uso**. En la prueba en producción (2026-10-11) apareció justo ese caso: con la app abierta en Pacientes y el servidor dormido, la lista tardó ~1 minuto y el usuario **no vio ningún aviso**, solo las filas grises de carga de la lista. Es el caso común de quien deja la pestaña abierta y vuelve al día siguiente o un domingo.

## What Changes

- **Aviso global de petición lenta**: si **cualquier** petición a la API sigue sin responder a los **4 s**, aparece un aviso discreto arriba de la pantalla: "Despertando el servidor…" y "Esto puede tomar un minuto. No cierres esta pestaña.". Desaparece cuando ya no queda ninguna petición lenta en curso.
- **Espera muy larga**: pasados **90 s**, el aviso ofrece **Reintentar** (recarga la app) y la fila de soporte (`SupportRow`), igual que la pantalla de arranque.
- **Sin red**: si el navegador está sin conexión, el aviso lo dice ("Sin conexión a internet") en vez de culpar al servidor.
- **Un solo lugar**: lo detecta el cliente HTTP (`httpClient.ts`) para todas las peticiones; ninguna pantalla cambia. Mientras la app se está iniciando, la pantalla de arranque en frío sigue a cargo y este aviso no aparece encima.
- Accesible (región `status` que se anuncia una vez por cambio de estado), solo tokens, modo oscuro, "reducir movimiento".

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `project-foundation`: requisito nuevo "Aviso de espera durante el uso" (complementa "Pantalla de arranque en frío al cargar la app").

## Non-goals

- Cancelar o reintentar automáticamente la petición lenta: la petición sigue viva y, si responde, la pantalla continúa sola.
- Cambiar la carga propia de cada pantalla (skeletons).
- Timeouts del cliente HTTP.
- Mantener el servidor despierto fuera del horario del monitor.

## Impact

- Frontend: `core/config/httpClient.ts` (interceptores), un store de peticiones lentas (`store/`), `core/components/SlowRequestNotice` y `RootLayout`. Sin dependencias nuevas.
- Sin cambios de backend, contrato ni dominio.
- Docs: `docs/frontend.md` (dónde vive el aviso) y `docs/deployment.md` §1.
